import type { AgeGroup, Gender, VoiceProfile } from "./types";
import type { TTSProviderId } from "./providers";

const v = (
  id: string,
  name: string,
  gender: Gender,
  langs: string[],
  tags: string[],
  extra: Partial<VoiceProfile> = {},
): VoiceProfile => ({ id, name, gender, langs, tags, ...extra });

export function edgeDisplayName(short: string) {
  return short
    .split("-")
    .slice(2)
    .join(" ")
    .replace(/Neural$/, "")
    .replace(/Multilingual/, " Multilingual")
    .trim();
}

const edge = (short: string, gender: Gender, tags: string[], extra: Partial<VoiceProfile> = {}) => {
  const locale = short.split("-").slice(0, 2).join("-");
  return v(short, edgeDisplayName(short), gender, [locale], tags, {
    multilingual: short.includes("Multilingual"),
    ...extra,
  });
};

export const EDGE_FALLBACK: VoiceProfile[] = [
  edge("en-US-AvaMultilingualNeural", "female", ["warm", "expressive", "confident", "narrator"]),
  edge("en-US-AndrewMultilingualNeural", "male", ["warm", "confident", "authentic", "narrator"]),
  edge("en-US-EmmaMultilingualNeural", "female", ["cheerful", "clear", "friendly"]),
  edge("en-US-BrianMultilingualNeural", "male", ["casual", "sincere", "approachable"]),
  edge("en-US-AriaNeural", "female", ["positive", "confident"]),
  edge("en-US-JennyNeural", "female", ["friendly", "considerate", "comfort"]),
  edge("en-US-MichelleNeural", "female", ["friendly", "pleasant"]),
  edge("en-US-AnaNeural", "female", ["cute", "bright"], { age: "child" }),
  edge("en-US-GuyNeural", "male", ["passion", "energetic"]),
  edge("en-US-ChristopherNeural", "male", ["reliable", "authority", "deep"]),
  edge("en-US-EricNeural", "male", ["rational", "calm"]),
  edge("en-US-RogerNeural", "male", ["lively", "mature"], { age: "middle" }),
  edge("en-US-SteffanNeural", "male", ["rational", "crisp"]),
  edge("en-GB-SoniaNeural", "female", ["gentle", "soft", "narrator"]),
  edge("en-GB-LibbyNeural", "female", ["bright", "friendly"]),
  edge("en-GB-MaisieNeural", "female", ["bright", "playful"], { age: "child" }),
  edge("en-GB-RyanNeural", "male", ["bright", "engaging"]),
  edge("en-GB-ThomasNeural", "male", ["calm", "mature"]),
  edge("en-IN-NeerjaNeural", "female", ["warm", "clear"]),
  edge("en-IN-PrabhatNeural", "male", ["calm", "clear"]),
  edge("hi-IN-SwaraNeural", "female", ["warm", "clear", "narrator"]),
  edge("hi-IN-MadhurNeural", "male", ["calm", "deep"]),
  edge("bn-IN-TanishaaNeural", "female", ["warm"]),
  edge("bn-IN-BashkarNeural", "male", ["calm"]),
  edge("ta-IN-PallaviNeural", "female", ["warm"]),
  edge("ta-IN-ValluvarNeural", "male", ["calm"]),
  edge("te-IN-ShrutiNeural", "female", ["warm"]),
  edge("te-IN-MohanNeural", "male", ["calm"]),
  edge("mr-IN-AarohiNeural", "female", ["warm"]),
  edge("mr-IN-ManoharNeural", "male", ["calm"]),
  edge("gu-IN-DhwaniNeural", "female", ["warm"]),
  edge("gu-IN-NiranjanNeural", "male", ["calm"]),
  edge("kn-IN-SapnaNeural", "female", ["warm"]),
  edge("kn-IN-GaganNeural", "male", ["calm"]),
  edge("ml-IN-SobhanaNeural", "female", ["warm"]),
  edge("ml-IN-MidhunNeural", "male", ["calm"]),
  edge("ur-IN-GulNeural", "female", ["warm"]),
  edge("ur-IN-SalmanNeural", "male", ["calm"]),
  edge("ur-PK-UzmaNeural", "female", ["warm"]),
  edge("ur-PK-AsadNeural", "male", ["calm"]),
  edge("es-ES-ElviraNeural", "female", ["warm"]),
  edge("es-ES-AlvaroNeural", "male", ["confident"]),
  edge("es-MX-DaliaNeural", "female", ["friendly"]),
  edge("es-MX-JorgeNeural", "male", ["calm"]),
  edge("fr-FR-DeniseNeural", "female", ["warm"]),
  edge("fr-FR-HenriNeural", "male", ["calm"]),
  edge("fr-FR-VivienneMultilingualNeural", "female", ["expressive"]),
  edge("fr-FR-RemyMultilingualNeural", "male", ["warm"]),
  edge("de-DE-KatjaNeural", "female", ["calm"]),
  edge("de-DE-ConradNeural", "male", ["deep"]),
  edge("de-DE-SeraphinaMultilingualNeural", "female", ["warm"]),
  edge("de-DE-FlorianMultilingualNeural", "male", ["warm"]),
  edge("it-IT-ElsaNeural", "female", ["warm"]),
  edge("it-IT-DiegoNeural", "male", ["calm"]),
  edge("pt-BR-FranciscaNeural", "female", ["warm"]),
  edge("pt-BR-AntonioNeural", "male", ["calm"]),
  edge("ja-JP-NanamiNeural", "female", ["warm", "friendly"]),
  edge("ja-JP-KeitaNeural", "male", ["calm"]),
  edge("ko-KR-SunHiNeural", "female", ["bright"]),
  edge("ko-KR-InJoonNeural", "male", ["calm"]),
  edge("zh-CN-XiaoxiaoNeural", "female", ["warm", "narrator"]),
  edge("zh-CN-XiaoyiNeural", "female", ["lively", "bright"], { age: "young" }),
  edge("zh-CN-YunxiNeural", "male", ["lively", "bright"]),
  edge("zh-CN-YunjianNeural", "male", ["passion", "deep"]),
  edge("zh-CN-YunyangNeural", "male", ["professional", "reliable"]),
  edge("ar-SA-ZariyahNeural", "female", ["warm"]),
  edge("ar-SA-HamedNeural", "male", ["calm"]),
  edge("ru-RU-SvetlanaNeural", "female", ["warm"]),
  edge("ru-RU-DmitryNeural", "male", ["calm"]),
  edge("tr-TR-EmelNeural", "female", ["warm"]),
  edge("tr-TR-AhmetNeural", "male", ["calm"]),
  edge("id-ID-GadisNeural", "female", ["warm"]),
  edge("id-ID-ArdiNeural", "male", ["calm"]),
  edge("ne-NP-HemkalaNeural", "female", ["warm"]),
  edge("ne-NP-SagarNeural", "male", ["calm"]),
];

const n = (id: string, name: string, gender: Gender, langs: string[], tags: string[], extra: Partial<VoiceProfile> = {}) =>
  v(id, name, gender, langs, tags, extra);

const US = ["en-US"];
const UK = ["en-GB"];
const IN = ["hi-IN", "en-IN"];

export const NIGIX_VOICES: VoiceProfile[] = [
  n("solenne", "Solenne", "female", US, ["warm", "expressive", "storyteller", "narrator"]),
  n("vesper", "Vesper", "female", US, ["bright", "lively", "expressive"]),
  n("elowen", "Elowen", "female", US, ["soft", "breathy", "gentle", "whisper"]),
  n("maren", "Maren", "female", US, ["clear", "friendly"]),
  n("liora", "Liora", "female", US, ["light", "youthful", "bright"], { age: "young" }),
  n("tamsin", "Tamsin", "female", US, ["crisp", "confident"]),
  n("wrenna", "Wrenna", "female", US, ["calm", "even"]),
  n("calla", "Calla", "female", US, ["casual", "conversational"]),
  n("isolde", "Isolde", "female", US, ["firm", "steady", "authority"]),
  n("odalys", "Odalys", "female", US, ["airy", "gentle", "breezy"]),
  n("zelia", "Zelia", "female", US, ["neutral", "balanced", "clear"]),
  n("caspian", "Caspian", "male", US, ["warm", "grounded", "narrator", "storyteller"]),
  n("dorian", "Dorian", "male", US, ["deep", "resonant", "authority"]),
  n("emrys", "Emrys", "male", US, ["soft", "calm", "gentle"]),
  n("falco", "Falco", "male", US, ["confident", "direct", "crisp"]),
  n("thorne", "Thorne", "male", US, ["rough", "gravelly", "deep", "intense"]),
  n("kellan", "Kellan", "male", US, ["youthful", "easygoing", "casual"], { age: "young" }),
  n("lucan", "Lucan", "male", US, ["deep", "authority", "firm"]),
  n("rafe", "Rafe", "male", US, ["playful", "bright", "energetic"]),
  n("barnaby", "Barnaby", "male", US, ["jolly", "mature", "wise"], { age: "elderly" }),
  n("primrose", "Primrose", "female", UK, ["warm", "graceful", "narrator"]),
  n("rosalind", "Rosalind", "female", UK, ["elegant", "poised", "refined"]),
  n("ottilie", "Ottilie", "female", UK, ["bright", "clear"]),
  n("winnie", "Winnie", "female", UK, ["soft", "gentle"]),
  n("cedric", "Cedric", "male", UK, ["mature", "deep", "storyteller", "narrator"], { age: "middle" }),
  n("bramwell", "Bramwell", "male", UK, ["expressive", "storyteller", "warm"]),
  n("hollis", "Hollis", "male", UK, ["calm", "measured", "even"]),
  n("alistair", "Alistair", "male", UK, ["crisp", "refined", "clear"]),
  n("chandni", "Chandni", "female", IN, ["warm", "clear", "narrator"], { multilingual: true }),
  n("meher", "Meher", "female", IN, ["bright", "soft"], { multilingual: true }),
  n("vikrant", "Vikrant", "male", IN, ["deep", "steady", "authority"], { multilingual: true }),
  n("ojas", "Ojas", "male", IN, ["calm", "mature", "narrator"], { multilingual: true, age: "middle" }),
  n("amaia", "Amaia", "female", US, ["warm", "accented"]),
  n("ignacio", "Ignacio", "male", US, ["calm", "accented"]),
  n("rufino", "Rufino", "male", US, ["jolly", "mature", "accented"], { age: "elderly" }),
  n("celeste", "Celeste", "female", US, ["warm", "graceful", "accented"]),
  n("fiorella", "Fiorella", "female", US, ["warm", "lively", "accented"]),
  n("massimo", "Massimo", "male", US, ["calm", "grounded", "accented"]),
  n("akane", "Akane", "female", US, ["clear", "warm", "accented"]),
  n("kohaku", "Kohaku", "female", US, ["storyteller", "accented"]),
  n("mirai", "Mirai", "female", US, ["light", "gentle", "accented"]),
  n("yuzu", "Yuzu", "female", US, ["soft", "youthful", "accented"], { age: "young" }),
  n("haruto", "Haruto", "male", US, ["calm", "accented"]),
  n("iolanda", "Iolanda", "female", US, ["warm", "accented"]),
  n("bento", "Bento", "male", US, ["calm", "accented"]),
  n("otavio", "Otavio", "male", US, ["jolly", "mature", "accented"], { age: "elderly" }),
  n("meilin", "Meilin", "female", US, ["soft", "accented"]),
  n("anqi", "Anqi", "female", US, ["bright", "accented"]),
  n("ruolan", "Ruolan", "female", US, ["warm", "accented"]),
  n("jiayu", "Jiayu", "female", US, ["lively", "accented"]),
  n("haoran", "Haoran", "male", US, ["deep", "accented"]),
  n("jingwei", "Jingwei", "male", US, ["bright", "accented"]),
  n("boyang", "Boyang", "male", US, ["gentle", "accented"]),
  n("tianming", "Tianming", "male", US, ["steady", "accented"]),
];

const INDIC = ["hi", "bn", "ta", "te", "kn", "ml", "mr", "gu", "pa", "od", "or", "en"];
const s = (id: string, gender: Gender, tags: string[], age?: AgeGroup) =>
  v(id, id[0].toUpperCase() + id.slice(1), gender, INDIC, tags, { age, multilingual: true });

export const SARVAM_V3_VOICES: VoiceProfile[] = [
  s("shubh", "male", ["warm", "narrator"]),
  s("aditya", "male", ["confident"]),
  s("rahul", "male", ["friendly"]),
  s("rohan", "male", ["youthful", "bright"], "young"),
  s("amit", "male", ["calm"]),
  s("dev", "male", ["crisp"]),
  s("ratan", "male", ["mature", "deep"], "elderly"),
  s("varun", "male", ["energetic"]),
  s("manan", "male", ["soft"]),
  s("sumit", "male", ["casual"]),
  s("kabir", "male", ["deep", "authority"]),
  s("aayan", "male", ["youthful"], "teen"),
  s("ashutosh", "male", ["serious"], "middle"),
  s("advait", "male", ["calm"]),
  s("anand", "male", ["warm"], "middle"),
  s("tarun", "male", ["bright"]),
  s("sunny", "male", ["playful"]),
  s("mani", "male", ["gentle"]),
  s("gokul", "male", ["warm"]),
  s("vijay", "male", ["confident"]),
  s("mohit", "male", ["friendly"]),
  s("rehan", "male", ["crisp"]),
  s("soham", "male", ["youthful"]),
  s("ritu", "female", ["warm", "narrator"]),
  s("priya", "female", ["bright", "friendly"]),
  s("neha", "female", ["clear"]),
  s("pooja", "female", ["gentle"]),
  s("simran", "female", ["expressive"]),
  s("kavya", "female", ["youthful", "bright"], "young"),
  s("ishita", "female", ["cheerful"]),
  s("shreya", "female", ["soft"]),
  s("roopa", "female", ["mature", "warm"], "middle"),
  s("tanya", "female", ["confident"]),
  s("shruti", "female", ["calm"]),
  s("suhani", "female", ["playful"], "teen"),
  s("kavitha", "female", ["warm"], "middle"),
  s("rupali", "female", ["serious"]),
];

export const SARVAM_V2_VOICES: VoiceProfile[] = [
  s("anushka", "female", ["clear", "narrator"]),
  s("manisha", "female", ["warm"]),
  s("vidya", "female", ["gentle"]),
  s("arya", "female", ["bright", "youthful"]),
  s("abhilash", "male", ["deep", "narrator"]),
  s("karun", "male", ["calm"]),
  s("hitesh", "male", ["bright"]),
];

const el = (id: string, name: string, gender: Gender, tags: string[], age?: AgeGroup) =>
  v(id, name, gender, ["*"], tags, { age, multilingual: true });

export const ELEVEN_FALLBACK: VoiceProfile[] = [
  el("JBFqnCBsd6RMkjVDRZzb", "George", "male", ["warm", "mature", "narrator", "storyteller"], "middle"),
  el("nPczCjzI2devNBz1zQrb", "Brian", "male", ["deep", "narrator"], "middle"),
  el("onwK4e9ZLuTAKqWW03F9", "Daniel", "male", ["authority", "crisp"], "middle"),
  el("TX3LPaxmHKxFdv7VOQHJ", "Liam", "male", ["youthful", "energetic"], "young"),
  el("N2lVS1w4EtoT3dr4eOWO", "Callum", "male", ["gravelly", "intense"]),
  el("IKne3meq5aSn9XLyUdCD", "Charlie", "male", ["casual", "friendly"], "young"),
  el("CwhRBWXzGAHq8TQ4Fs17", "Roger", "male", ["confident", "casual"], "middle"),
  el("bIHbv24MWmeRgasZH58o", "Will", "male", ["friendly", "bright"], "young"),
  el("cjVigY5qzO86Huf0OWal", "Eric", "male", ["smooth", "calm"], "middle"),
  el("iP95p4xoKVk53GoZ742B", "Chris", "male", ["casual"], "adult"),
  el("pqHfZKP75CvOlQylNhV4", "Bill", "male", ["mature", "wise"], "elderly"),
  el("EXAVITQu4vr4xnSDxMaL", "Sarah", "female", ["soft", "warm", "narrator"], "young"),
  el("XrExE9yKIg1WjnnlVkGX", "Matilda", "female", ["warm", "friendly"], "adult"),
  el("Xb7hH8MSUJpSbSDYk0k2", "Alice", "female", ["confident", "crisp"], "adult"),
  el("XB0fDUnXU5powFXDhCwa", "Charlotte", "female", ["seductive", "calm"], "young"),
  el("FGY2WhTYpPnrIDTdsKH5", "Laura", "female", ["bright", "playful"], "young"),
  el("cgSgspJ2msm6clMCkdW9", "Jessica", "female", ["expressive", "bright"], "young"),
  el("pFZP5JQG7iQjIQuC4Bku", "Lily", "female", ["gentle", "warm"], "middle"),
  el("9BWtsMINqrJLrRacOk9x", "Aria", "female", ["expressive", "raspy"], "middle"),
  el("SAz9YHcvj6GT2YYXdXww", "River", "neutral", ["calm", "neutral"]),
];

const o = (id: string, gender: Gender, tags: string[]) =>
  v(id, id[0].toUpperCase() + id.slice(1), gender, ["*"], tags, { multilingual: true });

export const OPENAI_VOICES: VoiceProfile[] = [
  o("alloy", "neutral", ["balanced", "clear"]),
  o("ash", "male", ["confident", "crisp"]),
  o("ballad", "male", ["warm", "storyteller", "gentle"]),
  o("coral", "female", ["warm", "friendly", "narrator"]),
  o("echo", "male", ["calm", "soft"]),
  o("fable", "male", ["storyteller", "expressive", "narrator"]),
  o("onyx", "male", ["deep", "authority"]),
  o("nova", "female", ["bright", "energetic", "youthful"]),
  o("sage", "female", ["calm", "wise"]),
  o("shimmer", "female", ["soft", "gentle"]),
  o("verse", "male", ["expressive", "versatile"]),
  o("marin", "female", ["natural", "warm"]),
  o("cedar", "male", ["natural", "deep"]),
];

const g = (id: string, gender: Gender, tags: string[], age?: AgeGroup) =>
  v(id, id, gender, ["*"], tags, { age, multilingual: true });

export const GEMINI_VOICES: VoiceProfile[] = [
  g("Kore", "female", ["firm", "confident", "narrator"]),
  g("Zephyr", "female", ["bright"]),
  g("Leda", "female", ["youthful", "bright"], "young"),
  g("Aoede", "female", ["breezy", "friendly"]),
  g("Callirrhoe", "female", ["easy-going", "calm"]),
  g("Autonoe", "female", ["bright"]),
  g("Despina", "female", ["smooth", "warm"]),
  g("Erinome", "female", ["clear"]),
  g("Laomedeia", "female", ["upbeat", "energetic"]),
  g("Achernar", "female", ["soft", "gentle"]),
  g("Gacrux", "female", ["mature", "wise"], "elderly"),
  g("Pulcherrima", "female", ["forward", "confident"]),
  g("Vindemiatrix", "female", ["gentle", "tender"]),
  g("Sulafat", "female", ["warm", "narrator"]),
  g("Puck", "male", ["upbeat", "playful"]),
  g("Charon", "male", ["informative", "calm", "narrator"]),
  g("Fenrir", "male", ["excitable", "energetic"]),
  g("Orus", "male", ["firm", "authority"]),
  g("Enceladus", "male", ["breathy", "soft"]),
  g("Iapetus", "male", ["clear"]),
  g("Umbriel", "male", ["easy-going", "casual"]),
  g("Algieba", "male", ["smooth", "warm"]),
  g("Algenib", "male", ["gravelly", "deep"], "middle"),
  g("Rasalgethi", "male", ["informative"]),
  g("Alnilam", "male", ["firm", "serious"]),
  g("Schedar", "male", ["even", "calm"]),
  g("Achird", "male", ["friendly"]),
  g("Zubenelgenubi", "male", ["casual"]),
  g("Sadachbia", "male", ["lively", "youthful"], "young"),
  g("Sadaltager", "male", ["knowledgeable", "mature"], "elderly"),
];

export function backupVoice(gender: Gender, language: string, seed = ""): string {
  const prefix = language.split("-")[0].toLowerCase();
  const sameLang = EDGE_FALLBACK.filter((x) => x.langs.some((l) => l.split("-")[0].toLowerCase() === prefix));
  const pool = sameLang.length ? sameLang : EDGE_FALLBACK.filter((x) => x.langs[0].startsWith("en"));
  const matched = pool.filter((x) => x.gender === gender);
  const list = matched.length ? matched : pool;
  let hash = 0;
  for (const ch of seed) hash = (hash * 31 + ch.charCodeAt(0)) % 9973;
  return list[hash % list.length].id;
}

export function staticVoices(provider: TTSProviderId, model?: string): VoiceProfile[] {
  switch (provider) {
    case "nigix":
      return NIGIX_VOICES;
    case "edge":
      return EDGE_FALLBACK;
    case "custom-tts":
      return OPENAI_VOICES;
    case "sarvam-tts":
      return model === "bulbul:v2" ? SARVAM_V2_VOICES : SARVAM_V3_VOICES;
    case "elevenlabs":
      return ELEVEN_FALLBACK;
    case "openai-tts":
      return model?.startsWith("tts-1")
        ? OPENAI_VOICES.filter((x) => !["ballad", "verse", "marin", "cedar"].includes(x.id))
        : OPENAI_VOICES;
    case "gemini-tts":
      return GEMINI_VOICES;
  }
}
