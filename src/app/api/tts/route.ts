import { requireUser } from "@/lib/server/auth";
import { synthesize, type TTSRequest } from "@/lib/server/tts";
import { toErrorResponse } from "@/lib/server/errors";

export const maxDuration = 120;

export async function POST(request: Request) {
  try {
    await requireUser();
    const body = (await request.json()) as TTSRequest;
    if (!body.text?.trim()) return Response.json({ error: "Nothing to speak" }, { status: 400 });
    if (!body.voice) return Response.json({ error: "No voice selected" }, { status: 400 });
    const { audio, mime } = await synthesize(body);
    return new Response(audio, { headers: { "Content-Type": mime, "Cache-Control": "no-store" } });
  } catch (err) {
    return toErrorResponse(err);
  }
}
