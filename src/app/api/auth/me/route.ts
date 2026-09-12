import { pageUser } from "@/lib/server/auth";

export const dynamic = "force-dynamic";

export async function GET() {
  return Response.json({ user: await pageUser() }, { headers: { "Cache-Control": "no-store" } });
}
