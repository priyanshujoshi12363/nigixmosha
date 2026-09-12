import { requestSpeech } from "@/lib/client/api";
import { getTTS, type TTSProviderId } from "@/lib/providers";
import type { AdvancedSettings } from "@/lib/store/settings";
import type { Analysis, CastEntry, ProviderConfig, Segment, TimelineEntry } from "@/lib/types";
import { NARRATOR_ID } from "@/lib/types";
import { runPool, withRetry } from "@/lib/utils";
import { backupVoice } from "@/lib/voices";
import {
  SAMPLE_RATE,
  computePeaks,
  concat,
  decodeToMono,
  encodeWav,
  normalizeLoudness,
  silence,
  trimSilence,
} from "./audio";
import { castFor } from "./casting";
import { styleFor } from "./direction";
import { splitForTTS } from "./text";

export interface RenderContext {
  analysis: Analysis;
  cast: Record<string, CastEntry>;
  shareNarrator: boolean;
  provider: TTSProviderId;
  config: ProviderConfig;
  signal?: AbortSignal;
}

export interface ProduceOptions extends RenderContext {
  segments: Segment[];
  advanced: AdvancedSettings;
  onProgress?: (done: number, total: number, current: Segment) => void;
}

export interface ProducedAudio {
  url: string;
  blob: Blob;
  duration: number;
  timeline: TimelineEntry[];
  peaks: number[];
  failed: string[];
  backup: string[];
}

function backupContext(ctx: RenderContext): RenderContext {
  const lang = ctx.analysis.language;
  const cast: Record<string, CastEntry> = {
    [NARRATOR_ID]: {
      voiceId: backupVoice(ctx.analysis.narrator.gender, lang, NARRATOR_ID),
      pitch: ctx.cast[NARRATOR_ID]?.pitch ?? 0,
      rate: ctx.cast[NARRATOR_ID]?.rate ?? 0,
    },
  };
  for (const c of ctx.analysis.characters) {
    cast[c.id] = {
      voiceId: backupVoice(c.gender, lang, c.id + c.name),
      pitch: ctx.cast[c.id]?.pitch ?? 0,
      rate: ctx.cast[c.id]?.rate ?? 0,
    };
  }
  return { ...ctx, provider: "edge", config: { apiKey: "", baseUrl: "", model: "neural" }, cast };
}

const clipCache = new Map<string, Float32Array>();

function isFatal(err: unknown) {
  const status = (err as { status?: number }).status;
  return (
    (err instanceof DOMException && err.name === "AbortError") ||
    (status !== undefined && [400, 401, 402, 403].includes(status))
  );
}

export async function renderSegment(seg: Segment, ctx: RenderContext): Promise<Float32Array> {
  const meta = getTTS(ctx.provider);
  const entry = castFor(seg.speaker, ctx.cast, ctx.analysis, ctx.shareNarrator);
  if (!entry) throw new Error("No voice cast for this speaker");
  const style = styleFor(seg, ctx.analysis, entry);
  const lang = seg.lang ?? ctx.analysis.language;
  const key = JSON.stringify([
    ctx.provider,
    ctx.config.model,
    ctx.config.baseUrl,
    entry.voiceId,
    Math.round(style.pitch),
    Math.round(style.rate),
    Math.round(style.volume),
    style.emotion,
    meta.instructable ? style.instructions : "",
    lang,
    seg.text,
  ]);
  const hit = clipCache.get(key);
  if (hit) return hit;
  const parts: Float32Array[] = [];
  for (const piece of splitForTTS(seg.text, meta.maxChars)) {
    const buf = await withRetry(
      () => requestSpeech(ctx.provider, ctx.config, entry.voiceId, piece, lang, style, ctx.signal),
      3,
    );
    parts.push(await decodeToMono(buf));
  }
  const out = concat(parts);
  clipCache.set(key, out);
  return out;
}

export async function renderPreview(seg: Segment, ctx: RenderContext): Promise<string> {
  const samples = await renderSegment(seg, ctx);
  return URL.createObjectURL(encodeWav(normalizeLoudness(trimSilence(samples))));
}

export async function produceAudiobook(opts: ProduceOptions): Promise<ProducedAudio> {
  const meta = getTTS(opts.provider);
  const controller = new AbortController();
  const onAbort = () => controller.abort();
  opts.signal?.addEventListener("abort", onAbort);
  const ctx: RenderContext = { ...opts, signal: controller.signal };
  const limit = Math.max(1, Math.min(meta.concurrency, opts.advanced.ttsConcurrency));
  const failed: string[] = [];
  const backup: string[] = [];
  let backupCtx: RenderContext | null = null;
  let firstError: unknown = null;
  let done = 0;

  try {
    const clips = await runPool(
      opts.segments,
      limit,
      async (seg) => {
        let samples: Float32Array | null = null;
        try {
          samples = await renderSegment(seg, ctx);
        } catch (err) {
          if (isFatal(err)) {
            controller.abort();
            throw err;
          }
          if (ctx.provider === "edge") {
            firstError ??= err;
            failed.push(seg.id);
          } else {
            backupCtx ??= backupContext(ctx);
            try {
              samples = await renderSegment(seg, backupCtx);
              backup.push(seg.id);
            } catch (backupErr) {
              if (isFatal(backupErr)) {
                controller.abort();
                throw backupErr;
              }
              firstError ??= err;
              failed.push(seg.id);
            }
          }
        }
        done++;
        opts.onProgress?.(done, opts.segments.length, seg);
        return samples;
      },
      controller.signal,
    );

    if (clips.every((c) => !c)) throw firstError ?? new Error("No audio was produced");

    const { lineGapMs, speakerGapMs, paragraphGapMs, normalize } = opts.advanced;
    const parts: Float32Array[] = [silence(300)];
    let cursor = parts[0].length;
    const timeline: TimelineEntry[] = [];
    let lastSpeaker: string | null = null;

    clips.forEach((clip, i) => {
      if (!clip) return;
      const seg = opts.segments[i];
      if (lastSpeaker !== null) {
        const gap = silence(seg.para ? paragraphGapMs : seg.speaker !== lastSpeaker ? speakerGapMs : lineGapMs);
        parts.push(gap);
        cursor += gap.length;
      }
      let s = trimSilence(clip);
      if (normalize) s = normalizeLoudness(s);
      timeline.push({
        segmentId: seg.id,
        speaker: seg.speaker,
        start: cursor / SAMPLE_RATE,
        end: (cursor + s.length) / SAMPLE_RATE,
      });
      parts.push(s);
      cursor += s.length;
      lastSpeaker = seg.speaker;
    });
    parts.push(silence(900));

    const all = concat(parts);
    const blob = encodeWav(all);
    return {
      blob,
      url: URL.createObjectURL(blob),
      duration: all.length / SAMPLE_RATE,
      timeline,
      peaks: computePeaks(all, 900),
      failed,
      backup,
    };
  } finally {
    opts.signal?.removeEventListener("abort", onAbort);
  }
}
