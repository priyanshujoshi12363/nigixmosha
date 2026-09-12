import type { AgeGroup, Analysis, Character, Emotion, Gender, Segment } from "@/lib/types";
import { NARRATOR_ID } from "@/lib/types";
import { CHARACTER_COLORS, uid } from "@/lib/utils";
import { detectLanguage } from "./lang";
import { letterCount, splitParagraphs, stripOuterQuotes } from "./text";

const QUOTE_RE = /“[^”]{1,2000}”|"[^"\n]{1,2000}"|«[^»]{1,2000}»|„[^“”]{1,2000}[“”]|「[^」]{1,2000}」|『[^』]{1,2000}』/g;

const VERBS = [
  "said", "says", "asked", "asks", "replied", "answered", "shouted", "yelled", "screamed", "cried",
  "whispered", "murmured", "muttered", "called", "exclaimed", "added", "continued", "began", "snapped",
  "growled", "hissed", "sighed", "laughed", "remarked", "told", "insisted", "demanded", "pleaded",
  "gasped", "breathed", "roared", "barked", "admitted", "agreed", "explained", "interrupted", "repeated",
  "responded", "stammered", "stuttered", "warned", "wondered", "mumbled", "snarled", "sneered", "teased",
  "urged", "groaned", "chuckled", "sobbed", "announced", "declared", "protested", "retorted", "joked",
].join("|");

const TITLES =
  "Mr|Mrs|Ms|Miss|Dr|Professor|Prof|Uncle|Aunt|Auntie|Lord|Lady|Sir|Madam|Captain|King|Queen|Prince|Princess|Father|Mother|Brother|Sister|Grandma|Grandpa|Master|Mistress|Inspector|Detective|Officer";
const NAME = `(?:(?:${TITLES})\\.?\\s+)?\\p{Lu}[\\p{L}'’-]+(?:\\s+\\p{Lu}[\\p{L}'’-]+)?`;
const LY = `(?:\\p{L}+ly\\s+)?`;
const LEAD = `^[\\s,;:—–-]*`;

const AFTER_VERB_NAME = new RegExp(`${LEAD}(${VERBS})\\s+(${NAME})`, "u");
const AFTER_NAME_VERB = new RegExp(`${LEAD}(${NAME})\\s+${LY}(${VERBS})\\b`, "u");
const AFTER_PRONOUN = new RegExp(`${LEAD}(he|she|I)\\s+${LY}(${VERBS})\\b`, "iu");
const BEFORE_NAME_VERB = new RegExp(`(${NAME})\\s+${LY}(${VERBS})[^.!?"“”]{0,40}[,:—–-]?\\s*$`, "u");
const BEFORE_VERB_NAME = new RegExp(`(${VERBS})\\s+(${NAME})[^.!?"“”]{0,25}[,:—–-]?\\s*$`, "u");
const BEFORE_PRONOUN = new RegExp(`\\b(he|she|I)\\s+${LY}(${VERBS})[^.!?"“”]{0,40}[,:—–-]?\\s*$`, "iu");

const HI_VERBS = "कहा|बोला|बोली|पूछा|चिल्लाया|चिल्लाई|फुसफुसाया|फुसफुसाई";
const HINDI_AFTER = new RegExp(`^[\\s,।—–-]*(\\p{Script=Devanagari}+)\\s+(?:ने\\s+)?(${HI_VERBS})`, "u");
const HINDI_BEFORE = new RegExp(`(\\p{Script=Devanagari}+)\\s+(?:ने\\s+)?(${HI_VERBS})[^।"“”]{0,30}[,:—–-]?\\s*$`, "u");
const HINDI_FIRST = new Set(["मैंने", "मैं", "हमने"]);
const HINDI_PRONOUNS = new Set(["उसने", "उन्होंने", "वह", "वो", "तुमने", "आपने", "फिर", "और", "तब"]);
const HI_MALE_VERB = /^(बोला|चिल्लाया|फुसफुसाया)$/;
const HI_FEMALE_VERB = /^(बोली|चिल्लाई|फुसफुसाई)$/;
const HI_MALE_KIN = /(दादा|दादाजी|पिता|पिताजी|भैया|भाई|चाचा|मामा|नाना|बाबा|बाबूजी)$/;
const HI_FEMALE_KIN = /(दादी|माँ|माता|दीदी|बहन|चाची|मामी|नानी|अम्मा)$/;

const NOT_NAMES = new Set([
  "The", "He", "She", "It", "They", "We", "I", "You", "But", "And", "Then", "When", "What", "Why", "How",
  "Yes", "No", "Oh", "Well", "This", "That", "There", "Here", "His", "Her", "Their", "Our", "My", "Your",
  "A", "An", "Now", "So", "Just", "If", "As", "At", "In", "On", "For", "With", "After", "Before",
  "Suddenly", "Still", "Someone", "Somebody", "Everyone", "Nobody", "One", "Another", "Finally", "Later",
  "Soon", "Again", "Instead", "Meanwhile", "Quietly", "Softly", "Slowly",
]);

const TITLE_RE = new RegExp(`^(${TITLES})\\.?\\s+`, "u");
const MALE_TITLE = /^(Mr|Uncle|Lord|Sir|Captain|King|Prince|Father|Brother|Grandpa|Master)\b/;
const FEMALE_TITLE = /^(Mrs|Ms|Miss|Aunt|Auntie|Lady|Madam|Queen|Princess|Mother|Sister|Grandma|Mistress)\b/;

const VERB_EMOTION: Record<string, Emotion> = {
  shouted: "shouting",
  yelled: "shouting",
  screamed: "shouting",
  roared: "shouting",
  barked: "angry",
  snapped: "angry",
  growled: "angry",
  snarled: "angry",
  hissed: "angry",
  whispered: "whisper",
  murmured: "whisper",
  breathed: "whisper",
  mumbled: "whisper",
  sneered: "sarcastic",
  teased: "sarcastic",
  retorted: "sarcastic",
  joked: "happy",
  laughed: "happy",
  chuckled: "happy",
  sobbed: "sad",
  sighed: "sad",
  groaned: "sad",
  gasped: "surprised",
  exclaimed: "excited",
  pleaded: "fearful",
  stammered: "fearful",
  stuttered: "fearful",
  demanded: "serious",
  insisted: "serious",
  warned: "serious",
  declared: "serious",
  चिल्लाया: "shouting",
  चिल्लाई: "shouting",
  फुसफुसाया: "whisper",
  फुसफुसाई: "whisper",
};

const EMOTION_TRAITS: Partial<Record<Emotion, string[]>> = {
  shouting: ["fiery", "forceful"],
  angry: ["fiery", "blunt"],
  whisper: ["soft-spoken", "reserved"],
  happy: ["cheerful", "warm"],
  sad: ["melancholic", "gentle"],
  sarcastic: ["witty", "sardonic"],
  fearful: ["anxious", "hesitant"],
  serious: ["earnest", "firm"],
  excited: ["lively", "animated"],
  surprised: ["expressive"],
};

type Attribution = { who: string; verb?: string };

function cleanName(raw: string): string | null {
  const tokens = raw.trim().split(/\s+/);
  while (tokens.length && NOT_NAMES.has(tokens[0])) tokens.shift();
  if (!tokens.length) return null;
  const name = tokens.join(" ").replace(/['’]s$/, "");
  return NOT_NAMES.has(name) ? null : name;
}

function pronoun(p: string, verb: string): Attribution {
  const l = p.toLowerCase();
  return { who: l === "i" ? "__first" : l === "he" ? "__he" : "__she", verb: verb.toLowerCase() };
}

function hindi(m: RegExpMatchArray | null): Attribution | null {
  if (!m) return null;
  if (HINDI_FIRST.has(m[1])) return { who: "__first", verb: m[2] };
  if (HINDI_PRONOUNS.has(m[1])) {
    if (HI_FEMALE_VERB.test(m[2])) return { who: "__she", verb: m[2] };
    if (HI_MALE_VERB.test(m[2])) return { who: "__he", verb: m[2] };
    return null;
  }
  return { who: m[1], verb: m[2] };
}

function attribute(after: string, before: string): Attribution | null {
  let m = after.match(AFTER_VERB_NAME);
  if (m) {
    const n = cleanName(m[2]);
    if (n) return { who: n, verb: m[1].toLowerCase() };
  }
  m = after.match(AFTER_NAME_VERB);
  if (m) {
    const n = cleanName(m[1]);
    if (n) return { who: n, verb: m[2].toLowerCase() };
  }
  m = after.match(AFTER_PRONOUN);
  if (m) return pronoun(m[1], m[2]);
  const ha = hindi(after.match(HINDI_AFTER));
  if (ha) return ha;
  m = before.match(BEFORE_NAME_VERB);
  if (m) {
    const n = cleanName(m[1]);
    if (n) return { who: n, verb: m[2].toLowerCase() };
  }
  m = before.match(BEFORE_VERB_NAME);
  if (m) {
    const n = cleanName(m[2]);
    if (n) return { who: n, verb: m[1].toLowerCase() };
  }
  m = before.match(BEFORE_PRONOUN);
  if (m) return pronoun(m[1], m[2]);
  return hindi(before.match(HINDI_BEFORE));
}

export function nameKey(name: string) {
  const titled = TITLE_RE.test(name);
  const tokens = name.replace(TITLE_RE, "").trim().split(/\s+/);
  return (titled ? tokens[tokens.length - 1] : tokens[0]).toLowerCase();
}

function escapeRe(s: string) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function guessGender(name: string, text: string, verbs: string[]): Gender {
  if (MALE_TITLE.test(name) || HI_MALE_KIN.test(name)) return "male";
  if (FEMALE_TITLE.test(name) || HI_FEMALE_KIN.test(name)) return "female";
  let he = 0;
  let she = 0;
  for (const v of verbs) {
    if (HI_MALE_VERB.test(v)) he += 2;
    if (HI_FEMALE_VERB.test(v)) she += 2;
  }
  const re = new RegExp(`${escapeRe(name)}([^.!?।"“”]{0,160})`, "g");
  let m: RegExpExecArray | null;
  let n = 0;
  while ((m = re.exec(text)) && n < 60) {
    n++;
    const first = m[1].match(/\b(he|him|his|himself|she|her|hers|herself)\b/i)?.[1].toLowerCase();
    if (!first) continue;
    if (first.startsWith("h") && first !== "her" && first !== "hers" && first !== "herself") he++;
    else she++;
  }
  if (he > she * 1.3 && he > 0) return "male";
  if (she > he * 1.3 && she > 0) return "female";
  return "neutral";
}

function emotionFor(text: string, verb?: string): Emotion {
  if (verb && VERB_EMOTION[verb]) return VERB_EMOTION[verb];
  const letters = text.replace(/[^\p{L}]/gu, "");
  if (letters.length > 4 && letters === letters.toUpperCase() && /\p{Lu}/u.test(letters)) return "shouting";
  if (/!{2,}/.test(text)) return "shouting";
  if (/!/.test(text)) return "excited";
  if (/…|\.\.\./.test(text) && text.length < 80) return "tender";
  return "neutral";
}

interface Span {
  kind: "narr" | "dlg";
  text: string;
  attr?: Attribution | null;
}

export interface HeuristicSpeaker {
  key: string;
  name: string;
  gender: Gender;
  lines: number;
  emotions: Partial<Record<Emotion, number>>;
}

export interface HeuristicLine {
  dialogue: boolean;
  speakerKey: string | null;
  emotion: Emotion;
  text: string;
  para: boolean;
}

export interface HeuristicResult {
  lines: HeuristicLine[];
  speakers: HeuristicSpeaker[];
  firstPerson: boolean;
}

export function heuristicLines(text: string): HeuristicResult {
  const paragraphs = splitParagraphs(text);
  const parsed: Span[][] = paragraphs.map((para) => {
    const spans: Span[] = [];
    let last = 0;
    for (const m of para.matchAll(QUOTE_RE)) {
      const idx = m.index ?? 0;
      if (idx > last) spans.push({ kind: "narr", text: para.slice(last, idx) });
      spans.push({ kind: "dlg", text: m[0] });
      last = idx + m[0].length;
    }
    if (last < para.length) spans.push({ kind: "narr", text: para.slice(last) });
    spans.forEach((s, i) => {
      if (s.kind !== "dlg") return;
      const after = spans[i + 1]?.kind === "narr" ? spans[i + 1].text.slice(0, 120) : "";
      const before = spans[i - 1]?.kind === "narr" ? spans[i - 1].text.slice(-120) : "";
      s.attr = attribute(after, before);
    });
    return spans;
  });

  const display = new Map<string, string>();
  const verbsBy = new Map<string, string[]>();
  for (const spans of parsed) {
    for (const s of spans) {
      if (!s.attr || s.attr.who.startsWith("__")) continue;
      const key = nameKey(s.attr.who);
      const prev = display.get(key);
      if (!prev || s.attr.who.length > prev.length) display.set(key, s.attr.who);
      if (s.attr.verb) verbsBy.set(key, [...(verbsBy.get(key) ?? []), s.attr.verb]);
    }
  }
  const genders = new Map<string, Gender>();
  for (const [key, name] of display) genders.set(key, guessGender(name, text, verbsBy.get(key) ?? []));

  const narrationWords = parsed
    .flat()
    .filter((s) => s.kind === "narr")
    .map((s) => s.text)
    .join(" ");
  const totalWords = narrationWords.split(/\s+/).length || 1;
  const firstCount = (narrationWords.match(/\bI\b|\bmy\b|\bme\b|मैं|मैंने|मेरा|मेरी/g) ?? []).length;
  const firstPerson = firstCount / totalWords > 0.015;

  const stats = new Map<string, HeuristicSpeaker>();
  const recent: string[] = [];
  const lines: HeuristicLine[] = [];
  let prevWasDialogue = false;

  const resolve = (a: Attribution): string => {
    if (a.who === "__first") return "__first";
    if (a.who === "__he" || a.who === "__she") {
      const want: Gender = a.who === "__he" ? "male" : "female";
      const hit = recent.find((k) => genders.get(k) === want);
      if (hit) return hit;
      const any = [...display.keys()].find((k) => genders.get(k) === want);
      return any ?? recent[0] ?? "__unknown";
    }
    return nameKey(a.who);
  };

  parsed.forEach((spans) => {
    const dialogues = spans.filter((s) => s.kind === "dlg");
    let speaker: string | null = null;
    if (dialogues.length) {
      const explicit = dialogues.find((d) => d.attr)?.attr;
      if (explicit) speaker = resolve(explicit);
      else if (prevWasDialogue && recent.length >= 2) speaker = recent[1];
      else speaker = "__unknown";
      const idx = recent.indexOf(speaker);
      if (idx !== -1) recent.splice(idx, 1);
      if (!speaker.startsWith("__")) recent.unshift(speaker);
      if (recent.length > 4) recent.pop();
    }
    prevWasDialogue = dialogues.length > 0;

    let first = true;
    for (const s of spans) {
      if (s.kind === "narr") {
        const t = s.text.replace(/^[\s,;:—–-]+/, "").trim();
        if (!letterCount(t)) continue;
        lines.push({ dialogue: false, speakerKey: null, emotion: "neutral", text: t, para: first });
      } else {
        const who = s.attr ? resolve(s.attr) : (speaker ?? "__unknown");
        const t = stripOuterQuotes(s.text);
        if (!letterCount(t)) continue;
        const emotion = emotionFor(t, s.attr?.verb);
        const key = who === "__first" && firstPerson ? null : who === "__first" ? "__unknown" : who;
        lines.push({ dialogue: true, speakerKey: key, emotion, text: t, para: first });
        if (key) {
          const st =
            stats.get(key) ??
            ({
              key,
              name: key === "__unknown" ? "Unnamed voice" : (display.get(key) ?? key),
              gender: genders.get(key) ?? "neutral",
              lines: 0,
              emotions: {},
            } satisfies HeuristicSpeaker);
          st.lines++;
          st.emotions[emotion] = (st.emotions[emotion] ?? 0) + 1;
          stats.set(key, st);
        }
      }
      first = false;
    }
  });

  const speakers = [...stats.values()].sort((a, b) => b.lines - a.lines);
  return { lines, speakers, firstPerson };
}

function traitsFrom(emotions: Partial<Record<Emotion, number>>): string[] {
  const top = Object.entries(emotions)
    .filter(([e]) => e !== "neutral")
    .sort((a, b) => (b[1] ?? 0) - (a[1] ?? 0))
    .slice(0, 2)
    .flatMap(([e]) => EMOTION_TRAITS[e as Emotion] ?? []);
  return top.length ? [...new Set(top)] : ["measured"];
}

export function analyzeHeuristic(text: string, title: string, languageHint: string): { analysis: Analysis; segments: Segment[] } {
  const { lines, speakers, firstPerson } = heuristicLines(text);
  const idByKey = new Map<string, string>();
  const characters: Character[] = speakers.map((sp, i) => {
    const id = `c${i + 1}`;
    idByKey.set(sp.key, id);
    const age: AgeGroup = "adult";
    return {
      id,
      name: sp.name,
      aliases: [],
      gender: sp.gender,
      age,
      role: i === 0 ? "lead" : sp.key === "__unknown" ? "background" : "supporting",
      personality: traitsFrom(sp.emotions),
      speakingStyle: "",
      voiceDescription: "",
      color: CHARACTER_COLORS[i % CHARACTER_COLORS.length],
      lineCount: sp.lines,
    };
  });
  const segments: Segment[] = lines.map((l) => ({
    id: uid("s"),
    speaker: l.speakerKey ? (idByKey.get(l.speakerKey) ?? NARRATOR_ID) : NARRATOR_ID,
    emotion: l.emotion,
    text: l.text,
    para: l.para,
  }));
  const language = languageHint || detectLanguage(text);
  return {
    analysis: {
      title: title || "Untitled chapter",
      language,
      mixedLanguages: [],
      pov: firstPerson ? "first" : "third",
      summary: "",
      narrator: { gender: "neutral", tone: "warm, measured storyteller", characterId: null },
      characters,
      engine: "Offline Director",
    },
    segments,
  };
}
