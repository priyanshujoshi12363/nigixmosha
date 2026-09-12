import { requireUser } from "@/lib/server/auth";
import { voiceServer } from "@/lib/server/env";
import { toErrorResponse } from "@/lib/server/errors";

export const dynamic = "force-dynamic";

export async function GET() {
  try {
    await requireUser();
    const { url, token } = voiceServer();
    if (!url) return Response.json({ state: "off" });
    try {
      const res = await fetch(`${url}/health`, {
        cache: "no-store",
        signal: AbortSignal.timeout(8000),
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      const data = res.ok ? ((await res.json()) as { status?: string }) : null;
      return Response.json({ state: data?.status === "ok" ? "ready" : "waking" });
    } catch {
      return Response.json({ state: "waking" });
    }
  } catch (err) {
    return toErrorResponse(err);
  }
}
