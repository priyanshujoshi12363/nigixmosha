export class ProviderError extends Error {
  status: number;
  code?: string;
  constructor(message: string, status = 502, code?: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export async function readProviderError(res: Response, label: string) {
  const raw = await res.text().catch(() => "");
  let msg: unknown = raw;
  try {
    const j = JSON.parse(raw);
    msg = j?.error?.message ?? j?.detail?.message ?? j?.detail ?? j?.error ?? j?.message ?? raw;
  } catch {}
  const text = typeof msg === "string" ? msg : JSON.stringify(msg);
  const status = res.status === 401 || res.status === 403 ? 401 : res.status === 429 ? 429 : res.status === 404 ? 404 : 502;
  return new ProviderError(`${label} (${res.status}): ${text.slice(0, 400) || res.statusText}`, status);
}

export function toErrorResponse(err: unknown) {
  if (err instanceof ProviderError) {
    return Response.json({ error: err.message, ...(err.code ? { code: err.code } : {}) }, { status: err.status });
  }
  if (err instanceof DOMException && (err.name === "AbortError" || err.name === "TimeoutError")) {
    return Response.json({ error: "Request timed out or was cancelled" }, { status: 504 });
  }
  if (err instanceof TypeError && /fetch failed/i.test(err.message)) {
    const cause = (err as TypeError & { cause?: { code?: string; message?: string } }).cause;
    return Response.json(
      { error: `Could not reach the provider${cause?.code ? ` (${cause.code})` : ""}. Check the URL and that the service is running.` },
      { status: 502 },
    );
  }
  const message = err instanceof Error ? err.message : String(err);
  return Response.json({ error: message }, { status: 500 });
}

export function toArrayBuffer(buf: Uint8Array): ArrayBuffer {
  const out = new ArrayBuffer(buf.byteLength);
  new Uint8Array(out).set(buf);
  return out;
}
