"use client";

import { useRef } from "react";
import { motion, useScroll, useTransform } from "motion/react";
import { AudioWaveform, BookOpenText, Drama, FileUp } from "lucide-react";
import { SectionEyebrow } from "@/components/ui";
import { Reveal } from "./reveal";

const STEPS = [
  {
    icon: FileUp,
    title: "Drop in a chapter",
    body: "Paste text or upload .txt, .docx, .pdf. One chapter at a time keeps the director sharp and the cast consistent.",
    tag: "manuscript",
  },
  {
    icon: BookOpenText,
    title: "The director reads",
    body: "The director maps every speaker — their age, temperament, the way they talk — and scripts each line with its emotion.",
    tag: "analysis",
  },
  {
    icon: Drama,
    title: "Voices are cast",
    body: "The director picks a voice for every character from your chosen voice engine, tuned in pitch and pace. Swap any voice with a click.",
    tag: "casting",
  },
  {
    icon: AudioWaveform,
    title: "Mastered & stitched",
    body: "Lines are performed, trimmed, loudness-matched and woven together with natural pauses into one audiobook.",
    tag: "production",
  },
];

export function How() {
  const ref = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start 80%", "end 60%"] });
  const width = useTransform(scrollYProgress, [0, 1], ["0%", "100%"]);

  return (
    <section id="how" className="relative scroll-mt-24 py-24">
      <div className="mx-auto max-w-6xl px-6">
        <Reveal className="max-w-2xl">
          <SectionEyebrow>How it works</SectionEyebrow>
          <h2 className="mt-4 font-display text-3xl font-semibold leading-[1.1] tracking-[-0.035em] text-ink sm:text-5xl">
            From page to performance <span className="text-gradient">in four acts.</span>
          </h2>
        </Reveal>

        <div ref={ref} className="relative mt-16">
          <div className="absolute left-0 right-0 top-7 hidden h-px bg-line lg:block">
            <motion.div style={{ width }} className="h-full bg-gradient-to-r from-tide via-cobalt to-iris" />
          </div>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {STEPS.map((s, i) => (
              <Reveal key={s.title} delay={i * 0.1}>
                <div className="group relative h-full">
                  <div className="relative z-10 mb-6 grid size-14 place-items-center rounded-2xl border border-line bg-white shadow-sm transition-transform duration-500 group-hover:-translate-y-1 group-hover:rotate-[-4deg]">
                    <s.icon className="size-6 text-ink" strokeWidth={1.6} />
                    <span className="absolute -right-2 -top-2 grid size-6 place-items-center rounded-full bg-ink font-mono text-[10px] text-paper">
                      {String(i + 1).padStart(2, "0")}
                    </span>
                  </div>
                  <div className="card h-[calc(100%-5rem)] p-6 transition-all duration-500 group-hover:-translate-y-1 group-hover:shadow-[0_30px_60px_-30px_rgba(0,0,0,0.2)]">
                    <div className="font-mono text-[10.5px] uppercase tracking-[0.18em] text-tide">{s.tag}</div>
                    <h3 className="mt-2 text-xl font-semibold tracking-tight text-ink">{s.title}</h3>
                    <p className="mt-3 text-[14.5px] leading-relaxed text-muted">{s.body}</p>
                  </div>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
