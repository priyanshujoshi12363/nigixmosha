import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const RATE = 24000;
const work = path.join(os.tmpdir(), "nigix-mixcheck");
fs.mkdirSync(work, { recursive: true });

const LEVEL = {
  subtle: { bed: -25, shot: -12, duck: 0.45 },
  cinematic: { bed: -19, shot: -7, duck: 0.38 },
};
const BED_RMS = 0.1;
const SHOT_PEAK = 0.8;
const SCENE_LEAD = 0.6;
const SCENE_TAIL = 1.2;
const FADE = 1.1;
const LOOP_FADE = 0.9;

const db = (v) => Math.pow(10, v / 20);
const secs = (n) => Math.round(n * RATE);

const catalog = JSON.parse(fs.readFileSync(path.join(root, "src/lib/sounds/catalog.json"), "utf8"));
const byTag = new Map(catalog.tags.map((t) => [t.tag, t]));

function readWav(file) {
  const buf = fs.readFileSync(file);
  const out = new Float32Array((buf.length - 44) / 2);
  for (let i = 0; i < out.length; i++) out[i] = buf.readInt16LE(44 + i * 2) / 32768;
  return out;
}

function writeWav(file, samples) {
  const buf = Buffer.alloc(44 + samples.length * 2);
  buf.write("RIFF", 0);
  buf.writeUInt32LE(36 + samples.length * 2, 4);
  buf.write("WAVE", 8);
  buf.write("fmt ", 12);
  buf.writeUInt32LE(16, 16);
  buf.writeUInt16LE(1, 20);
  buf.writeUInt16LE(1, 22);
  buf.writeUInt32LE(RATE, 24);
  buf.writeUInt32LE(RATE * 2, 28);
  buf.writeUInt16LE(2, 32);
  buf.writeUInt16LE(16, 34);
  buf.write("data", 36);
  buf.writeUInt32LE(samples.length * 2, 40);
  for (let i = 0; i < samples.length; i++) {
    const v = Math.max(-1, Math.min(1, samples[i]));
    buf.writeInt16LE(Math.round(v * 32767), 44 + i * 2);
  }
  fs.writeFileSync(file, buf);
}

function fetchClip(url, id) {
  const raw = path.join(work, `${id}.pcm`);
  if (!fs.existsSync(raw)) {
    const res = spawnSync("ffmpeg", ["-v", "error", "-i", url, "-ac", "1", "-ar", String(RATE), "-f", "s16le", "-y", raw]);
    if (res.status !== 0) throw new Error(`decode failed for ${url}`);
  }
  const buf = fs.readFileSync(raw);
  const out = new Float32Array(buf.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = buf.readInt16LE(i * 2) / 32768;
  return out;
}

function levelClip(samples, kind) {
  let sum = 0;
  let peak = 0;
  for (const v of samples) {
    sum += v * v;
    const a = Math.abs(v);
    if (a > peak) peak = a;
  }
  if (!samples.length || !peak) return samples;
  const rms = Math.sqrt(sum / samples.length);
  const gain = Math.max(0.05, Math.min(24, kind === "bed" ? BED_RMS / rms : SHOT_PEAK / peak));
  return samples.map((v) => v * gain);
}

function loopSample(clip, local, fade) {
  const step = clip.length - fade;
  if (step <= 0) return 0;
  const iteration = Math.floor(local / step);
  const pos = local - iteration * step;
  let value = clip[pos] * (iteration > 0 && pos < fade ? pos / fade : 1);
  const tail = pos + step;
  if (pos < fade && tail < clip.length) value += clip[tail] * ((fade - pos) / fade);
  return value;
}

function duckEnvelope(voice, depth) {
  const window = secs(0.03);
  const bins = Math.ceil(voice.length / window);
  const level = new Float32Array(bins);
  for (let b = 0; b < bins; b++) {
    let peak = 0;
    const from = b * window;
    const to = Math.min(voice.length, from + window);
    for (let i = from; i < to; i += 3) {
      const a = Math.abs(voice[i]);
      if (a > peak) peak = a;
    }
    level[b] = Math.min(1, peak / 0.12);
  }
  let smooth = 0;
  const env = new Float32Array(bins);
  for (let b = 0; b < bins; b++) {
    smooth += (level[b] - smooth) * (level[b] > smooth ? 0.55 : 0.12);
    env[b] = 1 - depth * Math.min(1, smooth);
  }
  const out = new Float32Array(voice.length);
  for (let i = 0; i < voice.length; i++) out[i] = env[Math.min(bins - 1, Math.floor(i / window))];
  return out;
}

const [voiceFile, planFile, levelName = "cinematic", outFile = "mixed.wav"] = process.argv.slice(2);
const voice = readWav(voiceFile);
const { plan, timeline } = JSON.parse(fs.readFileSync(planFile, "utf8"));
const settings = LEVEL[levelName];
const at = (i) => timeline[Math.max(0, Math.min(timeline.length - 1, i))];

const bed = new Float32Array(voice.length);
const shot = new Float32Array(voice.length);
let variant = 0;

for (const scene of plan.scenes) {
  const meta = byTag.get(scene.tag);
  const v = meta.variants[variant++ % meta.variants.length];
  const clip = levelClip(fetchClip(v.url, v.id), "bed");
  const start = Math.max(0, secs(at(scene.from).start - SCENE_LEAD));
  const end = Math.min(voice.length, secs(at(scene.to).end + SCENE_TAIL));
  const fade = Math.min(secs(LOOP_FADE), Math.floor(clip.length / 3));
  const fadeEdge = Math.min(secs(FADE), Math.floor((end - start) / 2));
  const gain = db(settings.bed + meta.gain + 22) * (0.45 + 0.55 * scene.intensity);
  for (let a = start; a < end; a++) {
    const local = a - start;
    let g = gain;
    const left = end - a;
    if (local < fadeEdge) g *= local / fadeEdge;
    if (left < fadeEdge) g *= left / fadeEdge;
    bed[a] += loopSample(clip, local, fade) * g;
  }
  console.log(`bed ${scene.tag} lines ${scene.from}-${scene.to} → ${(start / RATE).toFixed(1)}s..${(end / RATE).toFixed(1)}s`);
}

for (const cue of plan.cues) {
  const meta = byTag.get(cue.tag);
  const v = meta.variants[variant++ % meta.variants.length];
  const clip = levelClip(fetchClip(v.url, v.id), "shot");
  const line = at(cue.at);
  const where =
    cue.place === "before"
      ? secs(line.start - 0.18) - clip.length
      : cue.place === "after"
        ? secs(line.end + 0.12)
        : secs(line.start + Math.min(0.5, (line.end - line.start) * 0.3));
  const start = Math.max(0, where);
  const gain = db(settings.shot + meta.gain + 8) * cue.gain;
  for (let i = 0; i < Math.min(clip.length, shot.length - start); i++) shot[start + i] += clip[i] * gain;
  console.log(`cue ${cue.tag} (${cue.place}) at line ${cue.at} → ${(start / RATE).toFixed(2)}s`);
}

const duck = duckEnvelope(voice, settings.duck);
const out = new Float32Array(voice.length);
for (let i = 0; i < voice.length; i++) {
  out[i] = Math.max(-0.97, Math.min(0.97, voice[i] + bed[i] * duck[i] + shot[i] * (0.5 + 0.5 * duck[i])));
}
writeWav(outFile, out);

const rms = (arr, from, dur) => {
  let sum = 0;
  const a = secs(from);
  const b = Math.min(arr.length, a + secs(dur));
  for (let i = a; i < b; i++) sum += arr[i] * arr[i];
  return Math.sqrt(sum / Math.max(1, b - a)) * 32768;
};
console.log(`\nvoice speech rms ${rms(voice, 5, 1).toFixed(0)}`);
console.log(`bed in a gap     ${rms(out, 13.9, 0.35).toFixed(0)} (was 28)`);
console.log(`bed under speech ${(rms(out, 5, 1) - rms(voice, 5, 1)).toFixed(0)} added`);
console.log(`wrote ${outFile}`);
