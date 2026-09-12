import { SectionEyebrow } from "@/components/ui";
import { Reveal } from "./reveal";

const VOICES = ["nigixmosha Voices · built in", "Edge Neural · free", "Sarvam Bulbul", "ElevenLabs", "OpenAI Voice", "Gemini Speech", "Piper / XTTS / Kyutai"];

function Row({ items, reverse, dotClass }: { items: string[]; reverse?: boolean; dotClass: string }) {
  const list = [...items, ...items];
  return (
    <div className="relative overflow-hidden [mask-image:linear-gradient(90deg,transparent,black_12%,black_88%,transparent)]">
      <div
        className="flex w-max animate-marquee gap-3"
        style={reverse ? { animationDirection: "reverse", animationDuration: "55s" } : undefined}
      >
        {list.map((name, i) => (
          <span
            key={i}
            className="inline-flex items-center gap-2.5 rounded-full border border-line bg-white/70 px-5 py-2.5 text-[15px] font-medium text-ink-2 shadow-sm"
          >
            <span className={`size-2 rounded-full ${dotClass}`} />
            {name}
          </span>
        ))}
      </div>
    </div>
  );
}

export function Engines() {
  return (
    <section id="engines" className="relative py-20">
      <Reveal className="mx-auto max-w-6xl px-6 text-center">
        <SectionEyebrow>Every voice engine · one studio</SectionEyebrow>
        <h2 className="mx-auto mt-4 max-w-3xl font-display text-3xl font-semibold leading-[1.15] tracking-[-0.035em] text-ink sm:text-4xl">
          Bring the voices you love. <span className="text-muted">We direct the performance.</span>
        </h2>
      </Reveal>
      <div className="mt-12 space-y-3">
        <div className="mx-auto max-w-6xl px-6 font-mono text-[11px] uppercase tracking-[0.2em] text-muted">The voices that perform every line</div><Row items={VOICES} reverse dotClass="bg-neutral-400" />
      </div>
    </section>
  );
}
