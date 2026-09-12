export interface LanguageInfo {
  code: string;
  name: string;
  native: string;
}

export const LANGUAGES: LanguageInfo[] = [
  { code: "en-US", name: "English (US)", native: "English" },
  { code: "en-GB", name: "English (UK)", native: "English" },
  { code: "en-IN", name: "English (India)", native: "English" },
  { code: "hi-IN", name: "Hindi", native: "हिन्दी" },
  { code: "bn-IN", name: "Bengali", native: "বাংলা" },
  { code: "ta-IN", name: "Tamil", native: "தமிழ்" },
  { code: "te-IN", name: "Telugu", native: "తెలుగు" },
  { code: "mr-IN", name: "Marathi", native: "मराठी" },
  { code: "gu-IN", name: "Gujarati", native: "ગુજરાતી" },
  { code: "kn-IN", name: "Kannada", native: "ಕನ್ನಡ" },
  { code: "ml-IN", name: "Malayalam", native: "മലയാളം" },
  { code: "pa-IN", name: "Punjabi", native: "ਪੰਜਾਬੀ" },
  { code: "or-IN", name: "Odia", native: "ଓଡ଼ିଆ" },
  { code: "ur-IN", name: "Urdu", native: "اردو" },
  { code: "ne-NP", name: "Nepali", native: "नेपाली" },
  { code: "es-ES", name: "Spanish", native: "Español" },
  { code: "fr-FR", name: "French", native: "Français" },
  { code: "de-DE", name: "German", native: "Deutsch" },
  { code: "it-IT", name: "Italian", native: "Italiano" },
  { code: "pt-BR", name: "Portuguese", native: "Português" },
  { code: "ru-RU", name: "Russian", native: "Русский" },
  { code: "ja-JP", name: "Japanese", native: "日本語" },
  { code: "ko-KR", name: "Korean", native: "한국어" },
  { code: "zh-CN", name: "Chinese", native: "中文" },
  { code: "ar-SA", name: "Arabic", native: "العربية" },
  { code: "tr-TR", name: "Turkish", native: "Türkçe" },
  { code: "id-ID", name: "Indonesian", native: "Bahasa Indonesia" },
];

export function languageName(code: string) {
  const exact = LANGUAGES.find((l) => l.code.toLowerCase() === code.toLowerCase());
  if (exact) return exact.name;
  const prefix = code.split("-")[0].toLowerCase();
  return LANGUAGES.find((l) => l.code.startsWith(prefix + "-"))?.name ?? code;
}

export function normalizeLocale(code: string): string {
  const [lang, region] = code.trim().replace("_", "-").split("-");
  if (!lang) return "en-US";
  const l = lang.toLowerCase();
  if (region) return `${l}-${region.toUpperCase()}`;
  return LANGUAGES.find((x) => x.code.startsWith(l + "-"))?.code ?? l;
}

const SCRIPTS: [RegExp, string][] = [
  [/\p{Script=Devanagari}/gu, "hi-IN"],
  [/\p{Script=Bengali}/gu, "bn-IN"],
  [/\p{Script=Gurmukhi}/gu, "pa-IN"],
  [/\p{Script=Gujarati}/gu, "gu-IN"],
  [/\p{Script=Oriya}/gu, "or-IN"],
  [/\p{Script=Tamil}/gu, "ta-IN"],
  [/\p{Script=Telugu}/gu, "te-IN"],
  [/\p{Script=Kannada}/gu, "kn-IN"],
  [/\p{Script=Malayalam}/gu, "ml-IN"],
  [/\p{Script=Arabic}/gu, "ar-SA"],
  [/\p{Script=Cyrillic}/gu, "ru-RU"],
  [/[\p{Script=Hiragana}\p{Script=Katakana}]/gu, "ja-JP"],
  [/\p{Script=Hangul}/gu, "ko-KR"],
  [/\p{Script=Han}/gu, "zh-CN"],
];

const LATIN_HINTS: [string, string[]][] = [
  ["en-US", ["the", "and", "was", "that", "with", "his", "her", "said"]],
  ["es-ES", ["el", "la", "que", "los", "una", "dijo", "pero", "por"]],
  ["fr-FR", ["le", "les", "est", "une", "dans", "qui", "pas", "dit"]],
  ["de-DE", ["der", "die", "und", "nicht", "ist", "sie", "ich", "sagte"]],
  ["it-IT", ["il", "che", "non", "una", "della", "per", "sono", "disse"]],
  ["pt-BR", ["não", "uma", "que", "ele", "ela", "disse", "com", "para"]],
  ["id-ID", ["yang", "dan", "itu", "tidak", "dengan", "aku", "kata", "ini"]],
  ["tr-TR", ["bir", "ve", "bu", "için", "ama", "dedi", "çok", "gibi"]],
];

export function detectLanguage(text: string): string {
  const sample = text.slice(0, 20000);
  let best = "";
  let bestCount = 0;
  for (const [re, code] of SCRIPTS) {
    const n = sample.match(re)?.length ?? 0;
    if (n > bestCount) {
      best = code;
      bestCount = n;
    }
  }
  const latin = sample.match(/[A-Za-z]/g)?.length ?? 0;
  if (best && bestCount > latin * 0.3) {
    if (best === "zh-CN" && (sample.match(/[\p{Script=Hiragana}\p{Script=Katakana}]/gu)?.length ?? 0) > 20) return "ja-JP";
    if (best === "ar-SA" && /[ےںٹڈڑ]/.test(sample)) return "ur-IN";
    if (best === "hi-IN" && /(आहे|आणि|होते|झाले)/.test(sample)) return "mr-IN";
    return best;
  }
  const words = sample.toLowerCase().match(/[\p{L}']+/gu) ?? [];
  const freq = new Map<string, number>();
  for (const w of words) freq.set(w, (freq.get(w) ?? 0) + 1);
  let lang = "en-US";
  let score = 0;
  for (const [code, hints] of LATIN_HINTS) {
    const s = hints.reduce((acc, h) => acc + (freq.get(h) ?? 0), 0);
    if (s > score) {
      score = s;
      lang = code;
    }
  }
  return lang;
}

export const langPrefix = (code: string) => code.split("-")[0].toLowerCase();
