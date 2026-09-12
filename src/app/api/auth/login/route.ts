import {
  claimAnonymousProjects,
  clearRateLimit,
  clientIp,
  createSession,
  dummyHash,
  normalizeUsername,
  rateLimit,
  toAccount,
  users,
  verifyPassword,
} from "@/lib/server/auth";
import { ProviderError, toErrorResponse } from "@/lib/server/errors";
import { readBody } from "@/lib/server/projects";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  try {
    const body = await readBody<{ username?: string; password?: string; anonymousId?: string }>(request);
    const username = normalizeUsername(body.username);
    const password = typeof body.password === "string" ? body.password : "";
    const limitKey = `login:${clientIp(request)}:${username}`;
    rateLimit(limitKey, 8, 10 * 60_000);
    if (!username || !password) throw new ProviderError("Enter your username and password.", 400);
    const user = await (await users()).findOne({ username });
    const ok = await verifyPassword(password, user?.passwordHash ?? (await dummyHash()));
    if (!user || !ok) throw new ProviderError("Wrong username or password.", 401, "invalid_credentials");
    clearRateLimit(limitKey);
    await createSession(user._id, request);
    await claimAnonymousProjects(body.anonymousId, user._id.toHexString());
    return Response.json({ user: toAccount(user._id, user) });
  } catch (err) {
    return toErrorResponse(err);
  }
}
