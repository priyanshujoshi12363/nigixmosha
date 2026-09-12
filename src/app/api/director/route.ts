import { requireUser } from "@/lib/server/auth";
import { callBrain } from "@/lib/server/brain";
import { toErrorResponse } from "@/lib/server/errors";

export const maxDuration = 300;

export async function POST(request: Request) {
  try {
    await requireUser();
    const body = (await request.json()) as { system?: unknown; prompt?: unknown; json?: unknown };
    if (typeof body.system !== "string" || typeof body.prompt !== "string") {
      return Response.json({ error: "Invalid request" }, { status: 400 });
    }
    const text = await callBrain({ system: body.system, prompt: body.prompt, json: body.json !== false }, request.signal);
    return Response.json({ text });
  } catch (err) {
    return toErrorResponse(err);
  }
}
