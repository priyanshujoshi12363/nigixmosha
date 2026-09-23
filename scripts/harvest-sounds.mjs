import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const here = path.join(root, "scripts", "sounds");
const API = "https://freesound.org/apiv2/search/text/";
const PER_TAG = 5;
const FIELDS = "id,name,username,license,duration,filesize,samplerate,channels,avg_rating,num_ratings,num_downloads,previews,tags,description,url";
const WINDOW = { bed: "[15 TO 240]", shot: "[0.2 TO 8]" };

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

const token = envValue("FREESOUND_API_KEY");
if (!token) {
  console.error("FREESOUND_API_KEY missing from .env.local");
  process.exit(1);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function search(tag, sort) {
  const url = new URL(API);
  url.searchParams.set("query", tag.query);
  url.searchParams.set("filter", `license:"Creative Commons 0" duration:${WINDOW[tag.kind]}`);
  url.searchParams.set("fields", FIELDS);
  url.searchParams.set("sort", sort);
  url.searchParams.set("page_size", String(PER_TAG * 2));
  const res = await fetch(url, { headers: { Authorization: `Token ${token}` } });
  if (res.status === 429) {
    await sleep(60_000);
    return search(tag, sort);
  }
  if (!res.ok) throw new Error(`${tag.id}: ${res.status} ${await res.text()}`);
  const data = await res.json();
  return data.results ?? [];
}

function score(sound) {
  const rating = sound.num_ratings >= 3 ? sound.avg_rating : 2.5;
  return rating * 2 + Math.log10((sound.num_downloads ?? 0) + 1);
}

const tags = JSON.parse(fs.readFileSync(path.join(here, "tags.json"), "utf8"));
const out = [];
let empty = 0;

for (const tag of tags) {
  const seen = new Map();
  for (const sort of ["rating_desc", "downloads_desc"]) {
    for (const sound of await search(tag, sort)) seen.set(sound.id, sound);
    await sleep(250);
  }
  const picks = [...seen.values()].sort((a, b) => score(b) - score(a)).slice(0, PER_TAG);
  if (!picks.length) empty++;
  out.push({
    tag: tag.id,
    kind: tag.kind,
    label: tag.label,
    query: tag.query,
    candidates: picks.map((s) => ({
      id: s.id,
      name: s.name,
      user: s.username,
      duration: Math.round(s.duration * 10) / 10,
      rating: Math.round((s.avg_rating ?? 0) * 10) / 10,
      ratings: s.num_ratings ?? 0,
      downloads: s.num_downloads ?? 0,
      channels: s.channels,
      samplerate: s.samplerate,
      preview: s.previews?.["preview-hq-mp3"] ?? s.previews?.["preview-lq-mp3"],
      page: s.url,
      description: (s.description ?? "").replace(/\s+/g, " ").slice(0, 200),
    })),
  });
  console.log(`${picks.length ? "ok " : "!! "} ${tag.id.padEnd(18)} ${picks.length} candidates`);
}

fs.writeFileSync(path.join(here, "candidates.json"), JSON.stringify(out, null, 2) + "\n");
console.log(`\n${out.length} tags, ${out.reduce((n, t) => n + t.candidates.length, 0)} candidates, ${empty} empty`);
