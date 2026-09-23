import catalog from "./sounds/catalog.json";

export type SoundKind = "bed" | "shot";

export interface SoundVariant {
  id: number;
  url: string;
  seconds: number;
  channels: number;
  name: string;
  user: string;
  page: string;
  license: string;
}

export interface SoundTag {
  tag: string;
  kind: SoundKind;
  label: string;
  gain: number;
  variants: SoundVariant[];
}

export interface SoundCatalog {
  version: number;
  generatedAt: string;
  source: string;
  tags: SoundTag[];
}

export const SOUND_CATALOG = catalog as SoundCatalog;

export const SOUND_TAGS = SOUND_CATALOG.tags.filter((t) => t.variants.length > 0);

const byTag = new Map(SOUND_TAGS.map((t) => [t.tag, t]));

export const soundTag = (tag: string) => byTag.get(tag);

export const hasSounds = SOUND_TAGS.length > 0;

export function soundMenu(kind: SoundKind) {
  return SOUND_TAGS.filter((t) => t.kind === kind)
    .map((t) => `${t.tag} (${t.label.toLowerCase()})`)
    .join(", ");
}

export function pickVariant(tag: string, nth = 0): SoundVariant | null {
  const entry = byTag.get(tag);
  if (!entry || !entry.variants.length) return null;
  return entry.variants[Math.abs(nth) % entry.variants.length];
}

export function resolveTag(tag: string, fallback?: string): string | null {
  const clean = (v: string) => v.trim().toLowerCase().replace(/[\s-]+/g, "_");
  for (const candidate of [tag, fallback]) {
    if (!candidate) continue;
    const key = clean(candidate);
    if (byTag.has(key)) return key;
    const loose = SOUND_TAGS.find((t) => t.tag.startsWith(key) || key.startsWith(t.tag));
    if (loose) return loose.tag;
  }
  return null;
}

export function soundCredits(tags: string[]) {
  const seen = new Set<string>();
  const out: { name: string; user: string; page: string }[] = [];
  for (const tag of tags) {
    for (const v of byTag.get(tag)?.variants ?? []) {
      if (seen.has(v.page)) continue;
      seen.add(v.page);
      out.push({ name: v.name, user: v.user, page: v.page });
    }
  }
  return out;
}
