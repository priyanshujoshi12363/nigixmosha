import type { TTSProviderId } from "@/lib/providers";
import type { ProviderConfig, SynthesisStyle, VoiceProfile } from "@/lib/types";

export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

export async function fail(res: Response): Promise<never> {
  let msg = `${res.status} ${res.statusText}`;
  let code: string | undefined;
  try {
    const j = await res.json();
    if (j?.error) msg = j.error;
    code = j?.code;
  } catch {}
  if (code === "unauthenticated" && typeof window !== "undefined") {
    const target = new URL(`/login?next=${encodeURIComponent(window.location.pathname)}`, window.location.origin);
    window.location.replace(target.href);
  }
  throw new ApiError(msg, res.status);
}

export async function requestBrain(
  system: string,
  prompt: string,
  opts: { json?: boolean; signal?: AbortSignal } = {},
): Promise<string> {
  const res = await fetch("/api/director", {
    method: "POST",
    headers: { "content-type": "application/json" },
    signal: opts.signal,
    body: JSON.stringify({ system, prompt, json: opts.json ?? true }),
  });
  if (!res.ok) await fail(res);
  const data = (await res.json()) as { text: string };
  return data.text;
}

export async function requestSpeech(
  provider: TTSProviderId,
  config: ProviderConfig,
  voice: string,
  text: string,
  lang: string,
  style: SynthesisStyle,
  signal?: AbortSignal,
): Promise<ArrayBuffer> {
  const res = await fetch("/api/tts", {
    method: "POST",
    headers: { "content-type": "application/json" },
    signal,
    body: JSON.stringify({
      provider,
      apiKey: config.apiKey,
      baseUrl: config.baseUrl,
      model: config.model,
      voice,
      text,
      lang,
      style,
    }),
  });
  if (!res.ok) await fail(res);
  return res.arrayBuffer();
}

export async function requestVoices(provider: TTSProviderId, apiKey: string): Promise<VoiceProfile[]> {
  const res = await fetch("/api/voices", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ provider, apiKey }),
  });
  if (!res.ok) await fail(res);
  const data = (await res.json()) as { voices: VoiceProfile[] };
  return data.voices;
}

export async function extractFile(file: File): Promise<{ text: string; title: string }> {
  const form = new FormData();
  form.append("file", file);
  const res = await fetch("/api/extract", { method: "POST", body: form });
  if (!res.ok) await fail(res);
  return res.json();
}
