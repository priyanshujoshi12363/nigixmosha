import type { TTSProviderId } from "@/lib/providers";

const TTS_ENV: Partial<Record<TTSProviderId, string>> = {
  "openai-tts": "OPENAI_API_KEY",
  elevenlabs: "ELEVENLABS_API_KEY",
  "sarvam-tts": "SARVAM_API_KEY",
  "gemini-tts": "GEMINI_API_KEY",
};

const OLLAMA_CLOUD = "https://ollama.com";

const read = (name?: string) => (name ? process.env[name]?.trim() || undefined : undefined);

export const ttsServerKey = (id: string) => read(TTS_ENV[id as TTSProviderId]);

export function voiceServer() {
  return {
    url: read("NIGIX_TTS_URL")?.replace(/\/+$/, ""),
    token: read("NIGIX_TTS_TOKEN"),
  };
}

export function brainConfig() {
  const baseUrl = (read("OLLAMA_BASE_URL") ?? OLLAMA_CLOUD).replace(/\/+$/, "");
  return {
    baseUrl,
    cloud: baseUrl === OLLAMA_CLOUD,
    model: read("OLLAMA_MODEL") ?? "gpt-oss:120b",
    apiKey: read("OLLAMA_API_KEY"),
  };
}

export function brainReady() {
  const cfg = brainConfig();
  return !cfg.cloud || Boolean(cfg.apiKey);
}

export function serverKeyFlags() {
  return {
    ...Object.fromEntries(Object.keys(TTS_ENV).map((k) => [k, Boolean(ttsServerKey(k))])),
    nigix: Boolean(voiceServer().url),
  };
}

export function storageFlags() {
  return {
    db: Boolean(process.env.MONGODB_URI?.trim()),
    media: Boolean(
      process.env.CLOUDINARY_CLOUD_NAME?.trim() &&
        process.env.CLOUDINARY_API_KEY?.trim() &&
        process.env.CLOUDINARY_API_SECRET?.trim(),
    ),
  };
}
