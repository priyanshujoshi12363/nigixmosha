import { createHash } from "node:crypto";
import type { UploadTicket } from "@/lib/library-types";
import { ProviderError } from "./errors";

export const MAX_AUDIO_BYTES = 95 * 1024 * 1024;

function creds() {
  const cloud = process.env.CLOUDINARY_CLOUD_NAME?.trim();
  const key = process.env.CLOUDINARY_API_KEY?.trim();
  const secret = process.env.CLOUDINARY_API_SECRET?.trim();
  if (!cloud || !key || !secret) throw new ProviderError("Audio storage is not configured (Cloudinary).", 503);
  return { cloud, key, secret };
}

function sign(params: Record<string, string | number>, secret: string) {
  const payload = Object.keys(params)
    .sort()
    .map((k) => `${k}=${params[k]}`)
    .join("&");
  return createHash("sha1").update(payload + secret).digest("hex");
}

export function uploadTicket(publicId: string): UploadTicket {
  const { cloud, key, secret } = creds();
  const params = { invalidate: "true", public_id: publicId, timestamp: Math.floor(Date.now() / 1000) };
  return {
    uploadUrl: `https://api.cloudinary.com/v1_1/${cloud}/video/upload`,
    apiKey: key,
    params,
    signature: sign(params, secret),
    maxBytes: MAX_AUDIO_BYTES,
  };
}

function slug(s: string) {
  return s
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 60);
}

export function audioUrls(publicId: string, version: number, title: string) {
  const { cloud } = creds();
  const base = `https://res.cloudinary.com/${cloud}/video/upload`;
  return {
    url: `${base}/v${version}/${publicId}.wav`,
    streamUrl: `${base}/v${version}/${publicId}.mp3`,
    downloadUrl: `${base}/fl_attachment:${slug(title) || "audiobook"}/v${version}/${publicId}.mp3`,
  };
}

export async function destroyAsset(publicId: string) {
  const { cloud, key, secret } = creds();
  const params = { invalidate: "true", public_id: publicId, timestamp: Math.floor(Date.now() / 1000) };
  const body = new URLSearchParams({
    invalidate: params.invalidate,
    public_id: params.public_id,
    timestamp: String(params.timestamp),
    api_key: key,
    signature: sign(params, secret),
  });
  await fetch(`https://api.cloudinary.com/v1_1/${cloud}/video/destroy`, {
    method: "POST",
    body,
    signal: AbortSignal.timeout(15_000),
  });
}
