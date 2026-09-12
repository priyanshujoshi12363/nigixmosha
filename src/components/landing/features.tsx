"use client";

import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { AudioLines, Gauge, Globe2, PenLine, ShieldCheck, Sparkles } from "lucide-react";
import { Bars, Initials, SectionEyebrow } from "@/components/ui";
import { Reveal } from "./reveal";

const CHARACTERS = [
  { name: "Vikram", color: "#9F1239", traits: ["ruthless", "calm", "precise"], voice: "deep · slow · controlled" },
  { name: "Ananya", color: "#4F46E5", traits: ["witty", "restless", "kind"], voice: "bright · quick · warm" },
  { name: "Dadi", color: "#0D9488", traits: ["wise", "teasing"], voice: "aged · gentle · unhurried" },
];

const EMOTIONS = [
  { e: "whisper", line: "Don't move. They're right outside.", color: "#4338CA" },
  { e: "angry", line: "You promised me. You promised!", color: "#B91C1C" },
  { e: "tender", line: "Sleep now. I'll keep watch.", color: "#7C3AED" },
  { e: "excited", line: "It worked — it actually worked!", color: "#0891B2" },
  { e: "sarcastic", line: "Oh, wonderful. Another prophecy.", color: "#0D9488" },
];

function EmotionCycler() {
  const [i, setI] = useState(0);
  useEffect(() => {
    const t = setInterval(() => setI((x) => (x + 1) % EMOTIONS.length), 2200);
    return () => clearInterval(t);
  }, []);
  const cur = EMOTIONS[i];
  return (
    <div className="mt-6 rounded-2xl bg-night p-5 text-white">
      <AnimatePresence mode="wait">
        <motion.div
          key={i}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -8 }}
          transition={{ duration: 0.3 }}
        >
          <span
            className="inline-block rounded-full px-2.5 py-0.5 font-mono text-[10.5px] uppercase tracking-[0.14em]"
            style={{ background: `${cur.color}33`, color: "white" }}
          >
            {cur.e}
          </span>
          <p className="mt-3 text-xl font-semibold leading-snug">“{cur.line}”</p>
        </motion.div>
      </AnimatePresence>
      <div className="mt-4">
        <Bars color={cur.color} count={28} height={26} className="w-full justify-between" />
      </div>
    </div>
  );
}

const SCRIPTS = ["अ", "অ", "அ", "అ", "ಅ", "അ", "ਅ", "અ", "A", "あ", "가", "ع", "文", "Ж"];

export function Features() {
  return (
    <section id="features" className="relative scroll-mt-24 py-24">
      <div className="mx-auto max-w-6xl px-6">
        <Reveal className="flex flex-col items-start justify-between gap-6 md:flex-row md:items-end">
          <div className="max-w-2xl">
            <SectionEyebrow>Why it sounds alive</SectionEyebrow>
            <h2 className="mt-4 font-display text-3xl font-semibold leading-[1.1] tracking-[-0.035em] text-ink sm:text-5xl">
              Not text-to-speech. <span className="text-muted">A performance.</span>
            </h2>
          </div>
          <p className="max-w-sm text-[15px] leading-relaxed text-muted">
            Single-voice narration flattens a story. nigixmosha gives the villain his menace, the child her wonder, and
            the narrator room to breathe.
          </p>
        </Reveal>

        <div className="mt-14 grid gap-4 md:grid-cols-6">
          <Reveal className="md:col-span-4">
            <div className="card h-full overflow-hidden p-7">
              <div className="flex items-center gap-2 text-sm font-semibold text-ink">
                <Sparkles className="size-4 text-tide" /> Personality-aware casting
              </div>
              <p className="mt-2 max-w-md text-[14.5px] leading-relaxed text-muted">
                The director reads temperament, age and speech patterns, then matches each character to a voice — and
                tunes its pitch and pace so no two sound alike.
              </p>
              <div className="mt-7 grid gap-3 sm:grid-cols-3">
                {CHARACTERS.map((c, i) => (
                  <motion.div
                    key={c.name}
                    initial={{ opacity: 0, y: 20 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true }}
                    transition={{ delay: 0.2 + i * 0.12, duration: 0.6 }}
                    className="rounded-2xl border border-line bg-paper/70 p-4"
                  >
                    <div className="flex items-center gap-3">
                      <Initials name={c.name} color={c.color} size={36} />
                      <div className="font-semibold text-ink">{c.name}</div>
                    </div>
                    <div className="mt-3 flex flex-wrap gap-1">
                      {c.traits.map((t) => (
                        <span key={t} className="rounded-full bg-white px-2 py-0.5 text-[11px] text-ink-2 shadow-sm">
                          {t}
                        </span>
                      ))}
                    </div>
                    <div className="mt-4 flex items-center justify-between border-t border-line pt-3 text-[11.5px] text-muted">
                      <span>{c.voice}</span>
                      <Bars color={c.color} count={4} height={12} />
                    </div>
                  </motion.div>
                ))}
              </div>
            </div>
          </Reveal>

          <Reveal className="md:col-span-2" delay={0.1}>
            <div className="card h-full p-7">
              <div className="text-sm font-semibold text-ink">Emotion on every line</div>
              <p className="mt-2 text-[14.5px] leading-relaxed text-muted">
                Whispers, fury, tenderness — each line carries its own direction.
              </p>
              <EmotionCycler />
            </div>
          </Reveal>

          <Reveal className="md:col-span-2" delay={0.05}>
            <div className="card h-full p-7">
              <div className="flex items-center gap-2 text-sm font-semibold text-ink">
                <Globe2 className="size-4 text-iris" /> 40+ languages, code-mixed too
              </div>
              <p className="mt-2 text-[14.5px] leading-relaxed text-muted">
                Hindi, Tamil, Bengali, Japanese, Spanish — even Hinglish. The narrator speaks the book&apos;s language.
              </p>
              <div className="mt-6 grid grid-cols-7 gap-1.5">
                {SCRIPTS.map((s, i) => (
                  <motion.span
                    key={s}
                    initial={{ opacity: 0, scale: 0.6 }}
                    whileInView={{ opacity: 1, scale: 1 }}
                    viewport={{ once: true }}
                    transition={{ delay: i * 0.04 }}
                    className="grid aspect-square place-items-center rounded-xl bg-paper-2 text-lg text-ink-2 transition hover:bg-ink hover:text-paper"
                  >
                    {s}
                  </motion.span>
                ))}
              </div>
            </div>
          </Reveal>

          <Reveal className="md:col-span-2" delay={0.1}>
            <div className="card h-full p-7">
              <div className="flex items-center gap-2 text-sm font-semibold text-ink">
                <AudioLines className="size-4 text-tide" /> Pick your voices
              </div>
              <p className="mt-2 text-[14.5px] leading-relaxed text-muted">
                Free neural voices out of the box, or plug in Sarvam, ElevenLabs and more for a signature sound.
              </p>
              <div className="mt-6 space-y-2 font-mono text-[12px]">
                {["Edge Neural · free", "Sarvam Bulbul · Indian languages", "ElevenLabs · expressive"].map((k, i) => (
                  <motion.div
                    key={k}
                    initial={{ opacity: 0, x: -12 }}
                    whileInView={{ opacity: 1, x: 0 }}
                    viewport={{ once: true }}
                    transition={{ delay: 0.15 + i * 0.1 }}
                    className="flex items-center justify-between rounded-lg border border-line bg-paper/70 px-3 py-2 text-ink-2"
                  >
                    {k}
                    <span className="size-1.5 rounded-full bg-pine" />
                  </motion.div>
                ))}
              </div>
            </div>
          </Reveal>

          <Reveal className="md:col-span-2" delay={0.15}>
            <div className="card h-full p-7">
              <div className="flex items-center gap-2 text-sm font-semibold text-ink">
                <Gauge className="size-4 text-pine" /> Studio finishing
              </div>
              <p className="mt-2 text-[14.5px] leading-relaxed text-muted">
                Silence trimming, loudness matching across voices, paragraph-aware pauses and a clean WAV master.
              </p>
              <div className="mt-6 flex h-16 items-end gap-[3px]">
                {Array.from({ length: 36 }, (_, i) => (
                  <motion.span
                    key={i}
                    initial={{ height: `${10 + ((i * 53) % 90)}%` }}
                    whileInView={{ height: `${55 + Math.sin(i * 0.9) * 20}%` }}
                    viewport={{ once: true }}
                    transition={{ duration: 1.2, delay: i * 0.02 }}
                    className="flex-1 rounded-full bg-gradient-to-t from-tide to-iris opacity-80"
                  />
                ))}
              </div>
            </div>
          </Reveal>

          <Reveal className="md:col-span-3" delay={0.05}>
            <div className="card flex h-full items-start gap-4 p-7">
              <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-paper-2">
                <PenLine className="size-5 text-ink" />
              </span>
              <div>
                <div className="text-sm font-semibold text-ink">Edit before you render</div>
                <p className="mt-2 text-[14.5px] leading-relaxed text-muted">
                  Reassign a speaker, change an emotion, tweak a word. Preview any line instantly, then produce the whole
                  chapter.
                </p>
              </div>
            </div>
          </Reveal>

          <Reveal className="md:col-span-3" delay={0.1}>
            <div className="card flex h-full items-start gap-4 p-7">
              <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-paper-2">
                <ShieldCheck className="size-5 text-ink" />
              </span>
              <div>
                <div className="text-sm font-semibold text-ink">Private by design</div>
                <p className="mt-2 text-[14.5px] leading-relaxed text-muted">
                  Your chapters, casts and recordings live in your private history. Voice keys never leave your browser.
                </p>
              </div>
            </div>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
