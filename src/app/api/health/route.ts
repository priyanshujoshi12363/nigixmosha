export const dynamic = "force-dynamic";

export function GET() {
  return Response.json(
    { status: "ok", service: "nigixmosha", time: new Date().toISOString() },
    { headers: { "Cache-Control": "no-store" } },
  );
}
