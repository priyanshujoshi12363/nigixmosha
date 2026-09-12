import { ObjectId } from "mongodb";
import {
  assertPassword,
  endOtherSessions,
  hashPassword,
  rateLimit,
  requireUser,
  users,
  verifyPassword,
} from "@/lib/server/auth";
import { ProviderError, toErrorResponse } from "@/lib/server/errors";
import { readBody } from "@/lib/server/projects";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  try {
    const me = await requireUser();
    rateLimit(`password:${me.id}`, 8, 10 * 60_000);
    const body = await readBody<{ current?: string; next?: string }>(request);
    const next = assertPassword(body.next);
    const _id = new ObjectId(me.id);
    const col = await users();
    const user = await col.findOne({ _id });
    if (!user || !(await verifyPassword(typeof body.current === "string" ? body.current : "", user.passwordHash))) {
      throw new ProviderError("Your current password is incorrect.", 400);
    }
    await col.updateOne({ _id }, { $set: { passwordHash: await hashPassword(next), passwordChangedAt: new Date() } });
    await endOtherSessions(_id);
    return Response.json({ ok: true });
  } catch (err) {
    return toErrorResponse(err);
  }
}
