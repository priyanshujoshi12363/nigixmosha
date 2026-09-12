import { requestBrain } from "@/lib/client/api";
import type { AgeGroup, Analysis, Character, Emotion, Gender, NarratorProfile, Segment } from "@/lib/types";
import { EMOTIONS, NARRATOR_ID } from "@/lib/types";
import { CHARACTER_COLORS, extractJSON, runPool, uid, withRetry } from "@/lib/utils";
import { analyzeHeuristic, heuristicLines } from "./heuristic";
import { detectLanguage, languageName, normalizeLocale } from "./lang";
import { chunkParagraphs, letterCount, splitParagraphs, stripOuterQuotes } from "./text";

export const DIRECTOR_NAME = "nigixmosha director";
const CHUNK_CHARS = 4000;
const EXCERPT_LIMIT = 90000;

export interface DirectorProgress {
  phase: "reading" | "scripting" | "casting" | "finishing";
  label: string;
  value: number;
}

export interface DirectorOptions {
  text: string;
  title: string;
  languageHint: string;
  concurrency?: number;
  signal?: AbortSignal;
  onProgress?: (p: DirectorProgress) => void;
  onCharacters?: (characters: Character[]) => void;
}

export interface DirectorResult {
  analysis: Analysis;
  segments: Segment[];
  warnings: string[];
}

interface RawCharacter {
  name?: unknown;
  aliases?: unknown;
  gender?: unknown;
  age?: unknown;
  role?: unknown;
  personality?: unknown;
  speakingStyle?: unknown;
  voiceDescription?: unknown;
}

interface RawCast {
  title?: unknown;
  language?: unknown;
  mixedLanguages?: unknown;
  pov?: unknown;
  summary?: unknown;
  narrator?: { gender?: unknown; tone?: unknown; characterName?: unknown };
  characters?: RawCharacter[];
}

interface RawSegment {
  s?: unknown;
  e?: unknown;
  t?: unknown;
  p?: unknown;
  speaker?: unknown;
  emotion?: unknown;
  text?: unknown;
}

const str = (v: unknown) => (typeof v === "string" ? v.trim() : "");
const strList = (v: unknown) => (Array.isArray(v) ? v.map(str).filter(Boolean) : []);
const cleanCharName = (n: string) =>
  n
    .replace(/\s*[(（\[][^)）\]]*[)）\]]\s*/g, " ")
    .replace(/\s+/g, " ")
    .trim();
const norm = (s: string) => (s.toLowerCase().match(/[\p{L}\p{N}]/gu) ?? []).join("");

export function asGender(v: unknown): Gender {
  const s = str(v).toLowerCase();
  if (/^(f|woman|girl|lady)/.test(s)) return "female";
  if (/^(m|man|boy)/.test(s)) return "male";
  return "neutral";
}

export function asAge(v: unknown): AgeGroup {
  const s = str(v).toLowerCase();
  if (/child|kid|little/.test(s)) return "child";
  if (/teen|adolesc/.test(s)) return "teen";
  if (/young/.test(s)) return "young";
  if (/middle/.test(s)) return "middle";
  if (/old|elder|senior|aged/.test(s)) return "elderly";
  return "adult";
}

const EMOTION_ALIASES: Record<string, Emotion> = {
  joyful: "happy",
  cheerful: "happy",
  scared: "fearful",
  afraid: "fearful",
  nervous: "fearful",
  anxious: "fearful",
  shout: "shouting",
  yelling: "shouting",
  whispering: "whisper",
  soft: "tender",
  gentle: "tender",
  loving: "tender",
  furious: "angry",
  annoyed: "angry",
  sorrowful: "sad",
  shocked: "surprised",
  amazed: "surprised",
  thrilled: "excited",
  grave: "serious",
  stern: "serious",
  dry: "sarcastic",
  mocking: "sarcastic",
};

export function asEmotion(v: unknown): Emotion {
  const s = str(v).toLowerCase();
  if ((EMOTIONS as readonly string[]).includes(s)) return s as Emotion;
  return EMOTION_ALIASES[s] ?? "neutral";
}

const keyOf = (name: string) =>
  name
    .toLowerCase()
    .replace(/^(mr|mrs|ms|miss|dr|sir|lady|lord)\.?\s+/, "")
    .replace(/[^\p{L}\p{N}\s]/gu, "")
    .replace(/\s+/g, " ")
    .trim();

class CastRegistry {
  characters: Character[] = [];
  private byKey = new Map<string, string>();

  add(name: string, partial: Partial<Character> = {}): string {
    const id = `c${this.characters.length + 1}`;
    const character: Character = {
      id,
      name,
      aliases: partial.aliases ?? [],
      gender: partial.gender ?? "neutral",
      age: partial.age ?? "adult",
      role: partial.role ?? "minor",
      personality: partial.personality ?? [],
      speakingStyle: partial.speakingStyle ?? "",
      voiceDescription: partial.voiceDescription ?? "",
      color: CHARACTER_COLORS[this.characters.length % CHARACTER_COLORS.length],
      lineCount: 0,
    };
    this.characters.push(character);
    for (const n of [name, ...character.aliases]) {
      const k = keyOf(n);
      if (k && !this.byKey.has(k)) this.byKey.set(k, id);
      const first = k.split(" ")[0];
      if (first && first.length > 2 && !this.byKey.has(first)) this.byKey.set(first, id);
    }
    return id;
  }

  find(name: string): string | undefined {
    const k = keyOf(name);
    return this.byKey.get(k) ?? this.byKey.get(k.split(" ")[0]);
  }

  resolve(raw: string): string {
    const s = cleanCharName(raw);
    const lower = s.toLowerCase();
    if (!s || lower === "narrator" || lower === "n") return NARRATOR_ID;
    if (this.characters.some((c) => c.id === s)) return s;
    if (lower.startsWith("new:")) {
      const [name, g] = s.slice(4).split("|");
      const clean = name?.trim();
      if (!clean) return NARRATOR_ID;
      return this.find(clean) ?? this.add(clean, { gender: asGender(g) });
    }
    if (/^c\d+$/i.test(s)) return NARRATOR_ID;
    return this.find(s) ?? this.add(s);
  }

  roster(): string {
    const lines = [`- narrator: the narrator (all prose that is not spoken dialogue)`];
    for (const c of this.characters) {
      const aka = c.aliases.length ? `; also called ${c.aliases.join(", ")}` : "";
      lines.push(`- ${c.id}: ${c.name} (${c.gender}, ${c.age}${aka})`);
    }
    return lines.join("\n");
  }
}

const CAST_SYSTEM =
  "You are the casting director of a professional audiobook studio. You read a novel chapter in any language or script (including code-mixed text such as Hinglish) and identify every speaking character so each one can be given a distinct, fitting voice. You always answer with a single JSON object and nothing else.";

function castPrompt(excerpt: string, hint: string) {
  return `Analyse this chapter and return JSON with exactly this shape:
{
  "title": "chapter title if present, otherwise a short evocative title in the chapter's language",
  "language": "BCP-47 code of the main narration language, e.g. en-US, en-GB, en-IN, hi-IN, ta-IN, bn-IN, es-ES, ja-JP",
  "mixedLanguages": ["BCP-47 codes of any other languages that appear in dialogue"],
  "pov": "first" or "third",
  "summary": "two sentence summary in English",
  "narrator": { "gender": "male|female|neutral", "tone": "ideal narration voice in a few words", "characterName": "name of the narrating character if first person, else null" },
  "characters": [
    {
      "name": "primary name exactly as written in the text",
      "aliases": ["other names, nicknames or titles used for them"],
      "gender": "male|female|neutral",
      "age": "child|teen|young|adult|middle|elderly",
      "role": "protagonist|antagonist|mentor|love interest|friend|family|minor",
      "personality": ["3 to 5 adjectives grounded in how they act here"],
      "speakingStyle": "how they talk: pace, vocabulary, formality, accent, verbal tics",
      "voiceDescription": "ideal voice timbre for casting, e.g. 'husky low female voice, unhurried'"
    }
  ]
}
Rules:
- Include every character who speaks at least one line, even minor ones (a guard, a shopkeeper). Skip characters who are only mentioned.
- Infer gender and age from pronouns, titles, relationships, verb agreement and context.
- Order characters by how much they speak, most first.${hint ? `\n- The author says the chapter is written in ${languageName(hint)} (${hint}).` : ""}

CHAPTER:
"""
${excerpt}
"""`;
}

const SCRIPT_SYSTEM =
  "You are an audiobook script editor. You convert prose into a speaker-attributed script for a full-cast recording without changing, translating, or omitting a single word. You always answer with a single JSON object and nothing else.";

function scriptPrompt(roster: string, context: string, excerpt: string, strict: boolean) {
  return `Speakers (use these ids):
${roster}

Split the EXCERPT into consecutive segments and return:
{"segments":[{"s":"speaker id","e":"emotion","t":"exact text","p":1}]}

Rules:
1. Copy the text verbatim and in order. Every word of the excerpt must appear exactly once across all "t" values. Never summarise, translate, correct, or skip anything.${strict ? " Your previous answer dropped text — be exhaustive this time." : ""}
2. Spoken dialogue goes to the character who speaks it. Everything else — description, action, and dialogue tags such as "she said" — goes to "narrator". Split whenever the speaker changes. Example: “Run!” Arjun shouted. “Now!” becomes [c1 "Run!"], [narrator "Arjun shouted."], [c1 "Now!"].
3. Leave out the quotation marks around dialogue.
4. If a speaker is not in the list, use "new:Name|male" or "new:Name|female" (give an unnamed speaker a descriptive name like "Guard").
5. "e" is one of: ${EMOTIONS.join(", ")}. Judge it from context. Narration is usually neutral or calm.
6. "p": 1 when the segment begins a new paragraph, otherwise omit it.
7. Split narration longer than about 600 characters at sentence boundaries.
${context ? `\nCONTEXT (the text just before the excerpt; do not output it):\n"""\n${context}\n"""\n` : ""}
EXCERPT:
"""
${excerpt}
"""`;
}

function isFatal(err: unknown) {
  const status = (err as { status?: number }).status;
  return (
    (err instanceof DOMException && err.name === "AbortError") ||
    (status !== undefined && [400, 401, 402, 403, 404].includes(status))
  );
}

function fallbackSegments(chunk: string, reg: CastRegistry): Segment[] {
  const { lines, speakers } = heuristicLines(chunk);
  const genderOf = new Map(speakers.map((s) => [s.key, s]));
  return lines.map((l) => {
    let speaker = NARRATOR_ID;
    if (l.speakerKey) {
      const sp = genderOf.get(l.speakerKey);
      const name = sp?.name ?? l.speakerKey;
      speaker = reg.find(name) ?? (l.speakerKey === "__unknown" ? reg.resolve("Unnamed voice") : reg.add(name, { gender: sp?.gender }));
    }
    return { id: uid("s"), speaker, emotion: l.emotion, text: l.text, para: l.para };
  });
}

function alignWithQuotes(chunk: string, llm: Segment[], reg: CastRegistry, pov: "first" | "third"): Segment[] | null {
  const { lines, speakers } = heuristicLines(chunk);
  if (!lines.some((l) => l.dialogue)) return null;
  const byKey = new Map(speakers.map((s) => [s.key, s]));
  const llmNorm = llm.map((s) => norm(s.text));
  let cursor = 0;

  const find = (key: string, wantNarrator: boolean) => {
    const matches = (i: number) => llmNorm[i].includes(key);
    const typed = (i: number) => (llm[i].speaker === NARRATOR_ID) === wantNarrator;
    for (let i = cursor; i < Math.min(llm.length, cursor + 12); i++) if (matches(i) && typed(i)) return i;
    for (let i = cursor; i < llm.length; i++) if (matches(i)) return i;
    for (let i = 0; i < cursor; i++) if (matches(i)) return i;
    return -1;
  };

  return lines.map((l) => {
    const key = norm(l.text).slice(0, 28);
    const hit = key.length >= 3 ? find(key, !l.dialogue) : -1;
    if (hit >= cursor) cursor = hit;
    const src = hit >= 0 ? llm[hit] : undefined;
    let speaker = NARRATOR_ID;
    let emotion: Emotion = "neutral";
    if (l.dialogue) {
      emotion = src && src.speaker !== NARRATOR_ID ? src.emotion : l.emotion;
      if (src && src.speaker !== NARRATOR_ID) speaker = src.speaker;
      else if (l.speakerKey && l.speakerKey !== "__unknown") {
        const sp = byKey.get(l.speakerKey);
        const name = sp?.name ?? l.speakerKey;
        speaker = reg.find(name) ?? reg.add(name, { gender: sp?.gender });
      } else if (l.speakerKey === null || pov === "first") speaker = NARRATOR_ID;
      else speaker = reg.resolve("Unnamed voice");
    } else if (src && src.speaker === NARRATOR_ID) {
      emotion = src.emotion;
    }
    return { id: uid("s"), speaker, emotion, text: l.text, para: l.para };
  });
}

async function directWithBrain(opts: DirectorOptions): Promise<DirectorResult> {
  const { text, signal, onProgress } = opts;
  const hint = opts.languageHint && opts.languageHint !== "auto" ? opts.languageHint : "";

  const warnings: string[] = [];
  onProgress?.({ phase: "reading", label: "The director is reading the chapter", value: 0.04 });

  const excerptLimit = EXCERPT_LIMIT;
  const excerpt = text.length > excerptLimit ? text.slice(0, excerptLimit) : text;
  if (text.length > excerptLimit) {
    warnings.push("Long chapter: characters were discovered from the opening; later speakers were added while scripting.");
  }

  const castRaw = await withRetry(
    async () => extractJSON<RawCast>(await requestBrain(CAST_SYSTEM, castPrompt(excerpt, hint), { signal })),
    2,
  );

  const reg = new CastRegistry();
  for (const rc of castRaw.characters ?? []) {
    const name = cleanCharName(str(rc.name));
    if (!name || reg.find(name)) continue;
    reg.add(name, {
      aliases: strList(rc.aliases).filter((a) => a.toLowerCase() !== name.toLowerCase()),
      gender: asGender(rc.gender),
      age: asAge(rc.age),
      role: str(rc.role) || "minor",
      personality: strList(rc.personality).slice(0, 5),
      speakingStyle: str(rc.speakingStyle),
      voiceDescription: str(rc.voiceDescription),
    });
  }
  opts.onCharacters?.([...reg.characters]);

  const llmLang = str(castRaw.language);
  const language = hint || (/^[a-z]{2,3}(-[a-z0-9]{2,4})?$/i.test(llmLang) ? normalizeLocale(llmLang) : detectLanguage(text));
  const pov: "first" | "third" = str(castRaw.pov).toLowerCase().startsWith("first") ? "first" : "third";
  const narratorName = str(castRaw.narrator?.characterName);
  const narrator: NarratorProfile = {
    gender: asGender(castRaw.narrator?.gender),
    tone: str(castRaw.narrator?.tone) || "warm, measured storyteller",
    characterId: pov === "first" && narratorName && narratorName.toLowerCase() !== "null" ? (reg.find(narratorName) ?? null) : null,
  };

  const chunks = chunkParagraphs(splitParagraphs(text), CHUNK_CHARS);
  let done = 0;
  onProgress?.({ phase: "scripting", label: `Scripting ${chunks.length} part${chunks.length > 1 ? "s" : ""}`, value: 0.15 });

  const parts = await runPool(
    chunks,
    Math.max(1, opts.concurrency ?? 2),
    async (chunk, i) => {
      const context = i > 0 ? chunks[i - 1].slice(-500) : "";
      let segs: Segment[] | null = null;
      for (let attempt = 0; attempt < 2 && !segs; attempt++) {
        try {
          const raw = await withRetry(
            () => requestBrain(SCRIPT_SYSTEM, scriptPrompt(reg.roster(), context, chunk, attempt > 0), { signal }),
            2,
          );
          const parsed = extractJSON<{ segments?: RawSegment[] } | RawSegment[]>(raw);
          const list = Array.isArray(parsed) ? parsed : (parsed.segments ?? []);
          const candidate: Segment[] = [];
          for (const r of list) {
            const speaker = reg.resolve(str(r.s ?? r.speaker) || NARRATOR_ID);
            let t = typeof (r.t ?? r.text) === "string" ? String(r.t ?? r.text).trim() : "";
            if (speaker !== NARRATOR_ID) t = stripOuterQuotes(t);
            if (!letterCount(t)) continue;
            candidate.push({ id: uid("s"), speaker, emotion: asEmotion(r.e ?? r.emotion), text: t, para: Boolean(r.p) });
          }
          if (candidate.length) {
            const aligned = alignWithQuotes(chunk, candidate, reg, pov);
            const ratio = letterCount(candidate.map((c) => c.text).join(" ")) / Math.max(1, letterCount(chunk));
            if (aligned) segs = aligned;
            else if (ratio > 0.82 && ratio < 1.3) segs = candidate;
          }
        } catch (err) {
          if (isFatal(err)) throw err;
        }
      }
      if (!segs) {
        warnings.push(`Part ${i + 1} got a quick read.`);
        segs = fallbackSegments(chunk, reg);
      }
      if (segs.length) segs[0].para = true;
      done++;
      onProgress?.({
        phase: "scripting",
        label: `Scripted ${done} of ${chunks.length} part${chunks.length > 1 ? "s" : ""}`,
        value: 0.15 + 0.8 * (done / chunks.length),
      });
      return segs;
    },
    signal,
  );

  onProgress?.({ phase: "finishing", label: "Assembling the cast", value: 0.98 });
  const segments = parts.flat();
  const counts = new Map<string, number>();
  for (const s of segments) counts.set(s.speaker, (counts.get(s.speaker) ?? 0) + 1);
  const characters = reg.characters
    .map((c) => ({ ...c, lineCount: counts.get(c.id) ?? 0 }))
    .filter((c) => c.lineCount > 0 || c.id === narrator.characterId)
    .sort((a, b) => b.lineCount - a.lineCount)
    .map((c, i) => ({ ...c, color: CHARACTER_COLORS[i % CHARACTER_COLORS.length] }));

  return {
    analysis: {
      title: str(castRaw.title) || opts.title || "Untitled chapter",
      language,
      mixedLanguages: strList(castRaw.mixedLanguages).map(normalizeLocale).filter((l) => l !== language),
      pov,
      summary: str(castRaw.summary),
      narrator,
      characters,
      engine: DIRECTOR_NAME,
    },
    segments,
    warnings,
  };
}

export async function directChapter(opts: DirectorOptions): Promise<DirectorResult> {
  const hint = opts.languageHint && opts.languageHint !== "auto" ? opts.languageHint : "";
  try {
    return await directWithBrain(opts);
  } catch (err) {
    const status = (err as { status?: number }).status;
    if ((err instanceof DOMException && err.name === "AbortError") || status === 401 || status === 413) throw err;
    opts.onProgress?.({ phase: "finishing", label: "Finishing a quick read", value: 0.95 });
    const quick = analyzeHeuristic(opts.text, opts.title, hint);
    return {
      analysis: { ...quick.analysis, engine: DIRECTOR_NAME },
      segments: quick.segments,
      warnings: ["The director was busy, so this chapter got a quick read. Re-direct it later for richer characters."],
    };
  }
}
