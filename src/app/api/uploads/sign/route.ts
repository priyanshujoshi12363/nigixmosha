import { requireUser } from "@/lib/server/auth";
import { uploadTicket } from "@/lib/server/cloudinary";
import { ProviderError, toErrorResponse } from "@/lib/server/errors";
import { expectedPublicId, parseId, projects, readBody } from "@/lib/server/projects";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  try {
    const { id: ownerId } = await requireUser();
    const { projectId } = await readBody<{ projectId?: string }>(request);
    const _id = parseId(projectId);
    const exists = await (await projects()).findOne({ _id, ownerId }, { projection: { _id: 1 } });
    if (!exists) throw new ProviderError("Project not found", 404);
    return Response.json(uploadTicket(expectedPublicId(ownerId, String(projectId))));
  } catch (err) {
    return toErrorResponse(err);
  }
}
