import { requireUser } from "@/lib/server/auth";
import { toErrorResponse } from "@/lib/server/errors";

export const maxDuration = 60;

const MAX_BYTES = 15 * 1024 * 1024;

function stripHtml(html: string) {
  return html
    .replace(/<(script|style)[\s\S]*?<\/\1>/gi, "")
    .replace(/<\/(p|div|h[1-6]|li|blockquote)>/gi, "\n\n")
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'");
}

function reflowPdf(text: string) {
  return text
    .replace(/(\p{L})-\n(\p{L})/gu, "$1$2")
    .replace(/([^\n.!?:;"\p{Pf}\p{Pe}])\n(?=[\p{Ll}\p{Lo}])/gu, "$1 ");
}

function tidy(text: string) {
  const noBom = text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
  return noBom
    .replace(/\r\n?/g, "\n")
    .replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

export async function POST(request: Request) {
  try {
    await requireUser();
    const form = await request.formData();
    const file = form.get("file");
    if (!(file instanceof File)) return Response.json({ error: "No file uploaded" }, { status: 400 });
    if (file.size > MAX_BYTES) return Response.json({ error: "File is larger than 15 MB" }, { status: 413 });

    const name = file.name.toLowerCase();
    const buf = Buffer.from(await file.arrayBuffer());
    let text: string;

    if (name.endsWith(".docx")) {
      const mammoth = await import("mammoth");
      text = (await mammoth.extractRawText({ buffer: buf })).value;
    } else if (name.endsWith(".pdf")) {
      const { extractText, getDocumentProxy } = await import("unpdf");
      const pdf = await getDocumentProxy(new Uint8Array(buf));
      const result = await extractText(pdf, { mergePages: true });
      text = reflowPdf(Array.isArray(result.text) ? result.text.join("\n\n") : result.text);
    } else if (name.endsWith(".html") || name.endsWith(".htm")) {
      text = stripHtml(buf.toString("utf8"));
    } else if (/\.(txt|md|markdown|text)$/.test(name) || file.type.startsWith("text/")) {
      text = buf.toString("utf8");
    } else {
      return Response.json({ error: "Supported files: .txt, .md, .docx, .pdf, .html" }, { status: 415 });
    }

    text = tidy(text);
    if (!text) return Response.json({ error: "No readable text found in that file" }, { status: 422 });
    return Response.json({ text, title: file.name.replace(/\.[^.]+$/, "") });
  } catch (err) {
    return toErrorResponse(err);
  }
}
