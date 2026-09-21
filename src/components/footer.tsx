import Link from "next/link";
import { Logo } from "@/components/logo";

const COLUMNS = [
  {
    title: "Product",
    links: [
      { href: "/studio", label: "Studio" },
      { href: "/settings", label: "Engines & keys" },
      { href: "/#how", label: "How it works" },
      { href: "/privacy", label: "Privacy" },
    ],
  },
  {
    title: "Voices",
    links: [
      { href: "/#engines", label: "Free voices" },
      { href: "/#languages", label: "Languages" },
      { href: "/#features", label: "Emotion & casting" },
    ],
  },
];

export function Footer() {
  return (
    <footer className="relative border-t border-line/80 bg-paper">
      <div className="mx-auto grid max-w-6xl gap-10 px-6 py-14 md:grid-cols-[1.4fr_1fr_1fr]">
        <div className="max-w-sm">
          <Logo />
          <p className="mt-4 text-sm leading-relaxed text-muted">
            A full-cast audiobook studio for storytellers. Every character, their own voice — in the language they
            were written in.
          </p>
        </div>
        {COLUMNS.map((c) => (
          <div key={c.title}>
            <div className="font-mono text-[11px] uppercase tracking-[0.18em] text-muted">{c.title}</div>
            <ul className="mt-4 space-y-2.5 text-sm">
              {c.links.map((l) => (
                <li key={l.label}>
                  <Link href={l.href} className="text-ink-2 transition hover:text-ink">
                    {l.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="border-t border-line/60">
        <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-2 px-6 py-6 text-xs text-muted sm:flex-row sm:items-center">
          <span>© {new Date().getFullYear()} nigixmosha. Crafted for readers who listen.</span>
          <span className="font-mono">Your keys never leave your browser storage.</span>
          <span className="font-mono tracking-wide">
            made by <span className="font-semibold text-ink-2">knoc8</span>
          </span>
        </div>
      </div>
    </footer>
  );
}
