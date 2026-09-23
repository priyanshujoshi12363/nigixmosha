import { requestBrain } from "@/lib/client/api";
import { resolveTag, soundMenu } from "@/lib/sounds";
import type { Analysis, Segment } from "@/lib/types";
import { extractJSON, withRetry } from "@/lib/utils";

const LINES_PER_PASS = 150;
const TEXT_PREVIEW = 110;

export type CuePlacement = "before" | "under" | "after";

export interface SoundScene {
  from: number;
  to: number;
  tag: string;
  intensity: number;
  wanted: string;
}

export interface SoundCue {
  at: number;
  tag: string;
  placement: CuePlacement;
  gain: number;
  wanted: string;
}

export interface Soundscape {
  scenes: SoundScene[];
  cues: SoundCue[];
  missing: string[];
  engine: string;
}

interface RawScene {
  from?: unknown;
  to?: unknown;
  tag?: unknown;
  fallback?: unknown;
  prompt?: unknown;
  intensity?: unknown;
}

interface RawCue {
  at?: unknown;
  tag?: unknown;
  fallback?: unknown;
  prompt?: unknown;
  place?: unknown;
  gain?: unknown;
}

const str = (v: unknown) => (typeof v === "string" ? v.trim() : "");
const num = (v: unknown, fallback: number) => {
  const n = typeof v === "number" ? v : Number(str(v));
  return Number.isFinite(n) ? n : fallback;
};

const SOUND_SYSTEM =
  "You are the sound designer of an audiobook studio. You read a scripted chapter and decide what the listener should hear behind the voices: the ambience of each scene and the handful of sound effects the story actually calls for. You are restrained — silence is better than a sound that does not belong. You always answer with a single JSON object and nothing else.";

function soundPrompt(analysis: Analysis, lines: string, offset: number, count: number) {
  return `Chapter: "${analysis.title}"${analysis.summary ? `\n${analysis.summary}` : ""}

Ambience tags (continuous backgrounds):
${soundMenu("bed")}

Effect tags (single sounds):
${soundMenu("shot")}

Return JSON with exactly this shape:
{
  "scenes": [{ "from": ${offset}, "to": ${offset + count - 1}, "tag": "ambience tag", "fallback": "second choice tag", "prompt": "what you would record if no tag fits", "intensity": 0.6 }],
  "cues": [{ "at": ${offset}, "tag": "effect tag", "fallback": "second choice tag", "prompt": "what you would record if no tag fits", "place": "before", "gain": 0.8 }]
}

Rules:
1. A scene is a run of consecutive lines that share one place and mood. Cover the lines from ${offset} to ${offset + count - 1} in order, without gaps or overlaps. Most chapters have one to four scenes.
2. Use a tag from the lists above whenever one is close enough. Only when nothing fits, still give your nearest "tag" and describe the real sound in "prompt".
3. "intensity" is 0.2 for a barely-there room tone, 0.6 for ordinary weather or a street, 1.0 for a storm or a crowd at its loudest.
4. Cues are rare: at most one per ten lines, and only for a sound the text actually states, such as a knock, a gunshot, a phone, a door, thunder. Never add a cue for something merely imagined or remembered.
5. "place": "before" for a sound that happens just before the line is spoken, "under" for one during it, "after" for one just after.
6. "gain" is 0.3 for something distant, 1.0 for something in the room.
7. If a passage should be silent underneath, leave it out of "scenes" rather than inventing ambience.

LINES:
${lines}`;
}

function lineBlock(segments: Segment[], analysis: Analysis, from: number, to: number) {
  const name = new Map(analysis.characters.map((c) => [c.id, c.name]));
  const rows: string[] = [];
  for (let i = from; i <= to; i++) {
    const seg = segments[i];
    const who = seg.speaker === "narrator" ? "narrator" : (name.get(seg.speaker) ?? seg.speaker);
    const text = seg.text.length > TEXT_PREVIEW ? `${seg.text.slice(0, TEXT_PREVIEW)}…` : seg.text;
    rows.push(`${i}. [${who}] ${text.replace(/\s+/g, " ")}`);
  }
  return rows.join("\n");
}

export interface SoundscapeOptions {
  analysis: Analysis;
  segments: Segment[];
  signal?: AbortSignal;
  onProgress?: (done: number, total: number) => void;
}

export async function planSoundscape(opts: SoundscapeOptions): Promise<Soundscape> {
  const { analysis, segments, signal } = opts;
  const scenes: SoundScene[] = [];
  const cues: SoundCue[] = [];
  const missing: string[] = [];
  const passes = Math.max(1, Math.ceil(segments.length / LINES_PER_PASS));

  for (let pass = 0; pass < passes; pass++) {
    const from = pass * LINES_PER_PASS;
    const to = Math.min(segments.length - 1, from + LINES_PER_PASS - 1);
    const raw = await withRetry(
      () =>
        requestBrain(SOUND_SYSTEM, soundPrompt(analysis, lineBlock(segments, analysis, from, to), from, to - from + 1), {
          signal,
        }),
      2,
    );
    const parsed = extractJSON<{ scenes?: RawScene[]; cues?: RawCue[] }>(raw);

    for (const s of parsed.scenes ?? []) {
      const wanted = str(s.prompt) || str(s.tag);
      const tag = resolveTag(str(s.tag), str(s.fallback));
      const start = Math.max(from, Math.min(to, Math.round(num(s.from, from))));
      const end = Math.max(start, Math.min(to, Math.round(num(s.to, to))));
      if (!tag) {
        if (wanted && !missing.includes(wanted)) missing.push(wanted);
        continue;
      }
      scenes.push({ from: start, to: end, tag, intensity: Math.max(0.1, Math.min(1, num(s.intensity, 0.6))), wanted });
    }

    for (const c of parsed.cues ?? []) {
      const wanted = str(c.prompt) || str(c.tag);
      const tag = resolveTag(str(c.tag), str(c.fallback));
      const at = Math.round(num(c.at, -1));
      if (at < from || at > to) continue;
      if (!tag) {
        if (wanted && !missing.includes(wanted)) missing.push(wanted);
        continue;
      }
      const place = str(c.place).toLowerCase();
      cues.push({
        at,
        tag,
        placement: place === "under" ? "under" : place === "after" ? "after" : "before",
        gain: Math.max(0.15, Math.min(1, num(c.gain, 0.8))),
        wanted,
      });
    }
    opts.onProgress?.(pass + 1, passes);
  }

  scenes.sort((a, b) => a.from - b.from);
  const merged: SoundScene[] = [];
  for (const scene of scenes) {
    const prev = merged[merged.length - 1];
    if (prev && prev.tag === scene.tag && scene.from <= prev.to + 1) {
      prev.to = Math.max(prev.to, scene.to);
      prev.intensity = Math.max(prev.intensity, scene.intensity);
      continue;
    }
    if (prev && scene.from <= prev.to) scene.from = prev.to + 1;
    if (scene.from <= scene.to) merged.push(scene);
  }

  cues.sort((a, b) => a.at - b.at);
  const trimmed = cues.filter((cue, i) => i === 0 || cue.at !== cues[i - 1].at || cue.tag !== cues[i - 1].tag);

  return { scenes: merged, cues: trimmed, missing, engine: "nigixmosha sound designer" };
}

export function soundscapeSummary(plan: Soundscape) {
  const beds = plan.scenes.length;
  const cues = plan.cues.length;
  if (!beds && !cues) return "No background sound for this chapter";
  const parts: string[] = [];
  if (beds) parts.push(`${beds} ambience scene${beds > 1 ? "s" : ""}`);
  if (cues) parts.push(`${cues} effect${cues > 1 ? "s" : ""}`);
  return parts.join(" · ");
}
