const TERMINAL = /[.!?"”’»」』…:;।॥。！？)\]—–-]$/;

export function splitParagraphs(text: string): string[] {
  const clean = text.replace(/\r\n?/g, "\n").trim();
  if (!clean) return [];
  const lines = clean.split("\n");
  const filled = lines.map((l) => l.trim()).filter(Boolean);
  if (!filled.length) return [];
  const blank = lines.length - filled.length;
  const lens = filled.map((l) => l.length).sort((a, b) => a - b);
  const median = lens[Math.floor(lens.length / 2)];
  const unterminated = filled.filter((l) => !TERMINAL.test(l)).length / filled.length;
  const hardWrapped = blank > 0 && median > 40 && median < 100 && unterminated > 0.45;
  if (hardWrapped) {
    return clean
      .split(/\n\s*\n/)
      .map((p) => p.replace(/\s*\n\s*/g, " ").trim())
      .filter(Boolean);
  }
  return filled;
}

const SENTENCE_RE = /[^.!?।॥。！？…]+(?:[.!?।॥。！？…]+["”’»」』)\]]*|$)\s*/g;

export function splitSentences(text: string): string[] {
  const out = text.match(SENTENCE_RE)?.map((s) => s) ?? [text];
  return out.filter((s) => s.trim());
}

function hardSplit(text: string, max: number): string[] {
  const out: string[] = [];
  let rest = text;
  while (rest.length > max) {
    let cut = rest.lastIndexOf(", ", max);
    if (cut < max * 0.5) cut = rest.lastIndexOf(" ", max);
    if (cut < max * 0.3) cut = max;
    out.push(rest.slice(0, cut + 1).trim());
    rest = rest.slice(cut + 1);
  }
  if (rest.trim()) out.push(rest.trim());
  return out;
}

export function splitForTTS(text: string, max: number): string[] {
  const t = text.trim();
  if (t.length <= max) return [t];
  const pieces: string[] = [];
  let buf = "";
  for (const sentence of splitSentences(t)) {
    if (sentence.length > max) {
      if (buf.trim()) pieces.push(buf.trim());
      buf = "";
      pieces.push(...hardSplit(sentence, max));
      continue;
    }
    if ((buf + sentence).length > max) {
      pieces.push(buf.trim());
      buf = "";
    }
    buf += sentence;
  }
  if (buf.trim()) pieces.push(buf.trim());
  return pieces.filter(Boolean);
}

export function chunkParagraphs(paragraphs: string[], maxChars: number): string[] {
  const chunks: string[] = [];
  let current: string[] = [];
  let size = 0;
  const flush = () => {
    if (current.length) chunks.push(current.join("\n\n"));
    current = [];
    size = 0;
  };
  for (const para of paragraphs) {
    if (para.length > maxChars) {
      flush();
      let buf = "";
      for (const s of splitSentences(para)) {
        if ((buf + s).length > maxChars && buf) {
          chunks.push(buf.trim());
          buf = "";
        }
        buf += s;
      }
      if (buf.trim()) chunks.push(buf.trim());
      continue;
    }
    if (size + para.length > maxChars) flush();
    current.push(para);
    size += para.length + 2;
  }
  flush();
  return chunks;
}

export function letterCount(s: string) {
  return (s.match(/[\p{L}\p{N}]/gu) ?? []).length;
}

export function stripOuterQuotes(s: string) {
  return s
    .trim()
    .replace(/^["“”«»„「『'‘]+/, "")
    .replace(/["“”«»」』'’]+$/, "")
    .trim();
}
