import type { AudioUploadPayload, ProjectPayload, StoredAudio } from "@/lib/library-types";
import { requireUser } from "@/lib/server/auth";
import { audioUrls, destroyAsset } from "@/lib/server/cloudinary";
import { ProviderError, toErrorResponse } from "@/lib/server/errors";
import { expectedPublicId, parseId, projects, readBody, sanitize, toRecord } from "@/lib/server/projects";

export const dynamic = "force-dynamic";

type Ctx = { params: Promise<{ id: string }> };

export async function GET(_request: Request, { params }: Ctx) {
  try {
    const { id: ownerId } = await requireUser();
    const { id } = await params;
    const doc = await (await projects()).findOne({ _id: parseId(id), ownerId });
    if (!doc) throw new ProviderError("Project not found", 404);
    return Response.json({ project: toRecord(doc) });
  } catch (err) {
    return toErrorResponse(err);
  }
}

export async function PUT(request: Request, { params }: Ctx) {
  try {
    const { id: ownerId } = await requireUser();
    const { id } = await params;
    const _id = parseId(id);
    const body = await readBody<ProjectPayload & { audio?: AudioUploadPayload }>(request);
    const col = await projects();
    const update = sanitize(body);
    let replacedAsset: string | null = null;

    if (body.audio) {
      const a = body.audio;
      if (a.publicId !== expectedPublicId(ownerId, id) || !Number.isInteger(a.version) || a.version <= 0) {
        throw new ProviderError("Invalid audio upload", 400);
      }
      const existing = await col.findOne({ _id, ownerId }, { projection: { title: 1, "audio.publicId": 1 } });
      if (!existing) throw new ProviderError("Project not found", 404);
      if (existing.audio?.publicId && existing.audio.publicId !== a.publicId) replacedAsset = existing.audio.publicId;
      const audio: StoredAudio = {
        ...audioUrls(a.publicId, a.version, update.title ?? existing.title),
        publicId: a.publicId,
        version: a.version,
        bytes: Number(a.bytes) || 0,
        duration: Number(a.duration) || 0,
        timeline: Array.isArray(a.timeline) ? a.timeline.slice(0, 20_000) : [],
        peaks: Array.isArray(a.peaks) ? a.peaks.slice(0, 2_000).map(Number) : [],
        provider: a.provider,
        failed: Number(a.failed) || 0,
        createdAt: new Date().toISOString(),
      };
      update.audio = audio;
    }

    const doc = await col.findOneAndUpdate(
      { _id, ownerId },
      { $set: { ...update, updatedAt: new Date() } },
      { returnDocument: "after" },
    );
    if (!doc) throw new ProviderError("Project not found", 404);
    if (replacedAsset) await destroyAsset(replacedAsset).catch(() => undefined);
    return Response.json(body.audio ? { project: toRecord(doc) } : { ok: true });
  } catch (err) {
    return toErrorResponse(err);
  }
}

export async function DELETE(_request: Request, { params }: Ctx) {
  try {
    const { id: ownerId } = await requireUser();
    const { id } = await params;
    const doc = await (await projects()).findOneAndDelete({ _id: parseId(id), ownerId });
    if (!doc) throw new ProviderError("Project not found", 404);
    if (doc.audio?.publicId) await destroyAsset(doc.audio.publicId).catch(() => undefined);
    return Response.json({ ok: true });
  } catch (err) {
    return toErrorResponse(err);
  }
}
