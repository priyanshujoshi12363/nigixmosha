import { brainConfig } from "./env";
import { ProviderError } from "./errors";

export interface BrainRequest {
  system: string;
  prompt: string;
  json?: boolean;
}

const UNAVAILABLE = "The director is unavailable right now. Please try again in a moment.";
const BUSY = "The director is busy right now. Please try again in a minute.";
const MAX_PROMPT = 250_000;

export async function callBrain(req: BrainRequest, signal?: AbortSignal): Promise<string> {
  const { baseUrl, cloud, model, apiKey } = brainConfig();
  if (cloud && !apiKey) {
    console.error("[director] OLLAMA_API_KEY is not set");
    throw new ProviderError(UNAVAILABLE, 503, "director_unavailable");
  }
  if (req.system.length + req.prompt.length > MAX_PROMPT) {
    throw new ProviderError("This chapter is too long to direct at once. Try a shorter chapter.", 413);
  }

  const timeout = AbortSignal.timeout(290_000);
  let res: Response;
  try {
    res = await fetch(`${baseUrl}/api/chat`, {
      method: "POST",
      signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
      headers: { "content-type": "application/json", ...(apiKey ? { Authorization: `Bearer ${apiKey}` } : {}) },
      body: JSON.stringify({
        model,
        messages: [
          { role: "system", content: req.system },
          { role: "user", content: req.prompt },
        ],
        stream: false,
        ...(req.json ? { format: "json" } : {}),
        options: { temperature: 0.3, ...(cloud ? {} : { num_ctx: 16384 }) },
      }),
    });
  } catch (err) {
    if (err instanceof DOMException && (err.name === "AbortError" || err.name === "TimeoutError")) throw err;
    console.error("[director] request failed:", err);
    throw new ProviderError(UNAVAILABLE, 502, "director_unavailable");
  }

  if (!res.ok) {
    const detail = await res.text().catch(() => "");
    console.error(`[director] upstream ${res.status}: ${detail.slice(0, 300)}`);
    const busy = res.status === 429;
    throw new ProviderError(busy ? BUSY : UNAVAILABLE, busy ? 429 : 502, "director_unavailable");
  }

  const data = (await res.json()) as { message?: { content?: string } };
  return data.message?.content ?? "";
}
