import { requireUser } from "@/lib/server/auth";
import { edgeVoices } from "@/lib/server/edge-tts";
import { ttsServerKey } from "@/lib/server/env";
import { readProviderError, toErrorResponse } from "@/lib/server/errors";
import type { AgeGroup, Gender, VoiceProfile } from "@/lib/types";

interface ElevenVoice {
  voice_id: string;
  name: string;
  category?: string;
  labels?: Record<string, string>;
  description?: string | null;
}

function elevenAge(label?: string): AgeGroup | undefined {
  const l = (label ?? "").toLowerCase();
  if (l.includes("young")) return "young";
  if (l.includes("middle")) return "middle";
  if (l.includes("old")) return "elderly";
  return undefined;
}

export async function POST(request: Request) {
  try {
    await requireUser();
    const body = (await request.json()) as { provider: string; apiKey?: string };
    const provider = body.provider;
    const apiKey = body.apiKey?.trim() || ttsServerKey(provider);

    if (provider === "edge") {
      return Response.json({ voices: await edgeVoices() });
    }

    if (provider === "elevenlabs") {
      if (!apiKey) return Response.json({ error: "Add your ElevenLabs key first" }, { status: 401 });
      const res = await fetch("https://api.elevenlabs.io/v1/voices", {
        headers: { "xi-api-key": apiKey },
        signal: AbortSignal.timeout(15_000),
      });
      if (!res.ok) throw await readProviderError(res, "ElevenLabs");
      const data = (await res.json()) as { voices?: ElevenVoice[] };
      const voices = (data.voices ?? []).map<VoiceProfile>((vo) => {
        const labels = vo.labels ?? {};
        const g = (labels.gender ?? "").toLowerCase();
        const gender: Gender = g === "female" ? "female" : g === "male" ? "male" : "neutral";
        const tags = [labels.description, labels.descriptive, labels.use_case, labels.accent, vo.category]
          .filter(Boolean)
          .flatMap((t) => String(t).toLowerCase().split(/[\s,_-]+/));
        return {
          id: vo.voice_id,
          name: vo.name,
          gender,
          age: elevenAge(labels.age),
          langs: ["*"],
          tags,
          multilingual: true,
        };
      });
      return Response.json({ voices });
    }

    return Response.json({ error: "This engine has a built-in voice list" }, { status: 400 });
  } catch (err) {
    return toErrorResponse(err);
  }
}
