import { createHash } from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const here = path.join(root, "scripts", "sounds");
const work = path.join(os.tmpdir(), "nigix-sound-pack");
const statePath = path.join(here, "uploaded.json");
const catalogPath = path.join(here, "catalog.json");

const BED = { window: 32, target: "-20", gain: -22 };
const SHOT = { max: 8, target: "-16", gain: -8 };
const WORKERS = 3;

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

const cloud = envValue("CLOUDINARY_CLOUD_NAME");
const apiKey = envValue("CLOUDINARY_API_KEY");
const apiSecret = envValue("CLOUDINARY_API_SECRET");
if (!cloud || !apiKey || !apiSecret) {
  console.error("Cloudinary credentials missing from .env.local");
  process.exit(1);
}

fs.mkdirSync(work, { recursive: true });
const state = fs.existsSync(statePath) ? JSON.parse(fs.readFileSync(statePath, "utf8")) : {};

function run(cmd, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(cmd, args, { windowsHide: true });
    let err = "";
    child.stderr.on("data", (d) => (err += d.toString()));
    child.on("error", reject);
    child.on("close", (code) => (code === 0 ? resolve(err) : reject(new Error(err.slice(-400)))));
  });
}

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

async function retry(label, fn, attempts = 4) {
  let last;
  for (let i = 0; i < attempts; i++) {
    try {
      return await fn();
    } catch (err) {
      last = err;
      await wait(600 * (i + 1) * (i + 1) + Math.random() * 400);
    }
  }
  throw new Error(`${label}: ${last?.message ?? last}`);
}

async function download(url, file) {
  await retry("download", async () => {
    const res = await fetch(url, { headers: { "user-agent": "nigixmosha-soundpack/1.0" } });
    if (!res.ok) throw new Error(`status ${res.status}`);
    const buf = Buffer.from(await res.arrayBuffer());
    if (buf.length < 2048) throw new Error(`short body ${buf.length}`);
    fs.writeFileSync(file, buf);
  });
}

async function process_(src, out, kind, duration) {
  if (kind === "bed") {
    const start = duration > BED.window + 6 ? Math.min(duration * 0.15, 20) : 0;
    const len = Math.min(BED.window, Math.max(4, duration - start));
    await run("ffmpeg", [
      "-y", "-v", "error", "-ss", String(start.toFixed(2)), "-t", String(len.toFixed(2)), "-i", src,
      "-af", `loudnorm=I=${BED.target}:TP=-1.5:LRA=11,afade=t=in:st=0:d=0.4,afade=t=out:st=${(len - 0.4).toFixed(2)}:d=0.4`,
      "-ar", "44100", "-c:a", "libmp3lame", "-b:a", "128k", out,
    ]);
  } else {
    const trim =
      "silenceremove=start_periods=1:start_duration=0.02:start_threshold=-50dB:detection=peak," +
      "areverse,silenceremove=start_periods=1:start_duration=0.02:start_threshold=-50dB:detection=peak,areverse";
    await run("ffmpeg", [
      "-y", "-v", "error", "-t", String(SHOT.max), "-i", src,
      "-af", `${trim},loudnorm=I=${SHOT.target}:TP=-1.0:LRA=11`,
      "-ar", "44100", "-c:a", "libmp3lame", "-b:a", "128k", out,
    ]);
  }
}

async function probe(file) {
  const out = await new Promise((resolve, reject) => {
    const child = spawn("ffprobe", ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", file], { windowsHide: true });
    let text = "";
    child.stdout.on("data", (d) => (text += d.toString()));
    child.on("error", reject);
    child.on("close", () => resolve(text.trim()));
  });
  return Number(out) || 0;
}

async function upload(file, publicId) {
  const timestamp = Math.floor(Date.now() / 1000);
  const params = { invalidate: "true", overwrite: "true", public_id: publicId, timestamp };
  const payload = Object.keys(params)
    .sort()
    .map((k) => `${k}=${params[k]}`)
    .join("&");
  const signature = createHash("sha1").update(payload + apiSecret).digest("hex");
  const form = new FormData();
  form.append("file", new Blob([fs.readFileSync(file)]), path.basename(file));
  form.append("api_key", apiKey);
  form.append("timestamp", String(timestamp));
  form.append("public_id", publicId);
  form.append("overwrite", "true");
  form.append("invalidate", "true");
  form.append("signature", signature);
  return retry("upload", async () => {
    const res = await fetch(`https://api.cloudinary.com/v1_1/${cloud}/video/upload`, { method: "POST", body: form });
    const data = await res.json();
    if (!res.ok) throw new Error(data?.error?.message ?? `status ${res.status}`);
    return data;
  }, 3);
}

const tags = JSON.parse(fs.readFileSync(path.join(here, "candidates.json"), "utf8"));
const jobs = [];
for (const tag of tags) {
  tag.candidates.forEach((cand, rank) => jobs.push({ tag, cand, rank }));
}

let done = 0;
let failed = 0;
const total = jobs.length;

async function handle({ tag, cand, rank }) {
  const key = `${tag.tag}/${cand.id}`;
  if (state[key]) return;
  if (!cand.preview) throw new Error("no preview");
  const src = path.join(work, `${cand.id}.src.mp3`);
  const out = path.join(work, `${cand.id}.out.mp3`);
  await download(cand.preview, src);
  await process_(src, out, tag.kind, cand.duration);
  const seconds = await probe(out);
  const uploaded = await upload(out, `nigixmosha/sounds/${tag.tag}/${cand.id}`);
  state[key] = {
    tag: tag.tag,
    kind: tag.kind,
    rank,
    id: cand.id,
    name: cand.name,
    user: cand.user,
    page: cand.page,
    license: "CC0",
    rating: cand.rating,
    ratings: cand.ratings,
    downloads: cand.downloads,
    channels: cand.channels,
    seconds: Math.round(seconds * 100) / 100,
    bytes: fs.statSync(out).size,
    url: uploaded.secure_url,
    publicId: uploaded.public_id,
  };
  fs.rmSync(src, { force: true });
  fs.rmSync(out, { force: true });
}

async function worker(queue) {
  while (queue.length) {
    const job = queue.shift();
    try {
      await handle(job);
    } catch (err) {
      failed++;
      console.log(`\n!! ${job.tag.tag}/${job.cand.id}: ${err.message}`);
    }
    done++;
    if (done % 5 === 0 || done === total) {
      fs.writeFileSync(statePath, JSON.stringify(state, null, 2) + "\n");
      process.stdout.write(`\r${done}/${total} processed, ${failed} failed   `);
    }
  }
}

const queue = [...jobs];
await Promise.all(Array.from({ length: WORKERS }, () => worker(queue)));
fs.writeFileSync(statePath, JSON.stringify(state, null, 2) + "\n");

const catalog = {
  version: 1,
  generatedAt: new Date().toISOString().slice(0, 10),
  source: "freesound.org (CC0)",
  tags: tags.map((tag) => ({
    tag: tag.tag,
    kind: tag.kind,
    label: tag.label,
    gain: tag.kind === "bed" ? BED.gain : SHOT.gain,
    variants: tag.candidates
      .map((cand) => state[`${tag.tag}/${cand.id}`])
      .filter(Boolean)
      .sort((a, b) => a.rank - b.rank)
      .map((v) => ({
        id: v.id,
        url: v.url,
        seconds: v.seconds,
        bytes: v.bytes,
        channels: v.channels,
        name: v.name,
        user: v.user,
        page: v.page,
        license: v.license,
      })),
  })),
};
fs.writeFileSync(catalogPath, JSON.stringify(catalog, null, 2) + "\n");

const variants = catalog.tags.reduce((n, t) => n + t.variants.length, 0);
const bytes = Object.values(state).reduce((n, v) => n + (v.bytes ?? 0), 0);
const missing = catalog.tags.filter((t) => !t.variants.length).map((t) => t.tag);
console.log(`\n\ncatalog: ${catalog.tags.length} tags, ${variants} variants, ${(bytes / 1024 / 1024).toFixed(1)} MB`);
if (missing.length) console.log(`empty tags: ${missing.join(", ")}`);
