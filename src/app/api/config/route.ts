import { brainReady, serverKeyFlags, storageFlags } from "@/lib/server/env";

export const dynamic = "force-dynamic";

export function GET() {
  return Response.json({ brain: brainReady(), serverKeys: serverKeyFlags(), storage: storageFlags() });
}
