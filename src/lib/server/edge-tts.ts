import { WebSocket } from "ws";
import { createHash, randomBytes, randomUUID } from "node:crypto";
import type { Gender, VoiceProfile } from "@/lib/types";
import { edgeDisplayName } from "@/lib/voices";
import { ProviderError } from "./errors";

const TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
const CHROMIUM_FULL = "143.0.3650.75";
const CHROMIUM_MAJOR = CHROMIUM_FULL.split(".")[0];
const GEC_VERSION = `1-${CHROMIUM_FULL}`;
const BASE = "speech.platform.bing.com/consumer/speech/synthesize/readaloud";
const WIN_EPOCH = 11644473600;
const UA = `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${CHROMIUM_MAJOR}.0.0.0 Safari/537.36 Edg/${CHROMIUM_MAJOR}.0.0.0`;

let clockSkew = 0;

function secMsGec() {
  const seconds = Math.floor(Date.now() / 1000 + clockSkew) + WIN_EPOCH;
  const ticks = BigInt(seconds - (seconds % 300)) * BigInt(10_000_000);
  return createHash("sha256").update(`${ticks}${TOKEN}`, "ascii").digest("hex").toUpperCase();
}

const muid = () => randomBytes(16).toString("hex").toUpperCase();
const connectionId = () => randomUUID().replace(/-/g, "");

function dateString() {
  const d = new Date();
  const days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
  const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  const p = (n: number) => String(n).padStart(2, "0");
  return `${days[d.getUTCDay()]} ${months[d.getUTCMonth()]} ${p(d.getUTCDate())} ${d.getUTCFullYear()} ${p(d.getUTCHours())}:${p(d.getUTCMinutes())}:${p(d.getUTCSeconds())} GMT+0000 (Coordinated Universal Time)`;
}

function longVoiceName(shortName: string) {
  const m = shortName.match(/^([a-z]{2,})-([A-Z]{2,})-(.+Neural)$/);
  if (!m) return shortName;
  const lang = m[1];
  let region = m[2];
  let name = m[3];
  const dash = name.indexOf("-");
  if (dash !== -1) {
    region = `${region}-${name.slice(0, dash)}`;
    name = name.slice(dash + 1);
  }
  return `Microsoft Server Speech Text to Speech Voice (${lang}-${region}, ${name})`;
}

function escapeXml(text: string) {
  let clean = "";
  for (const ch of text) {
    const code = ch.charCodeAt(0);
    clean += code < 32 && code !== 9 && code !== 10 && code !== 13 ? " " : ch;
  }
  return clean.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

const signed = (n: number, unit: string) => `${n >= 0 ? "+" : ""}${Math.round(n)}${unit}`;

export interface EdgeOptions {
  voice: string;
  pitchHz: number;
  ratePct: number;
  volumePct: number;
}

class EdgeHandshakeError extends Error {
  status: number;
  constructor(status: number) {
    super(`Edge TTS handshake failed (${status})`);
    this.status = status;
  }
}

function synthesizeOnce(text: string, opts: EdgeOptions): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const url = `wss://${BASE}/edge/v1?TrustedClientToken=${TOKEN}&ConnectionId=${connectionId()}&Sec-MS-GEC=${secMsGec()}&Sec-MS-GEC-Version=${GEC_VERSION}`;
    const ws = new WebSocket(url, {
      headers: {
        Pragma: "no-cache",
        "Cache-Control": "no-cache",
        Origin: "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold",
        "User-Agent": UA,
        "Accept-Language": "en-US,en;q=0.9",
        Cookie: `muid=${muid()};`,
      },
    });
    const chunks: Buffer[] = [];
    let settled = false;

    const finish = (err?: Error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      try {
        ws.terminate();
      } catch {}
      if (err) reject(err);
      else if (!chunks.length) reject(new ProviderError("Edge TTS returned no audio. Try another voice.", 502));
      else resolve(Buffer.concat(chunks));
    };

    const timer = setTimeout(() => finish(new ProviderError("Edge TTS timed out", 504)), 60_000);

    ws.on("unexpected-response", (_req, res) => {
      const date = res.headers.date;
      if (res.statusCode === 403 && date) {
        const server = Date.parse(date) / 1000;
        if (Number.isFinite(server)) clockSkew = server - Date.now() / 1000;
      }
      finish(new EdgeHandshakeError(res.statusCode ?? 0));
    });

    ws.on("error", (e) => finish(e instanceof Error ? e : new Error(String(e))));

    ws.on("open", () => {
      const ts = dateString();
      ws.send(
        `X-Timestamp:${ts}\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n` +
          `{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}\r\n`,
      );
      const ssml =
        `<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>` +
        `<voice name='${longVoiceName(opts.voice)}'>` +
        `<prosody pitch='${signed(opts.pitchHz, "Hz")}' rate='${signed(opts.ratePct, "%")}' volume='${signed(opts.volumePct, "%")}'>` +
        `${escapeXml(text)}</prosody></voice></speak>`;
      ws.send(
        `X-RequestId:${connectionId()}\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:${ts}Z\r\nPath:ssml\r\n\r\n${ssml}`,
      );
    });

    ws.on("message", (data, isBinary) => {
      const buf = Buffer.isBuffer(data)
        ? data
        : Array.isArray(data)
          ? Buffer.concat(data)
          : Buffer.from(data as ArrayBuffer);
      if (isBinary) {
        if (buf.length < 2) return;
        const headerLen = buf.readUInt16BE(0);
        const header = buf.subarray(2, 2 + headerLen).toString("utf8");
        if (header.includes("Path:audio")) {
          const body = buf.subarray(2 + headerLen);
          if (body.length) chunks.push(Buffer.from(body));
        }
      } else if (buf.toString("utf8").includes("Path:turn.end")) {
        finish();
      }
    });

    ws.on("close", () => finish());
  });
}

export async function edgeSynthesize(text: string, opts: EdgeOptions): Promise<Buffer> {
  try {
    return await synthesizeOnce(text, opts);
  } catch (err) {
    if (err instanceof EdgeHandshakeError && err.status === 403) return synthesizeOnce(text, opts);
    if (err instanceof EdgeHandshakeError) throw new ProviderError(err.message, 502);
    throw err;
  }
}

interface EdgeVoiceRaw {
  ShortName: string;
  Gender: string;
  Locale: string;
  VoiceTag?: { VoicePersonalities?: string[]; ContentCategories?: string[] };
}

let voiceCache: { at: number; voices: VoiceProfile[] } | null = null;

export async function edgeVoices(): Promise<VoiceProfile[]> {
  if (voiceCache && Date.now() - voiceCache.at < 6 * 3600_000) return voiceCache.voices;
  const url = `https://${BASE}/voices/list?trustedclienttoken=${TOKEN}&Sec-MS-GEC=${secMsGec()}&Sec-MS-GEC-Version=${GEC_VERSION}`;
  const res = await fetch(url, {
    headers: {
      "Sec-CH-UA": `" Not;A Brand";v="99", "Microsoft Edge";v="${CHROMIUM_MAJOR}", "Chromium";v="${CHROMIUM_MAJOR}"`,
      "Sec-CH-UA-Mobile": "?0",
      Accept: "*/*",
      "Sec-Fetch-Site": "none",
      "Sec-Fetch-Mode": "cors",
      "Sec-Fetch-Dest": "empty",
      "User-Agent": UA,
      "Accept-Language": "en-US,en;q=0.9",
      Cookie: `muid=${muid()};`,
    },
    signal: AbortSignal.timeout(15_000),
  });
  if (!res.ok) throw new ProviderError(`Edge voice list unavailable (${res.status})`, 502);
  const raw = (await res.json()) as EdgeVoiceRaw[];
  const voices = raw.map<VoiceProfile>((r) => {
    const gender: Gender = r.Gender === "Female" ? "female" : r.Gender === "Male" ? "male" : "neutral";
    const tags = [...(r.VoiceTag?.VoicePersonalities ?? []), ...(r.VoiceTag?.ContentCategories ?? [])].map((t) =>
      t.toLowerCase(),
    );
    return {
      id: r.ShortName,
      name: edgeDisplayName(r.ShortName),
      gender,
      langs: [r.Locale],
      tags,
      multilingual: r.ShortName.includes("Multilingual"),
      age: /-(Ana|Maisie)Neural$/.test(r.ShortName) ? "child" : undefined,
    };
  });
  voiceCache = { at: Date.now(), voices };
  return voices;
}
