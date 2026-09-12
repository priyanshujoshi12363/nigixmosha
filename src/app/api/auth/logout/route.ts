import { ObjectId } from "mongodb";
import { currentUser, endSession } from "@/lib/server/auth";
import { toErrorResponse } from "@/lib/server/errors";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  try {
    const body = (await request.json().catch(() => ({}))) as { all?: boolean };
    const user = body.all ? await currentUser() : null;
    await endSession(user ? new ObjectId(user.id) : undefined);
    return Response.json({ ok: true });
  } catch (err) {
    return toErrorResponse(err);
  }
}
