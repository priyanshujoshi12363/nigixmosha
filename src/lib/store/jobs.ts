"use client";

import { create } from "zustand";
import { requestVoices } from "@/lib/client/api";
import { saveAudiobook } from "@/lib/client/library-sync";
import { castVoices } from "@/lib/engine/ai-cast";
import { directChapter, type DirectorProgress } from "@/lib/engine/director";
import { produceAudiobook, renderPreview } from "@/lib/engine/produce";
import { planSoundscape } from "@/lib/engine/soundscape";
import { hasSounds } from "@/lib/sounds";
import { getTTS } from "@/lib/providers";
import type { Analysis, Character, Segment } from "@/lib/types";
import { staticVoices } from "@/lib/voices";
import { hashText, useProject } from "./project";
import { useSettings } from "./settings";

type Status = "idle" | "running" | "error";

interface JobsState {
  directStatus: Status;
  directProgress: DirectorProgress | null;
  directCharacters: Character[];
  directError: string | null;
  castStatus: Status;
  castNote: string | null;
  produceStatus: Status;
  produceDone: number;
  produceTotal: number;
  produceCurrent: Segment | null;
  produceError: string | null;
  produceStage: "voices" | "sound" | null;
  previewKey: string | null;
  previewState: "loading" | "playing" | null;
  previewError: string | null;
  startDirect: () => Promise<void>;
  cancelDirect: () => void;
  startCast: () => Promise<void>;
  startProduce: () => Promise<void>;
  cancelProduce: () => void;
  preview: (key: string, seg: Segment) => Promise<void>;
  stopPreview: () => void;
  clearPreviewError: () => void;
}

let directCtrl: AbortController | null = null;
let castCtrl: AbortController | null = null;
let produceCtrl: AbortController | null = null;
let previewAudio: HTMLAudioElement | null = null;
let previewUrl: string | null = null;
let previewToken = 0;

const message = (err: unknown) => (err instanceof Error ? err.message : String(err));
const isAbort = (err: unknown) => err instanceof DOMException && err.name === "AbortError";

function voiceContext() {
  const p = useProject.getState();
  const s = useSettings.getState();
  if (!p.analysis || !p.castFor) throw new Error("Cast the voices first");
  const provider = p.castFor.provider;
  return {
    analysis: p.analysis,
    cast: p.cast,
    shareNarrator: p.shareNarrator,
    provider,
    config: { ...s.tts[provider], model: p.castFor.model },
  };
}

async function castWithBrain(analysis: Analysis, signal?: AbortSignal) {
  const s = useSettings.getState();
  const tts = s.activeTTS;
  const meta = getTTS(tts);
  const cfg = s.tts[tts];
  const model = cfg.model || meta.defaultModel;
  let voices = staticVoices(tts, model);
  if (meta.liveVoices && (tts === "edge" || cfg.apiKey.trim() || s.serverKeys[tts])) {
    try {
      const live = await requestVoices(tts, cfg.apiKey);
      if (live.length) voices = live;
    } catch {}
  }
  const outcome = await castVoices({
    analysis,
    voices,
    meta,
    model,
    signal,
  });
  return { ...outcome, castFor: { provider: tts, model } };
}

export const useJobs = create<JobsState>((set, get) => ({
  directStatus: "idle",
  directProgress: null,
  directCharacters: [],
  directError: null,
  castStatus: "idle",
  castNote: null,
  produceStatus: "idle",
  produceDone: 0,
  produceTotal: 0,
  produceCurrent: null,
  produceError: null,
  produceStage: null,
  previewKey: null,
  previewState: null,
  previewError: null,

  startDirect: async () => {
    const p = useProject.getState();
    const s = useSettings.getState();
    if (!p.text.trim()) return;
    directCtrl?.abort();
    castCtrl?.abort();
    const ctrl = new AbortController();
    directCtrl = ctrl;
    set({
      directStatus: "running",
      directProgress: { phase: "reading", label: "Opening the manuscript", value: 0.02 },
      directCharacters: [],
      directError: null,
      castNote: null,
    });
    try {
      const result = await directChapter({
        text: p.text,
        title: p.title,
        languageHint: p.languageHint,
        concurrency: 2,
        signal: ctrl.signal,
        onProgress: (directProgress) => set({ directProgress }),
        onCharacters: (directCharacters) => set({ directCharacters }),
      });
      if (ctrl.signal.aborted) return;
      set({
        directCharacters: result.analysis.characters,
        directProgress: {
          phase: "casting",
          label: `Casting ${getTTS(s.activeTTS).name} voices`,
          value: 0.97,
        },
      });
      const casting = await castWithBrain(result.analysis, ctrl.signal);
      if (ctrl.signal.aborted) return;
      const project = useProject.getState();
      const hash = hashText(p.text);
      if (project.projectTextHash !== hash) project.setProjectId(null, hash);
      project.setOutput(null);
      project.setDirected(result.analysis, result.segments, result.warnings);
      project.setCast(casting.cast, casting.castFor, { reasons: casting.reasons, by: casting.by });
      set({ directStatus: "idle", directProgress: null, directCharacters: [], castNote: casting.note ?? null });
    } catch (err) {
      if (isAbort(err) || ctrl.signal.aborted) set({ directStatus: "idle", directProgress: null });
      else set({ directStatus: "error", directError: message(err), directProgress: null });
    } finally {
      if (directCtrl === ctrl) directCtrl = null;
    }
  },

  cancelDirect: () => {
    directCtrl?.abort();
    set({ directStatus: "idle", directProgress: null, directCharacters: [] });
  },

  startCast: async () => {
    const analysis = useProject.getState().analysis;
    if (!analysis) return;
    castCtrl?.abort();
    const ctrl = new AbortController();
    castCtrl = ctrl;
    set({ castStatus: "running", castNote: null });
    try {
      const casting = await castWithBrain(analysis, ctrl.signal);
      if (ctrl.signal.aborted) return;
      useProject.getState().setCast(casting.cast, casting.castFor, { reasons: casting.reasons, by: casting.by });
      set({ castStatus: "idle", castNote: casting.note ?? null });
    } catch (err) {
      if (isAbort(err) || ctrl.signal.aborted) set({ castStatus: "idle" });
      else set({ castStatus: "error", castNote: message(err) });
    } finally {
      if (castCtrl === ctrl) castCtrl = null;
    }
  },

  startProduce: async () => {
    const p = useProject.getState();
    if (!p.analysis || !p.castFor || !p.segments.length) return;
    get().stopPreview();
    produceCtrl?.abort();
    const ctrl = new AbortController();
    produceCtrl = ctrl;
    p.setStep("listen");
    set({
      produceStatus: "running",
      produceDone: 0,
      produceTotal: p.segments.length,
      produceCurrent: null,
      produceError: null,
      produceStage: "voices",
    });
    try {
      const ctx = voiceContext();
      const s = useSettings.getState();
      const level = s.advanced.soundscape;
      let plan = p.soundscape;
      if (level !== "off" && hasSounds && !plan && p.analysis) {
        set({ produceStage: "sound" });
        try {
          plan = await planSoundscape({
            analysis: p.analysis,
            segments: p.segments,
            signal: ctrl.signal,
          });
          useProject.getState().setSoundscape(plan);
        } catch (err) {
          if (isAbort(err) || ctrl.signal.aborted) throw err;
          plan = null;
        }
        set({ produceStage: "voices" });
      }
      const out = await produceAudiobook({
        ...ctx,
        segments: p.segments,
        advanced: s.advanced,
        soundscape: plan,
        soundLevel: level,
        signal: ctrl.signal,
        onProgress: (produceDone, produceTotal, produceCurrent) => set({ produceDone, produceTotal, produceCurrent }),
        onMixProgress: () => set({ produceStage: "sound" }),
      });
      if (ctrl.signal.aborted) {
        URL.revokeObjectURL(out.url);
        return;
      }
      useProject.getState().setOutput({
        url: out.url,
        duration: out.duration,
        timeline: out.timeline,
        peaks: out.peaks,
        provider: ctx.provider,
        createdAt: Date.now(),
        failed: out.failed.length,
        backup: out.backup.length,
      });
      set({ produceStatus: "idle", produceCurrent: null, produceStage: null });
      void saveAudiobook(out.blob);
    } catch (err) {
      if (produceCtrl !== ctrl) return;
      if (isAbort(err) && ctrl.signal.aborted && get().produceStatus !== "running") return;
      if (isAbort(err)) set({ produceStatus: "idle", produceCurrent: null, produceStage: null });
      else set({ produceStatus: "error", produceError: message(err), produceCurrent: null, produceStage: null });
    } finally {
      if (produceCtrl === ctrl) produceCtrl = null;
    }
  },

  cancelProduce: () => {
    set({ produceStatus: "idle", produceCurrent: null, produceStage: null });
    produceCtrl?.abort();
  },

  preview: async (key, seg) => {
    get().stopPreview();
    const token = ++previewToken;
    set({ previewKey: key, previewState: "loading", previewError: null });
    try {
      const url = await renderPreview(seg, voiceContext());
      if (token !== previewToken) {
        URL.revokeObjectURL(url);
        return;
      }
      previewUrl = url;
      previewAudio = new Audio(url);
      previewAudio.onended = () => {
        if (token === previewToken) get().stopPreview();
      };
      set({ previewState: "playing" });
      await previewAudio.play();
    } catch (err) {
      if (token === previewToken) set({ previewKey: null, previewState: null, previewError: message(err) });
    }
  },

  stopPreview: () => {
    previewToken++;
    previewAudio?.pause();
    previewAudio = null;
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = null;
    set({ previewKey: null, previewState: null });
  },

  clearPreviewError: () => set({ previewError: null }),
}));
