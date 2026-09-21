import { ObjectId } from "mongodb";
import { endSession, rateLimit, requireUser, users, verifyPassword } from "@/lib/server/auth";
import { destroyAsset } from "@/lib/server/cloudinary";
import { ProviderError, toErrorResponse } from "@/lib/server/errors";
import { projects, readBody } from "@/lib/server/projects";

export const dynamic = "force-dynamic";

export async function DELETE(request: Request) {
  try {
    const me = await requireUser();
    rateLimit(`delete-account:${me.id}`, 5, 10 * 60_000);
    const body = await readBody<{ password?: string }>(request);
    const _id = new ObjectId(me.id);
    const col = await users();
    const user = await col.findOne({ _id });
    if (!user || !(await verifyPassword(typeof body.password === "string" ? body.password : "", user.passwordHash))) {
      throw new ProviderError("Your password is incorrect.", 400);
    }
    const projectCol = await projects();
    const owned = await projectCol.find({ ownerId: me.id }, { projection: { "audio.publicId": 1 } }).toArray();
    await Promise.all(
      owned
        .map((p) => p.audio?.publicId)
        .filter((id): id is string => Boolean(id))
        .map((id) => destroyAsset(id).catch(() => undefined)),
    );
    await projectCol.deleteMany({ ownerId: me.id });
    await endSession(_id);
    await col.deleteOne({ _id });
    return Response.json({ ok: true });
  } catch (err) {
    return toErrorResponse(err);
  }
}
