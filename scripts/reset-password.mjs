import { randomBytes, scrypt } from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { MongoClient } from "mongodb";

const SCRYPT = { N: 16384, r: 8, p: 1, keylen: 64 };
const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));

function envValue(name) {
  if (process.env[name]) return process.env[name];
  for (const file of [".env.local", ".env"]) {
    const full = path.join(root, file);
    if (!fs.existsSync(full)) continue;
    const line = fs.readFileSync(full, "utf8").match(new RegExp(`^${name}=(.*)$`, "m"));
    if (line) return line[1].trim();
  }
  return undefined;
}

const derive = (password, salt) =>
  new Promise((resolve, reject) =>
    scrypt(
      password.normalize("NFKC"),
      salt,
      SCRYPT.keylen,
      { N: SCRYPT.N, r: SCRYPT.r, p: SCRYPT.p, maxmem: 64 * 1024 * 1024 },
      (err, key) => (err ? reject(err) : resolve(key)),
    ),
  );

async function hashPassword(password) {
  const salt = randomBytes(16);
  const key = await derive(password, salt);
  return `scrypt$${SCRYPT.N}$${SCRYPT.r}$${SCRYPT.p}$${salt.toString("base64")}$${key.toString("base64")}`;
}

const args = process.argv.slice(2);
const list = args.includes("--list");
const userArg = args.indexOf("--user");
const username = userArg !== -1 ? (args[userArg + 1] ?? "").trim().toLowerCase() : "";
const password = process.env.NEW_PASSWORD ?? "";

const uri = envValue("MONGODB_URI");
if (!uri) {
  console.error("MONGODB_URI not found in environment or .env.local");
  process.exit(1);
}

const client = new MongoClient(uri);
await client.connect();
const db = client.db();

if (list || !username) {
  const all = await db
    .collection("users")
    .find({}, { projection: { username: 1, displayName: 1, createdAt: 1 } })
    .sort({ createdAt: 1 })
    .toArray();
  console.log(`${all.length} account(s):`);
  for (const u of all) {
    console.log(`  ${u.username.padEnd(20)} ${u.displayName ?? ""}  created ${u.createdAt?.toISOString?.().slice(0, 10) ?? "?"}`);
  }
  if (!username) console.log("\nRun again with:  node scripts/reset-password.mjs --user <username>");
  await client.close();
  process.exit(0);
}

if (password.length < 8) {
  console.error("Set a new password first, at least 8 characters:");
  console.error('  PowerShell:  $env:NEW_PASSWORD = "your new password"');
  await client.close();
  process.exit(1);
}

const user = await db.collection("users").findOne({ username }, { projection: { _id: 1, username: 1 } });
if (!user) {
  console.error(`No account named "${username}". Run with --list to see the accounts.`);
  await client.close();
  process.exit(1);
}

await db
  .collection("users")
  .updateOne({ _id: user._id }, { $set: { passwordHash: await hashPassword(password), passwordChangedAt: new Date() } });
const removed = await db.collection("sessions").deleteMany({ userId: user._id });

console.log(`Password updated for "${user.username}".`);
console.log(`Signed out ${removed.deletedCount} existing session(s). Sign in with the new password.`);
await client.close();
