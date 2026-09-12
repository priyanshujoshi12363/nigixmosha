import type { TTSProviderId, TTSProviderMeta } from "@/lib/providers";
import type { AgeGroup, Analysis, CastEntry, Character, Gender, VoiceProfile } from "@/lib/types";
import { NARRATOR_ID } from "@/lib/types";
import { clamp } from "@/lib/utils";
import { langPrefix } from "./lang";

const TRAIT_TAGS: [RegExp, string[]][] = [
  [/brave|bold|confident|leader|command|authorit|proud|strong|determined|stern|firm|noble/, ["confident", "authority", "firm", "deep", "reliable"]],
  [/kind|gentle|caring|loving|warm|nurtur|soft|sweet|compassion|tender/, ["warm", "gentle", "soft", "friendly", "comfort", "considerate"]],
  [/cheer|playful|witty|funny|energetic|excit|impulsive|lively|mischiev|bubbly|enthusias|fiery/, ["bright", "energetic", "playful", "lively", "cheerful", "upbeat", "passion", "excitable"]],
  [/wise|calm|stoic|patient|thought|serene|reserved|quiet|measured|old/, ["calm", "mature", "wise", "even", "rational", "knowledgeable"]],
  [/villain|cruel|menac|cold|cunning|sinister|ruthless|dark|arrogant|threat|brood/, ["deep", "gravelly", "serious", "intense", "firm"]],
  [/shy|timid|nervous|anxious|meek|insecure|hesitant/, ["soft", "gentle", "breathy", "youthful"]],
  [/sarcas|dry|cynic|sardonic|blunt/, ["crisp", "casual", "rational"]],
  [/formal|professional|precise|educated|intellect|scholar/, ["crisp", "clear", "professional", "informative", "knowledgeable"]],
  [/gruff|rough|tough|grizzled|hoarse|husky/, ["gravelly", "raspy", "deep"]],
  [/young|youth|innocent|naive|curious/, ["youthful", "bright", "cute"]],
];

const NARRATOR_TAGS = ["narrator", "novel", "storyteller", "audiobook", "warm", "news"];

const AGE_PROSODY: Record<AgeGroup, { pitch: number; rate: number }> = {
  child: { pitch: 16, rate: 6 },
  teen: { pitch: 8, rate: 4 },
  young: { pitch: 3, rate: 2 },
  adult: { pitch: 0, rate: 0 },
  middle: { pitch: -3, rate: -2 },
  elderly: { pitch: -7, rate: -10 },
};

const VARIANTS = [
  { pitch: 0, rate: 0 },
  { pitch: 9, rate: 4 },
  { pitch: -9, rate: -4 },
  { pitch: 15, rate: -3 },
  { pitch: -15, rate: 3 },
  { pitch: 5, rate: -8 },
  { pitch: -5, rate: 8 },
];

export function supportsPitch(provider: TTSProviderId, model: string) {
  return provider === "edge" || (provider === "sarvam-tts" && model === "bulbul:v2");
}

function describe(c: Character) {
  return [c.personality.join(" "), c.speakingStyle, c.voiceDescription, c.role].join(" ").toLowerCase();
}

function wantedTags(c: Character | null, narratorTone: string): Set<string> {
  const text = c ? describe(c) : narratorTone.toLowerCase();
  const tags = new Set<string>();
  for (const [re, t] of TRAIT_TAGS) if (re.test(text)) t.forEach((x) => tags.add(x));
  for (const word of text.split(/[^a-z]+/)) if (word.length > 3) tags.add(word);
  return tags;
}

function baseProsody(c: Character | null) {
  if (!c) return { pitch: 0, rate: -3 };
  const p = { ...AGE_PROSODY[c.age] };
  const text = describe(c);
  if (/energetic|impulsive|excit|lively|fast|quick|hyper|bubbly/.test(text)) p.rate += 6;
  if (/calm|wise|slow|measured|patient|deliberate|stoic/.test(text)) p.rate -= 5;
  if (/nervous|anxious|timid/.test(text)) {
    p.rate += 4;
    p.pitch += 2;
  }
  if (/menac|cold|sinister|gruff|deep/.test(text)) {
    p.pitch -= 4;
    p.rate -= 3;
  }
  return p;
}

export function candidateVoices(voices: VoiceProfile[], meta: TTSProviderMeta, language: string) {
  if (meta.anyLanguage) return voices.map((voice) => ({ voice, native: true }));
  const p = langPrefix(language);
  const speaks = (v: VoiceProfile) => v.langs.some((l) => l === "*" || langPrefix(l) === p);
  const native = voices.filter(speaks).map((voice) => ({ voice, native: true }));
  const multi = voices.filter((v) => v.multilingual && !speaks(v)).map((voice) => ({ voice, native: false }));
  if (native.length) return [...native, ...multi];
  if (multi.length) return multi;
  return voices.map((voice) => ({ voice, native: false }));
}

function score(
  voice: VoiceProfile,
  native: boolean,
  gender: Gender,
  age: AgeGroup,
  tags: Set<string>,
  isNarrator: boolean,
  language: string,
  uses: number,
) {
  let s = 0;
  if (gender !== "neutral") s += voice.gender === gender ? 6 : voice.gender === "neutral" ? 1 : -12;
  if (voice.age && voice.age === age) s += 3;
  if (age === "child" || age === "teen") {
    if (voice.age === "child") s += age === "child" ? 6 : 1;
    if (voice.age === "elderly" || voice.age === "middle") s -= 4;
  } else if (voice.age === "child") s -= 9;
  if (age === "elderly" && voice.age === "middle") s += 1.5;
  for (const t of voice.tags) if (tags.has(t)) s += 1.5;
  if (isNarrator && voice.tags.some((t) => NARRATOR_TAGS.includes(t))) s += 3;
  if (native) {
    if (voice.langs.includes(language)) s += 1.5;
  } else s -= 3;
  return s - uses * 7;
}

export function autoCast(
  analysis: Analysis,
  voices: VoiceProfile[],
  meta: TTSProviderMeta,
  model: string,
): Record<string, CastEntry> {
  const pool = candidateVoices(voices, meta, analysis.language);
  if (!pool.length) return {};
  const pitchOK = supportsPitch(meta.id, model);
  const used = new Map<string, number>();
  const cast: Record<string, CastEntry> = {};
  const order: (Character | null)[] = [null, ...[...analysis.characters].sort((a, b) => b.lineCount - a.lineCount)];

  for (const c of order) {
    const gender = c ? c.gender : analysis.narrator.gender;
    const age: AgeGroup = c ? c.age : "adult";
    const tags = wantedTags(c, analysis.narrator.tone);
    let best = pool[0].voice;
    let bestScore = -Infinity;
    for (const { voice, native } of pool) {
      const sc = score(voice, native, gender, age, tags, !c, analysis.language, used.get(voice.id) ?? 0);
      if (sc > bestScore) {
        bestScore = sc;
        best = voice;
      }
    }
    const reuse = used.get(best.id) ?? 0;
    used.set(best.id, reuse + 1);
    const base = baseProsody(c);
    const variant = VARIANTS[reuse % VARIANTS.length];
    cast[c ? c.id : NARRATOR_ID] = {
      voiceId: best.id,
      pitch: pitchOK ? clamp(base.pitch + variant.pitch, -30, 30) : 0,
      rate: clamp(base.rate + (pitchOK ? variant.rate : variant.rate * 1.5), -35, 35),
    };
  }
  return cast;
}

export function castFor(
  speaker: string,
  cast: Record<string, CastEntry>,
  analysis: Analysis,
  shareNarrator: boolean,
): CastEntry | undefined {
  if (speaker === NARRATOR_ID && shareNarrator && analysis.narrator.characterId) {
    return cast[analysis.narrator.characterId] ?? cast[NARRATOR_ID];
  }
  return cast[speaker] ?? cast[NARRATOR_ID];
}
