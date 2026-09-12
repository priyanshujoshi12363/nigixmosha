import { requestBrain } from "@/lib/client/api";
import type { TTSProviderMeta } from "@/lib/providers";
import type { Analysis, CastEntry, VoiceProfile } from "@/lib/types";
import { NARRATOR_ID } from "@/lib/types";
import { clamp, extractJSON, withRetry } from "@/lib/utils";
import { autoCast, candidateVoices, supportsPitch } from "./casting";
import { languageName } from "./lang";

export interface CastOutcome {
  cast: Record<string, CastEntry>;
  reasons: Record<string, string>;
  by: "ai" | "rules";
  note?: string;
}

interface CastOptions {
  analysis: Analysis;
  voices: VoiceProfile[];
  meta: TTSProviderMeta;
  model: string;
  signal?: AbortSignal;
}

interface RawPick {
  voice?: unknown;
  pitch?: unknown;
  rate?: unknown;
  why?: unknown;
}

const MAX_VOICES = 90;
const FALLBACK_NOTE = "The director couldn't finish casting this time, so voices were matched automatically.";

const SYSTEM =
  "You are the voice casting director of a professional audiobook studio. You match every speaker of a story to the single best voice from a voice catalogue, judging gender, age, personality, speaking style and the story's language, and you keep every main character clearly distinguishable. You always answer with a single JSON object and nothing else.";

function voiceLine(v: VoiceProfile) {
  const who = [v.gender, v.age].filter(Boolean).join(", ");
  const langs = v.langs.includes("*") ? "any language" : v.langs.join("/");
  return `${v.id} | ${v.name} | ${who} | ${langs}${v.multilingual ? " (multilingual)" : ""} | ${v.tags.slice(0, 5).join(", ")}`;
}

function buildPrompt(analysis: Analysis, voices: VoiceProfile[], pitchOK: boolean, engine: string) {
  const speakers = [
    `- narrator: ${analysis.narrator.gender} narrator; tone: ${analysis.narrator.tone}; ${analysis.pov}-person story`,
    ...[...analysis.characters]
      .sort((a, b) => b.lineCount - a.lineCount)
      .map(
        (c) =>
          `- ${c.id}: ${c.name} — ${c.gender}, ${c.age}, ${c.role}, ${c.lineCount} lines; personality: ${
            c.personality.join(", ") || "unknown"
          }; speaks: ${c.speakingStyle || "n/a"}; ideal voice: ${c.voiceDescription || "n/a"}`,
      ),
  ].join("\n");

  return `Cast the audiobook "${analysis.title}" (${languageName(analysis.language)}) with the ${engine} voice engine.

Return JSON: {"cast": {"narrator": {"voice": "voice id", "pitch": 0, "rate": 0, "why": "one short sentence"}, "c1": {"voice": "...", "pitch": 0, "rate": 0, "why": "..."}}}
Include every speaker listed below, keyed by its id.

Rules:
1. Use only voice ids from the catalogue, copied exactly.
2. Match gender first, then age: youthful voices for children and teens, mature voices for elders.
3. Give every main character a different voice. Reuse a voice only for minor characters, and then shift pitch or rate so they still sound distinct.
4. The narrator needs a clear, steady storytelling voice that is different from the lead characters.
5. Prefer voices native to the story's language; use multilingual voices when needed.
6. "rate" is a pace offset in percent from -30 to 30 that fits how the speaker talks: slow, wise or menacing below 0; quick, excitable or young above 0.
7. "pitch" is an offset in percent from -20 to 20${pitchOK ? "" : ". This engine cannot shift pitch, so always use 0"}.
8. "why" explains the choice in one short sentence about the voice, without naming any software.

SPEAKERS:
${speakers}

VOICE CATALOGUE (id | name | gender, age | languages | traits):
${voices.map(voiceLine).join("\n")}`;
}

function keepDistinct(cast: Record<string, CastEntry>, analysis: Analysis, pitchOK: boolean) {
  const nudges = [0, 8, -8, 14, -14, 5, -5];
  const seen = new Map<string, number>();
  const order = [NARRATOR_ID, ...[...analysis.characters].sort((a, b) => b.lineCount - a.lineCount).map((c) => c.id)];
  for (const id of order) {
    const entry = cast[id];
    if (!entry) continue;
    const n = seen.get(entry.voiceId) ?? 0;
    seen.set(entry.voiceId, n + 1);
    if (n === 0) continue;
    const k = nudges[n % nudges.length];
    cast[id] = {
      ...entry,
      pitch: pitchOK ? clamp(entry.pitch + k, -30, 30) : entry.pitch,
      rate: clamp(entry.rate + (pitchOK ? k / 2 : k), -35, 35),
    };
  }
}

export async function castVoices(o: CastOptions): Promise<CastOutcome> {
  const rules = autoCast(o.analysis, o.voices, o.meta, o.model);
  const pool = candidateVoices(o.voices, o.meta, o.analysis.language);
  const catalogue = [...pool.filter((p) => p.native), ...pool.filter((p) => !p.native)]
    .slice(0, MAX_VOICES)
    .map((p) => p.voice);
  if (!catalogue.length) return { cast: rules, reasons: {}, by: "rules" };
  const pitchOK = supportsPitch(o.meta.id, o.model);
  const exact = new Set(catalogue.map((v) => v.id));
  const loose = new Map(catalogue.map((v) => [v.id.toLowerCase(), v.id]));

  try {
    const raw = await withRetry(
      () => requestBrain(SYSTEM, buildPrompt(o.analysis, catalogue, pitchOK, o.meta.name), { signal: o.signal }),
      2,
    );
    const parsed = extractJSON<{ cast?: Record<string, RawPick> } & Record<string, RawPick>>(raw);
    const picks: Record<string, RawPick> = parsed.cast ?? parsed;
    const speakerIds = [NARRATOR_ID, ...o.analysis.characters.map((c) => c.id)];
    const cast = { ...rules };
    const reasons: Record<string, string> = {};
    let hits = 0;

    for (const id of speakerIds) {
      const pick = picks[id];
      if (!pick || typeof pick.voice !== "string") continue;
      const voiceId = exact.has(pick.voice) ? pick.voice : loose.get(pick.voice.trim().toLowerCase());
      if (!voiceId) continue;
      hits++;
      cast[id] = {
        voiceId,
        pitch: pitchOK ? clamp(Number(pick.pitch) || 0, -30, 30) : 0,
        rate: clamp(Number(pick.rate) || 0, -35, 35),
      };
      if (typeof pick.why === "string" && pick.why.trim()) reasons[id] = pick.why.trim().slice(0, 220);
    }

    if (hits < Math.ceil(speakerIds.length / 2)) return { cast: rules, reasons: {}, by: "rules", note: FALLBACK_NOTE };
    keepDistinct(cast, o.analysis, pitchOK);
    return { cast, reasons, by: "ai" };
  } catch (err) {
    if (err instanceof DOMException && err.name === "AbortError") throw err;
    if ((err as { status?: number }).status === 401) throw err;
    return { cast: rules, reasons: {}, by: "rules", note: FALLBACK_NOTE };
  }
}
