import { langPrefix } from "@/lib/engine/lang";
import { getTTS, type TTSProviderId } from "@/lib/providers";
import type { Emotion, SynthesisStyle } from "@/lib/types";
import { clamp } from "@/lib/utils";
import { edgeSynthesize } from "./edge-tts";
import { ttsServerKey, voiceServer } from "./env";
import { ProviderError, readProviderError, toArrayBuffer } from "./errors";

export interface TTSRequest {
  provider: TTSProviderId;
  apiKey?: string;
  baseUrl?: string;
  model?: string;
  voice: string;
  text: string;
  lang?: string;
  style: SynthesisStyle;
}

export interface TTSResult {
  audio: ArrayBuffer;
  mime: string;
}

const ELEVEN_V3_TAG: Record<Emotion, string> = {
  neutral: "",
  happy: "happy",
  sad: "sad",
  angry: "angry",
  fearful: "nervous",
  surprised: "surprised",
  excited: "excited",
  whisper: "whispers",
  calm: "calm",
  serious: "serious",
  sarcastic: "sarcastic",
  tender: "softly",
  shouting: "shouting",
};

const SARVAM_TEMPERATURE: Partial<Record<Emotion, number>> = {
  excited: 0.9,
  happy: 0.8,
  angry: 0.85,
  shouting: 0.9,
  surprised: 0.85,
  sarcastic: 0.8,
  calm: 0.4,
  serious: 0.45,
  whisper: 0.5,
};

const SARVAM_LANGS = ["hi", "bn", "ta", "te", "kn", "ml", "mr", "gu", "pa", "en"];

function sarvamLanguage(lang?: string) {
  const p = (lang ?? "").toLowerCase().split("-")[0];
  if (p === "or" || p === "od") return "od-IN";
  return SARVAM_LANGS.includes(p) ? `${p}-IN` : "en-IN";
}

function pcmToWav(pcm: Buffer, rate: number): ArrayBuffer {
  const header = Buffer.alloc(44);
  header.write("RIFF", 0);
  header.writeUInt32LE(36 + pcm.length, 4);
  header.write("WAVE", 8);
  header.write("fmt ", 12);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);
  header.writeUInt16LE(1, 22);
  header.writeUInt32LE(rate, 24);
  header.writeUInt32LE(rate * 2, 28);
  header.writeUInt16LE(2, 32);
  header.writeUInt16LE(16, 34);
  header.write("data", 36);
  header.writeUInt32LE(pcm.length, 40);
  return toArrayBuffer(Buffer.concat([header, pcm]));
}

export async function synthesize(req: TTSRequest): Promise<TTSResult> {
  const meta = getTTS(req.provider);
  const key = req.apiKey?.trim() || ttsServerKey(req.provider) || "";
  if (meta.needsKey && !key) {
    throw new ProviderError(`${meta.name} needs an API key. Add it in Settings.`, 401);
  }
  const base = (meta.editableBaseUrl && req.baseUrl?.trim() ? req.baseUrl.trim() : meta.defaultBaseUrl).replace(/\/+$/, "");
  const model = req.model?.trim() || meta.defaultModel;
  const { style } = req;
  const signal = AbortSignal.timeout(120_000);

  switch (req.provider) {
    case "edge": {
      const audio = await edgeSynthesize(req.text, {
        voice: req.voice,
        pitchHz: style.pitch * 1.6,
        ratePct: style.rate,
        volumePct: style.volume,
      });
      return { audio: toArrayBuffer(audio), mime: "audio/mpeg" };
    }

    case "nigix": {
      const server = voiceServer();
      if (!server.url) throw new ProviderError("The built-in voices are not set up on this server.", 503);
      const body = {
        input: req.text,
        voice: req.voice,
        speed: clamp(1 + style.rate / 100, 0.5, 2),
        response_format: "wav",
        ...(langPrefix(req.lang ?? "en") === "hi" ? { language: "hi" } : {}),
      };
      const deadline = Date.now() + 100_000;
      for (;;) {
        try {
          const res = await fetch(`${server.url}/v1/audio/speech`, {
            method: "POST",
            signal,
            headers: {
              "content-type": "application/json",
              ...(server.token ? { Authorization: `Bearer ${server.token}` } : {}),
            },
            body: JSON.stringify(body),
          });
          if (res.ok) return { audio: await res.arrayBuffer(), mime: res.headers.get("content-type") || "audio/wav" };
          if (res.status !== 503) throw await readProviderError(res, meta.name);
        } catch (err) {
          if (err instanceof ProviderError || signal.aborted || Date.now() > deadline) throw err;
        }
        if (Date.now() > deadline) {
          throw new ProviderError("The voices are still waking up. Give it a moment and try again.", 503);
        }
        await new Promise((r) => setTimeout(r, 4000));
      }
    }

    case "openai-tts":
    case "custom-tts": {
      const instructable = model.startsWith("gpt-4o");
      const body: Record<string, unknown> = { model, voice: req.voice, input: req.text, response_format: "mp3" };
      if (instructable && style.instructions) body.instructions = style.instructions;
      if (!instructable) body.speed = clamp(1 + style.rate / 100, 0.5, 2);
      let res: Response;
      try {
        res = await fetch(`${base}/audio/speech`, {
          method: "POST",
          signal,
          headers: { "content-type": "application/json", ...(key ? { Authorization: `Bearer ${key}` } : {}) },
          body: JSON.stringify(body),
        });
      } catch (err) {
        if (req.provider !== "openai-tts") {
          throw new ProviderError(`Can't reach ${meta.name} at ${base}. Is the server running?`, 502);
        }
        throw err;
      }
      if (!res.ok) throw await readProviderError(res, meta.name);
      return { audio: await res.arrayBuffer(), mime: res.headers.get("content-type") || "audio/mpeg" };
    }

    case "elevenlabs": {
      const v3 = model.includes("v3");
      const tag = ELEVEN_V3_TAG[style.emotion];
      const expressive = !["neutral", "calm", "serious"].includes(style.emotion);
      const text = v3 && tag ? `[${tag}] ${req.text}` : req.text;
      const voice_settings = v3
        ? { stability: 0.5, similarity_boost: 0.75 }
        : {
            stability: expressive ? 0.32 : 0.5,
            similarity_boost: 0.78,
            style: expressive ? 0.45 : 0.15,
            use_speaker_boost: true,
            speed: clamp(1 + style.rate / 100, 0.7, 1.2),
          };
      const res = await fetch(
        `${base}/text-to-speech/${encodeURIComponent(req.voice)}?output_format=mp3_44100_128`,
        {
          method: "POST",
          signal,
          headers: { "content-type": "application/json", "xi-api-key": key, accept: "audio/mpeg" },
          body: JSON.stringify({ text, model_id: model, voice_settings }),
        },
      );
      if (!res.ok) throw await readProviderError(res, meta.name);
      return { audio: await res.arrayBuffer(), mime: "audio/mpeg" };
    }

    case "sarvam-tts": {
      const v2 = model === "bulbul:v2";
      const body: Record<string, unknown> = {
        text: req.text,
        language_code: sarvamLanguage(req.lang),
        speaker: req.voice,
        model,
        pace: clamp(1 + style.rate / 100, v2 ? 0.3 : 0.5, v2 ? 3 : 2),
        speech_sample_rate: 24000,
      };
      if (v2) {
        body.pitch = clamp(style.pitch / 40, -0.75, 0.75);
        body.loudness = clamp(1 + style.volume / 100, 0.3, 3);
        body.enable_preprocessing = true;
      } else {
        body.temperature = SARVAM_TEMPERATURE[style.emotion] ?? 0.6;
      }
      const res = await fetch(`${base}/text-to-speech`, {
        method: "POST",
        signal,
        headers: { "content-type": "application/json", "api-subscription-key": key },
        body: JSON.stringify(body),
      });
      if (!res.ok) throw await readProviderError(res, meta.name);
      const data = (await res.json()) as { audios?: string[] };
      const b64 = data.audios?.[0];
      if (!b64) throw new ProviderError("Sarvam returned no audio", 502);
      return { audio: toArrayBuffer(Buffer.from(b64, "base64")), mime: "audio/wav" };
    }

    case "gemini-tts": {
      const prompt = style.instructions ? `Read aloud with this direction: ${style.instructions}\n\n${req.text}` : req.text;
      const res = await fetch(`${base}/models/${encodeURIComponent(model)}:generateContent`, {
        method: "POST",
        signal,
        headers: { "content-type": "application/json", "x-goog-api-key": key },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: {
            responseModalities: ["AUDIO"],
            speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: req.voice } } },
          },
        }),
      });
      if (!res.ok) throw await readProviderError(res, meta.name);
      const data = (await res.json()) as {
        candidates?: { content?: { parts?: { inlineData?: { mimeType?: string; data?: string } }[] } }[];
      };
      const inline = data.candidates?.[0]?.content?.parts?.find((p) => p.inlineData?.data)?.inlineData;
      if (!inline?.data) throw new ProviderError("Gemini returned no audio", 502);
      const rate = Number(inline.mimeType?.match(/rate=(\d+)/)?.[1] ?? 24000);
      return { audio: pcmToWav(Buffer.from(inline.data, "base64"), rate), mime: "audio/wav" };
    }
  }
}
