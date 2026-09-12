import { cookies } from "next/headers";
import { createHash, randomBytes, scrypt, timingSafeEqual } from "node:crypto";
import { ObjectId } from "mongodb";
import type { AccountUser } from "@/lib/library-types";
import { getDb } from "./db";
import { ProviderError } from "./errors";

export const SESSION_COOKIE = "nigix_session";
const SESSION_DAYS = 30;
const SCRYPT = { N: 16384, r: 8, p: 1, keylen: 64 };
const USERNAME_RE = /^[a-z0-9_]{3,24}$/;
const ANON_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export interface UserDoc {
  username: string;
  displayName: string;
  passwordHash: string;
  createdAt: Date;
  passwordChangedAt?: Date;
}

export interface SessionDoc {
  _id: string;
  userId: ObjectId;
  createdAt: Date;
  expiresAt: Date;
  userAgent: string;
}

export const users = async () => (await getDb()).collection<UserDoc>("users");
export const sessions = async () => (await getDb()).collection<SessionDoc>("sessions");

export function normalizeUsername(v: unknown) {
  return typeof v === "string" ? v.trim().toLowerCase() : "";
}

export function assertUsername(username: string) {
  if (!USERNAME_RE.test(username)) {
    throw new ProviderError("Username must be 3–24 characters: letters, numbers or underscore.", 400);
  }
}

export function assertPassword(password: unknown): string {
  if (typeof password !== "string" || password.length < 8) {
    throw new ProviderError("Password must be at least 8 characters.", 400);
  }
  if (password.length > 128) throw new ProviderError("Password is too long.", 400);
  return password;
}

function derive(password: string, salt: Buffer, n = SCRYPT.N): Promise<Buffer> {
  return new Promise((resolve, reject) =>
    scrypt(
      password.normalize("NFKC"),
      salt,
      SCRYPT.keylen,
      { N: n, r: SCRYPT.r, p: SCRYPT.p, maxmem: 64 * 1024 * 1024 },
      (err, key) => (err ? reject(err) : resolve(key)),
    ),
  );
}

export async function hashPassword(password: string) {
  const salt = randomBytes(16);
  const key = await derive(password, salt);
  return `scrypt$${SCRYPT.N}$${SCRYPT.r}$${SCRYPT.p}$${salt.toString("base64")}$${key.toString("base64")}`;
}

export async function verifyPassword(password: string, stored: string) {
  const [alg, n, , , salt, hash] = stored.split("$");
  if (alg !== "scrypt" || !salt || !hash) return false;
  const key = await derive(password, Buffer.from(salt, "base64"), Number(n) || SCRYPT.N);
  const expected = Buffer.from(hash, "base64");
  return key.length === expected.length && timingSafeEqual(key, expected);
}

let dummy: Promise<string> | null = null;
export const dummyHash = () => (dummy ??= hashPassword(randomBytes(12).toString("hex")));

const hashToken = (token: string) => createHash("sha256").update(token).digest("base64url");

export function toAccount(id: ObjectId, u: Pick<UserDoc, "username" | "displayName" | "createdAt">): AccountUser {
  return { id: id.toHexString(), username: u.username, displayName: u.displayName, createdAt: u.createdAt.toISOString() };
}

export async function createSession(userId: ObjectId, request: Request) {
  const token = randomBytes(32).toString("base64url");
  const now = new Date();
  const expiresAt = new Date(now.getTime() + SESSION_DAYS * 86_400_000);
  await (await sessions()).insertOne({
    _id: hashToken(token),
    userId,
    createdAt: now,
    expiresAt,
    userAgent: (request.headers.get("user-agent") ?? "").slice(0, 200),
  });
  (await cookies()).set(SESSION_COOKIE, token, {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    expires: expiresAt,
  });
}

async function readToken() {
  const token = (await cookies()).get(SESSION_COOKIE)?.value;
  return token && /^[A-Za-z0-9_-]{40,64}$/.test(token) ? token : null;
}

export async function currentUser(): Promise<AccountUser | null> {
  const token = await readToken();
  if (!token) return null;
  const session = await (await sessions()).findOne({ _id: hashToken(token), expiresAt: { $gt: new Date() } });
  if (!session) return null;
  const user = await (await users()).findOne({ _id: session.userId }, { projection: { passwordHash: 0 } });
  return user ? toAccount(user._id, user) : null;
}

export async function pageUser(): Promise<AccountUser | null> {
  try {
    return await currentUser();
  } catch {
    return null;
  }
}

export async function requireUser(): Promise<AccountUser> {
  const user = await currentUser();
  if (!user) throw new ProviderError("Please sign in to continue.", 401, "unauthenticated");
  return user;
}

export async function endSession(allForUser?: ObjectId) {
  const token = await readToken();
  const col = await sessions();
  if (allForUser) await col.deleteMany({ userId: allForUser });
  else if (token) await col.deleteOne({ _id: hashToken(token) });
  (await cookies()).delete(SESSION_COOKIE);
}

export async function endOtherSessions(userId: ObjectId) {
  const token = await readToken();
  await (await sessions()).deleteMany({ userId, ...(token ? { _id: { $ne: hashToken(token) } } : {}) });
}

export async function claimAnonymousProjects(anonymousId: unknown, userId: string) {
  if (typeof anonymousId !== "string" || !ANON_RE.test(anonymousId)) return;
  await (await getDb())
    .collection("projects")
    .updateMany({ ownerId: anonymousId.toLowerCase() }, { $set: { ownerId: userId } });
}

const attempts = new Map<string, { count: number; reset: number }>();

export function rateLimit(key: string, limit: number, windowMs: number) {
  const now = Date.now();
  const entry = attempts.get(key);
  if (!entry || entry.reset < now) {
    attempts.set(key, { count: 1, reset: now + windowMs });
    return;
  }
  entry.count++;
  if (entry.count > limit) {
    const minutes = Math.max(1, Math.ceil((entry.reset - now) / 60_000));
    throw new ProviderError(`Too many attempts. Try again in ${minutes} min.`, 429);
  }
}

export const clearRateLimit = (key: string) => attempts.delete(key);

export function clientIp(request: Request) {
  return request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || request.headers.get("x-real-ip") || "local";
}
