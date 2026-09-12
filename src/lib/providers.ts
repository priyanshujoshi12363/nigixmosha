export type TTSProviderId =
  | "nigix"
  | "edge"
  | "openai-tts"
  | "elevenlabs"
  | "sarvam-tts"
  | "gemini-tts"
  | "custom-tts";

export interface TTSProviderMeta {
  id: TTSProviderId;
  name: string;
  tagline: string;
  defaultBaseUrl: string;
  editableBaseUrl: boolean;
  needsKey: boolean;
  models: string[];
  defaultModel: string;
  free?: boolean;
  anyLanguage: boolean;
  languages?: string[];
  maxChars: number;
  instructable: boolean;
  liveVoices: boolean;
  keyUrl?: string;
  concurrency: number;
}

export const TTS_PROVIDERS: TTSProviderMeta[] = [
  {
    id: "nigix",
    name: "nigixmosha Voices",
    tagline: "Our own 54 studio voices for English and Hindi. Built in, free, nothing to set up.",
    defaultBaseUrl: "",
    editableBaseUrl: false,
    needsKey: false,
    models: ["nigix-1"],
    defaultModel: "nigix-1",
    free: true,
    anyLanguage: false,
    languages: ["en", "hi"],
    maxChars: 4000,
    instructable: false,
    liveVoices: false,
    concurrency: 2,
  },
  {
    id: "edge",
    name: "Edge Neural Voices",
    tagline: "400+ Microsoft neural voices in 100+ languages. Free, no key.",
    defaultBaseUrl: "",
    editableBaseUrl: false,
    needsKey: false,
    models: ["neural"],
    defaultModel: "neural",
    free: true,
    anyLanguage: false,
    maxChars: 3000,
    instructable: false,
    liveVoices: true,
    concurrency: 4,
  },
  {
    id: "sarvam-tts",
    name: "Sarvam Bulbul",
    tagline: "Natural voices for 11 Indian languages.",
    defaultBaseUrl: "https://api.sarvam.ai",
    editableBaseUrl: false,
    needsKey: true,
    models: ["bulbul:v3", "bulbul:v2"],
    defaultModel: "bulbul:v3",
    anyLanguage: false,
    languages: ["hi", "bn", "ta", "te", "kn", "ml", "mr", "gu", "pa", "od", "or", "en"],
    maxChars: 1400,
    instructable: false,
    liveVoices: false,
    keyUrl: "https://dashboard.sarvam.ai",
    concurrency: 3,
  },
  {
    id: "elevenlabs",
    name: "ElevenLabs",
    tagline: "Studio-grade expressive voices, 30+ languages.",
    defaultBaseUrl: "https://api.elevenlabs.io/v1",
    editableBaseUrl: false,
    needsKey: true,
    models: ["eleven_multilingual_v2", "eleven_v3", "eleven_flash_v2_5", "eleven_turbo_v2_5"],
    defaultModel: "eleven_multilingual_v2",
    anyLanguage: true,
    maxChars: 2500,
    instructable: false,
    liveVoices: true,
    keyUrl: "https://elevenlabs.io/app/settings/api-keys",
    concurrency: 2,
  },
  {
    id: "openai-tts",
    name: "OpenAI Voice",
    tagline: "gpt-4o-mini-tts takes acting direction per line.",
    defaultBaseUrl: "https://api.openai.com/v1",
    editableBaseUrl: false,
    needsKey: true,
    models: ["gpt-4o-mini-tts", "tts-1-hd", "tts-1"],
    defaultModel: "gpt-4o-mini-tts",
    anyLanguage: true,
    maxChars: 3800,
    instructable: true,
    liveVoices: false,
    keyUrl: "https://platform.openai.com/api-keys",
    concurrency: 3,
  },
  {
    id: "gemini-tts",
    name: "Gemini Speech",
    tagline: "30 controllable voices, 24 languages, style by prompt.",
    defaultBaseUrl: "https://generativelanguage.googleapis.com/v1beta",
    editableBaseUrl: false,
    needsKey: true,
    models: ["gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts"],
    defaultModel: "gemini-2.5-flash-preview-tts",
    anyLanguage: true,
    maxChars: 3000,
    instructable: true,
    liveVoices: false,
    keyUrl: "https://aistudio.google.com/apikey",
    concurrency: 2,
  },
  {
    id: "custom-tts",
    name: "Custom (OpenAI-compatible)",
    tagline: "Piper, XTTS, Kyutai or any /audio/speech server.",
    defaultBaseUrl: "http://localhost:8000/v1",
    editableBaseUrl: true,
    needsKey: false,
    models: ["tts-1"],
    defaultModel: "tts-1",
    free: true,
    anyLanguage: true,
    maxChars: 2000,
    instructable: false,
    liveVoices: false,
    concurrency: 2,
  },
];

export const getTTS = (id: string) => TTS_PROVIDERS.find((p) => p.id === id) ?? TTS_PROVIDERS[0];
