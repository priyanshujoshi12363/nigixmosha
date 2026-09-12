import { ObjectId, type Collection, type WithId } from "mongodb";
import type { ProjectPayload, ProjectRecord, ProjectSummary, StoredAudio } from "@/lib/library-types";
import type { TTSProviderId } from "@/lib/providers";
import type { Analysis, CastEntry, Segment } from "@/lib/types";
import { wordCount } from "@/lib/utils";
import { getDb } from "./db";
import { ProviderError } from "./errors";

export interface ProjectDoc {
  ownerId: string;
  title: string;
  text: string;
  languageHint: string;
  analysis: Analysis | null;
  segments: Segment[];
  warnings: string[];
  cast: Record<string, CastEntry>;
  castFor: { provider: TTSProviderId; model: string } | null;
  castReasons: Record<string, string>;
  castBy: "ai" | "rules" | null;
  shareNarrator: boolean;
  stats: { segments: number; words: number };
  audio: StoredAudio | null;
  createdAt: Date;
  updatedAt: Date;
}

export const MAX_BODY = 8 * 1024 * 1024;

export async function projects(): Promise<Collection<ProjectDoc>> {
  return (await getDb()).collection<ProjectDoc>("projects");
}

export function parseId(id: unknown): ObjectId {
  if (typeof id !== "string" || !/^[0-9a-f]{24}$/i.test(id)) throw new ProviderError("Project not found", 404);
  return new ObjectId(id);
}

export async function readBody<T>(request: Request): Promise<T> {
  const size = Number(request.headers.get("content-length") ?? 0);
  if (size > MAX_BODY) throw new ProviderError("Project is too large to save", 413);
  try {
    return (await request.json()) as T;
  } catch {
    throw new ProviderError("Invalid request body", 400);
  }
}

export function sanitize(body: ProjectPayload): Partial<ProjectDoc> {
  const out: Partial<ProjectDoc> = {};
  if (typeof body.title === "string") out.title = body.title.slice(0, 300);
  if (typeof body.text === "string") out.text = body.text.slice(0, 500_000);
  if (typeof body.languageHint === "string") out.languageHint = body.languageHint.slice(0, 24);
  if (body.analysis === null || (body.analysis && typeof body.analysis === "object")) out.analysis = body.analysis;
  if (Array.isArray(body.segments)) out.segments = body.segments.slice(0, 20_000);
  if (Array.isArray(body.warnings)) out.warnings = body.warnings.filter((w) => typeof w === "string").slice(0, 50);
  if (body.cast && typeof body.cast === "object" && !Array.isArray(body.cast)) out.cast = body.cast;
  if (body.castFor === null || (body.castFor && typeof body.castFor === "object")) out.castFor = body.castFor;
  if (typeof body.shareNarrator === "boolean") out.shareNarrator = body.shareNarrator;
  if (body.castReasons && typeof body.castReasons === "object" && !Array.isArray(body.castReasons)) {
    out.castReasons = Object.fromEntries(
      Object.entries(body.castReasons)
        .filter(([, v]) => typeof v === "string")
        .slice(0, 200)
        .map(([k, v]) => [k.slice(0, 40), String(v).slice(0, 300)]),
    );
  }
  if (body.castBy === "ai" || body.castBy === "rules" || body.castBy === null) out.castBy = body.castBy;
  if (out.segments && out.text !== undefined) {
    out.stats = { segments: out.segments.length, words: wordCount(out.text) };
  }
  return out;
}

export const expectedPublicId = (ownerId: string, projectId: string) => `nigixmosha/${ownerId}/${projectId}`;

export function toRecord(doc: WithId<ProjectDoc>): ProjectRecord {
  return {
    id: doc._id.toHexString(),
    title: doc.title,
    text: doc.text,
    languageHint: doc.languageHint,
    analysis: doc.analysis,
    segments: doc.segments,
    warnings: doc.warnings,
    cast: doc.cast,
    castFor: doc.castFor,
    castReasons: doc.castReasons ?? {},
    castBy: doc.castBy ?? null,
    shareNarrator: doc.shareNarrator,
    audio: doc.audio,
    createdAt: doc.createdAt.toISOString(),
    updatedAt: doc.updatedAt.toISOString(),
  };
}

export function toSummary(doc: Partial<ProjectDoc> & { _id: ObjectId }): ProjectSummary {
  return {
    id: doc._id.toHexString(),
    title: doc.title || doc.analysis?.title || "Untitled chapter",
    language: doc.analysis?.language ?? null,
    characters: (doc.analysis?.characters ?? []).map((c) => ({ name: c.name, color: c.color })),
    segments: doc.stats?.segments ?? 0,
    words: doc.stats?.words ?? 0,
    audio: doc.audio
      ? {
          url: doc.audio.url,
          streamUrl: doc.audio.streamUrl,
          downloadUrl: doc.audio.downloadUrl,
          duration: doc.audio.duration,
        }
      : null,
    createdAt: doc.createdAt?.toISOString() ?? "",
    updatedAt: doc.updatedAt?.toISOString() ?? "",
  };
}
