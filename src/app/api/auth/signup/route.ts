import {
  assertPassword,
  assertUsername,
  claimAnonymousProjects,
  clientIp,
  createSession,
  hashPassword,
  normalizeUsername,
  rateLimit,
  toAccount,
  users,
} from "@/lib/server/auth";
import { ProviderError, toErrorResponse } from "@/lib/server/errors";
import { readBody } from "@/lib/server/projects";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  try {
    rateLimit(`signup:${clientIp(request)}`, 10, 60 * 60_000);
    const body = await readBody<{ username?: string; password?: string; displayName?: string; anonymousId?: string }>(request);
    const username = normalizeUsername(body.username);
    assertUsername(username);
    const password = assertPassword(body.password);
    const displayName = (typeof body.displayName === "string" ? body.displayName.trim() : "").slice(0, 40) || username;
    const col = await users();
    if (await col.findOne({ username }, { projection: { _id: 1 } })) {
      throw new ProviderError("That username is taken. Try another one.", 409);
    }
    const doc = { username, displayName, passwordHash: await hashPassword(password), createdAt: new Date() };
    let insertedId;
    try {
      insertedId = (await col.insertOne(doc)).insertedId;
    } catch (err) {
      if ((err as { code?: number }).code === 11000) throw new ProviderError("That username is taken. Try another one.", 409);
      throw err;
    }
    await createSession(insertedId, request);
    await claimAnonymousProjects(body.anonymousId, insertedId.toHexString());
    return Response.json({ user: toAccount(insertedId, doc) }, { status: 201 });
  } catch (err) {
    return toErrorResponse(err);
  }
}
