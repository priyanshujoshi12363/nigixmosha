import type { ProjectPayload } from "@/lib/library-types";
import { requireUser } from "@/lib/server/auth";
import { toErrorResponse } from "@/lib/server/errors";
import { projects, readBody, sanitize, toSummary, type ProjectDoc } from "@/lib/server/projects";

export const dynamic = "force-dynamic";

export async function GET() {
  try {
    const { id: ownerId } = await requireUser();
    const col = await projects();
    const docs = await col
      .find(
        { ownerId },
        { projection: { text: 0, segments: 0, warnings: 0, cast: 0, "audio.timeline": 0, "audio.peaks": 0 } },
      )
      .sort({ updatedAt: -1 })
      .limit(500)
      .toArray();
    return Response.json({ projects: docs.map(toSummary) });
  } catch (err) {
    return toErrorResponse(err);
  }
}

export async function POST(request: Request) {
  try {
    const { id: ownerId } = await requireUser();
    const body = await readBody<ProjectPayload>(request);
    const now = new Date();
    const doc: ProjectDoc = {
      ownerId,
      title: "Untitled chapter",
      text: "",
      languageHint: "auto",
      analysis: null,
      segments: [],
      warnings: [],
      cast: {},
      castFor: null,
      castReasons: {},
      castBy: null,
      shareNarrator: false,
      stats: { segments: 0, words: 0 },
      ...sanitize(body),
      audio: null,
      createdAt: now,
      updatedAt: now,
    };
    const res = await (await projects()).insertOne(doc);
    return Response.json({ id: res.insertedId.toHexString() }, { status: 201 });
  } catch (err) {
    return toErrorResponse(err);
  }
}
