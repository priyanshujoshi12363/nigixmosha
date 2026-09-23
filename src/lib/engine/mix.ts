import { pickVariant, soundTag } from "@/lib/sounds";
import type { TimelineEntry } from "@/lib/types";
import { SAMPLE_RATE, decodeToMono } from "./audio";
import type { Soundscape } from "./soundscape";

export type SoundLevel = "off" | "subtle" | "cinematic";

const LEVEL = {
  subtle: { bed: -25, shot: -12, duck: 0.45 },
  cinematic: { bed: -19, shot: -7, duck: 0.38 },
} as const;

const BED_RMS = 0.1;
const SHOT_PEAK = 0.8;

const SCENE_LEAD = 0.6;
const SCENE_TAIL = 1.2;
const FADE = 1.1;
const LOOP_FADE = 0.9;

const db = (value: number) => Math.pow(10, value / 20);
const seconds = (n: number) => Math.round(n * SAMPLE_RATE);

const cache = new Map<string, Promise<Float32Array>>();

export function levelClip(samples: Float32Array, kind: "bed" | "shot"): Float32Array {
  let sum = 0;
  let peak = 0;
  for (let i = 0; i < samples.length; i++) {
    const v = samples[i];
    sum += v * v;
    const a = Math.abs(v);
    if (a > peak) peak = a;
  }
  if (!samples.length || !peak) return samples;
  const rms = Math.sqrt(sum / samples.length);
  const target = kind === "bed" ? (rms > 0 ? BED_RMS / rms : 1) : SHOT_PEAK / peak;
  const gain = Math.max(0.05, Math.min(24, target));
  const out = new Float32Array(samples.length);
  for (let i = 0; i < samples.length; i++) out[i] = samples[i] * gain;
  return out;
}

async function loadSound(url: string, kind: "bed" | "shot", signal?: AbortSignal): Promise<Float32Array> {
  const hit = cache.get(url);
  if (hit) return hit;
  const task = (async () => {
    const res = await fetch(url, { signal, mode: "cors" });
    if (!res.ok) throw new Error(`Sound download failed (${res.status})`);
    return levelClip(await decodeToMono(await res.arrayBuffer()), kind);
  })();
  cache.set(url, task);
  try {
    return await task;
  } catch (err) {
    cache.delete(url);
    throw err;
  }
}

function loopSample(clip: Float32Array, local: number, fade: number) {
  const step = clip.length - fade;
  if (step <= 0) return 0;
  const iteration = Math.floor(local / step);
  const pos = local - iteration * step;
  let value = clip[pos] * (iteration > 0 && pos < fade ? pos / fade : 1);
  const tail = pos + step;
  if (pos < fade && tail < clip.length) value += clip[tail] * ((fade - pos) / fade);
  return value;
}

function addLoop(track: Float32Array, clip: Float32Array, start: number, end: number, gain: number) {
  if (clip.length < SAMPLE_RATE || start >= end) return;
  const fade = Math.min(seconds(LOOP_FADE), Math.floor(clip.length / 3));
  const span = end - start;
  const fadeEdge = Math.min(seconds(FADE), Math.floor(span / 2));
  for (let at = start; at < end; at++) {
    const local = at - start;
    let level = gain;
    if (fadeEdge > 0) {
      const left = end - at;
      if (local < fadeEdge) level *= local / fadeEdge;
      if (left < fadeEdge) level *= left / fadeEdge;
    }
    track[at] += loopSample(clip, local, fade) * level;
  }
}

function addShot(track: Float32Array, clip: Float32Array, at: number, gain: number) {
  const start = Math.max(0, at);
  const tail = Math.min(clip.length, track.length - start);
  for (let i = 0; i < tail; i++) track[start + i] += clip[i] * gain;
}

function duckEnvelope(voice: Float32Array, depth: number): Float32Array {
  const window = seconds(0.03);
  const bins = Math.ceil(voice.length / window);
  const level = new Float32Array(bins);
  for (let b = 0; b < bins; b++) {
    let peak = 0;
    const from = b * window;
    const to = Math.min(voice.length, from + window);
    for (let i = from; i < to; i += 3) {
      const a = Math.abs(voice[i]);
      if (a > peak) peak = a;
    }
    level[b] = Math.min(1, peak / 0.12);
  }
  const attack = 0.55;
  const release = 0.12;
  let smooth = 0;
  const envelope = new Float32Array(bins);
  for (let b = 0; b < bins; b++) {
    smooth += (level[b] - smooth) * (level[b] > smooth ? attack : release);
    envelope[b] = 1 - depth * Math.min(1, smooth);
  }
  const out = new Float32Array(voice.length);
  for (let i = 0; i < voice.length; i++) out[i] = envelope[Math.min(bins - 1, Math.floor(i / window))];
  return out;
}

export interface MixOptions {
  voice: Float32Array;
  timeline: TimelineEntry[];
  plan: Soundscape;
  level: SoundLevel;
  signal?: AbortSignal;
  onProgress?: (done: number, total: number) => void;
}

export interface MixResult {
  samples: Float32Array;
  used: string[];
  skipped: string[];
}

export async function mixSoundscape(opts: MixOptions): Promise<MixResult> {
  const { voice, timeline, plan, level } = opts;
  if (level === "off" || !timeline.length || (!plan.scenes.length && !plan.cues.length)) {
    return { samples: voice, used: [], skipped: [] };
  }
  const settings = LEVEL[level];
  const at = (index: number) => timeline[Math.max(0, Math.min(timeline.length - 1, index))];
  const used: string[] = [];
  const skipped: string[] = [];
  const bedTrack = new Float32Array(voice.length);
  const shotTrack = new Float32Array(voice.length);
  const jobs = plan.scenes.length + plan.cues.length;
  let done = 0;
  let variant = 0;

  for (const scene of plan.scenes) {
    const meta = soundTag(scene.tag);
    const clip = pickVariant(scene.tag, variant++);
    if (meta && clip) {
      try {
        const samples = await loadSound(clip.url, "bed", opts.signal);
        const start = Math.max(0, seconds(at(scene.from).start - SCENE_LEAD));
        const end = Math.min(voice.length, seconds(at(scene.to).end + SCENE_TAIL));
        addLoop(bedTrack, samples, start, end, db(settings.bed + meta.gain + 22) * (0.45 + 0.55 * scene.intensity));
        used.push(scene.tag);
      } catch {
        skipped.push(scene.tag);
      }
    } else {
      skipped.push(scene.tag);
    }
    opts.onProgress?.(++done, jobs);
  }

  for (const cue of plan.cues) {
    const meta = soundTag(cue.tag);
    const clip = pickVariant(cue.tag, variant++);
    if (meta && clip) {
      try {
        const samples = await loadSound(clip.url, "shot", opts.signal);
        const line = at(cue.at);
        const where =
          cue.placement === "before"
            ? seconds(line.start - 0.18) - samples.length
            : cue.placement === "after"
              ? seconds(line.end + 0.12)
              : seconds(line.start + Math.min(0.5, (line.end - line.start) * 0.3));
        addShot(shotTrack, samples, where, db(settings.shot + meta.gain + 8) * cue.gain);
        used.push(cue.tag);
      } catch {
        skipped.push(cue.tag);
      }
    } else {
      skipped.push(cue.tag);
    }
    opts.onProgress?.(++done, jobs);
  }

  const duck = duckEnvelope(voice, settings.duck);
  const out = new Float32Array(voice.length);
  for (let i = 0; i < voice.length; i++) {
    const value = voice[i] + bedTrack[i] * duck[i] + shotTrack[i] * (0.5 + 0.5 * duck[i]);
    out[i] = Math.max(-0.97, Math.min(0.97, value));
  }
  return { samples: out, used: [...new Set(used)], skipped: [...new Set(skipped)] };
}
