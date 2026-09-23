"use client";

import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import type { Soundscape } from "@/lib/engine/soundscape";
import type { ProjectRecord } from "@/lib/library-types";
import type { TTSProviderId } from "@/lib/providers";
import type { Analysis, CastEntry, Character, Segment, TimelineEntry } from "@/lib/types";
import { NARRATOR_ID } from "@/lib/types";

export type Step = "manuscript" | "cast" | "script" | "listen";

export interface RemoteAudio {
  url: string;
  streamUrl: string;
  downloadUrl: string;
}

export interface Output {
  url: string;
  duration: number;
  timeline: TimelineEntry[];
  peaks: number[];
  provider: TTSProviderId;
  createdAt: number;
  failed: number;
  backup: number;
  remote?: RemoteAudio;
}

export type SaveState = "idle" | "saving" | "saved" | "error";

export interface AudioSave {
  status: "idle" | "uploading" | "saved" | "error";
  progress: number;
  error?: string;
}

export function hashText(text: string) {
  let h = 2166136261;
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return `${text.length}:${(h >>> 0).toString(36)}`;
}

interface ProjectState {
  title: string;
  text: string;
  languageHint: string;
  step: Step;
  analysis: Analysis | null;
  segments: Segment[];
  warnings: string[];
  cast: Record<string, CastEntry>;
  castFor: { provider: TTSProviderId; model: string } | null;
  castReasons: Record<string, string>;
  castBy: "ai" | "rules" | null;
  shareNarrator: boolean;
  soundscape: Soundscape | null;
  output: Output | null;
  projectId: string | null;
  projectTextHash: string | null;
  saveState: SaveState;
  saveError: string | null;
  audioSave: AudioSave;
  setDraft: (patch: Partial<Pick<ProjectState, "title" | "text" | "languageHint">>) => void;
  setStep: (step: Step) => void;
  setDirected: (analysis: Analysis, segments: Segment[], warnings: string[]) => void;
  updateAnalysis: (patch: Partial<Analysis>) => void;
  updateCharacter: (id: string, patch: Partial<Character>) => void;
  setCast: (
    cast: Record<string, CastEntry>,
    castFor: { provider: TTSProviderId; model: string },
    meta?: { reasons?: Record<string, string>; by?: "ai" | "rules" },
  ) => void;
  updateCast: (id: string, patch: Partial<CastEntry>) => void;
  setShareNarrator: (v: boolean) => void;
  setSoundscape: (plan: Soundscape | null) => void;
  updateSegment: (id: string, patch: Partial<Segment>) => void;
  mergeSegmentUp: (id: string) => void;
  deleteSegment: (id: string) => void;
  setOutput: (output: Output | null) => void;
  setRemote: (remote: RemoteAudio) => void;
  setProjectId: (id: string | null, textHash?: string | null) => void;
  setSaveState: (state: SaveState, error?: string | null) => void;
  setAudioSave: (audioSave: AudioSave) => void;
  loadProject: (record: ProjectRecord) => void;
  reset: () => void;
}

const IDLE_AUDIO: AudioSave = { status: "idle", progress: 0 };

const empty = {
  title: "",
  text: "",
  languageHint: "auto",
  step: "manuscript" as Step,
  analysis: null,
  segments: [],
  warnings: [],
  cast: {},
  castFor: null,
  castReasons: {},
  castBy: null,
  shareNarrator: false,
  soundscape: null,
  output: null,
  projectId: null,
  projectTextHash: null,
  saveState: "idle" as SaveState,
  saveError: null,
  audioSave: IDLE_AUDIO,
};

function recount(analysis: Analysis, segments: Segment[]): Analysis {
  const counts = new Map<string, number>();
  for (const s of segments) counts.set(s.speaker, (counts.get(s.speaker) ?? 0) + 1);
  return {
    ...analysis,
    characters: analysis.characters.map((c) => ({ ...c, lineCount: counts.get(c.id) ?? 0 })),
  };
}

const revoke = (url?: string) => {
  if (url?.startsWith("blob:")) URL.revokeObjectURL(url);
};

export const useProject = create<ProjectState>()(
  persist(
    (set, get) => ({
      ...empty,
      setDraft: (patch) => set(patch),
      setStep: (step) => set({ step }),
      setSoundscape: (soundscape) => set({ soundscape }),
      setDirected: (analysis, segments, warnings) =>
        set({
          analysis,
          segments,
          warnings,
          cast: {},
          castFor: null,
          castReasons: {},
          castBy: null,
          soundscape: null,
          output: null,
          audioSave: IDLE_AUDIO,
          shareNarrator: Boolean(analysis.narrator.characterId),
          step: "cast",
        }),
      updateAnalysis: (patch) => {
        const a = get().analysis;
        if (a) set({ analysis: { ...a, ...patch } });
      },
      updateCharacter: (id, patch) => {
        const a = get().analysis;
        if (!a) return;
        set({ analysis: { ...a, characters: a.characters.map((c) => (c.id === id ? { ...c, ...patch } : c)) } });
      },
      setCast: (cast, castFor, meta) =>
        set({ cast, castFor, castReasons: meta?.reasons ?? {}, castBy: meta?.by ?? "rules" }),
      updateCast: (id, patch) =>
        set((s) => {
          const current = s.cast[id] ?? s.cast[NARRATOR_ID] ?? { voiceId: "", pitch: 0, rate: 0 };
          return { cast: { ...s.cast, [id]: { ...current, ...patch } } };
        }),
      setShareNarrator: (v) => set({ shareNarrator: v }),
      updateSegment: (id, patch) => {
        const segments = get().segments.map((s) => (s.id === id ? { ...s, ...patch } : s));
        const a = get().analysis;
        set({ segments, analysis: a && patch.speaker ? recount(a, segments) : a });
      },
      mergeSegmentUp: (id) => {
        const segs = get().segments;
        const i = segs.findIndex((s) => s.id === id);
        if (i <= 0) return;
        const merged = { ...segs[i - 1], text: `${segs[i - 1].text} ${segs[i].text}` };
        const segments = [...segs.slice(0, i - 1), merged, ...segs.slice(i + 1)];
        const a = get().analysis;
        set({ segments, analysis: a ? recount(a, segments) : a });
      },
      deleteSegment: (id) => {
        const segments = get().segments.filter((s) => s.id !== id);
        const a = get().analysis;
        set({ segments, analysis: a ? recount(a, segments) : a });
      },
      setOutput: (output) => {
        const prev = get().output;
        if (prev && prev.url !== output?.url) revoke(prev.url);
        set({ output, audioSave: output?.remote ? get().audioSave : IDLE_AUDIO });
      },
      setRemote: (remote) => {
        const out = get().output;
        if (out) set({ output: { ...out, remote } });
      },
      setProjectId: (projectId, textHash) =>
        set(textHash === undefined ? { projectId } : { projectId, projectTextHash: textHash }),
      setSaveState: (saveState, error = null) => set({ saveState, saveError: error }),
      setAudioSave: (audioSave) => set({ audioSave }),
      loadProject: (r) => {
        revoke(get().output?.url);
        const output: Output | null = r.audio
          ? {
              url: r.audio.streamUrl,
              duration: r.audio.duration,
              timeline: r.audio.timeline,
              peaks: r.audio.peaks,
              provider: r.audio.provider,
              createdAt: Date.parse(r.audio.createdAt) || Date.now(),
              failed: r.audio.failed,
              backup: 0,
              remote: { url: r.audio.url, streamUrl: r.audio.streamUrl, downloadUrl: r.audio.downloadUrl },
            }
          : null;
        set({
          projectId: r.id,
          projectTextHash: hashText(r.text),
          title: r.title,
          text: r.text,
          languageHint: r.languageHint || "auto",
          analysis: r.analysis,
          segments: r.segments,
          warnings: r.warnings,
          cast: r.cast,
          castFor: r.castFor,
          castReasons: r.castReasons ?? {},
          castBy: r.castBy ?? null,
          shareNarrator: r.shareNarrator,
          output,
          step: output ? "listen" : r.analysis ? "cast" : "manuscript",
          saveState: "saved",
          saveError: null,
          audioSave: output ? { status: "saved", progress: 1 } : IDLE_AUDIO,
        });
      },
      reset: () => {
        revoke(get().output?.url);
        set({ ...empty });
      },
    }),
    {
      name: "nigixmosha.project",
      version: 1,
      storage: createJSONStorage(() => localStorage),
      skipHydration: true,
      partialize: (s) => ({
        title: s.title,
        text: s.text,
        languageHint: s.languageHint,
        step: s.step === "listen" && !s.output?.remote ? ("script" as Step) : s.step,
        analysis: s.analysis,
        segments: s.segments,
        warnings: s.warnings,
        cast: s.cast,
        castFor: s.castFor,
        castReasons: s.castReasons,
        castBy: s.castBy,
        shareNarrator: s.shareNarrator,
        soundscape: s.soundscape,
        projectId: s.projectId,
        projectTextHash: s.projectTextHash,
        output: s.output?.remote ? { ...s.output, url: s.output.remote.streamUrl } : null,
        audioSave: s.audioSave.status === "saved" && s.output?.remote ? s.audioSave : IDLE_AUDIO,
      }),
    },
  ),
);
