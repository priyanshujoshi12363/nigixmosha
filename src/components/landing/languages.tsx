"use client";

import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { SectionEyebrow } from "@/components/ui";
import { LANGUAGES } from "@/lib/engine/lang";
import { Reveal } from "./reveal";

const GREETINGS = [
  { word: "नमस्ते", lang: "Hindi" },
  { word: "Hello", lang: "English" },
  { word: "வணக்கம்", lang: "Tamil" },
  { word: "নমস্কার", lang: "Bengali" },
  { word: "こんにちは", lang: "Japanese" },
  { word: "Hola", lang: "Spanish" },
  { word: "నమస్కారం", lang: "Telugu" },
  { word: "Bonjour", lang: "French" },
  { word: "안녕하세요", lang: "Korean" },
  { word: "ನಮಸ್ಕಾರ", lang: "Kannada" },
  { word: "مرحبا", lang: "Arabic" },
  { word: "ਸਤ ਸ੍ਰੀ ਅਕਾਲ", lang: "Punjabi" },
];

export function LanguagesSection() {
  const [i, setI] = useState(0);
  useEffect(() => {
    const t = setInterval(() => setI((x) => (x + 1) % GREETINGS.length), 1800);
    return () => clearInterval(t);
  }, []);
  const g = GREETINGS[i];

  return (
    <section id="languages" className="relative scroll-mt-24 overflow-hidden py-24">
      <div className="mx-auto max-w-6xl px-6">
        <div className="grid items-center gap-12 lg:grid-cols-2">
          <Reveal>
            <SectionEyebrow>Multilingual by nature</SectionEyebrow>
            <h2 className="mt-4 font-display text-3xl font-semibold leading-[1.1] tracking-[-0.035em] text-ink sm:text-5xl">
              Stories don&apos;t speak <span className="text-gradient">one language.</span>
            </h2>
            <p className="mt-5 max-w-md text-[15.5px] leading-relaxed text-muted">
              The director detects the language of your chapter — and the ones your characters slip into — and casts
              native voices for each. Indian languages get first-class support through Sarvam and Edge neural voices.
            </p>
            <div className="mt-8 flex flex-wrap gap-2">
              {LANGUAGES.map((l) => (
                <span
                  key={l.code}
                  className="rounded-full border border-line bg-white/70 px-3 py-1.5 text-[13px] text-ink-2 transition hover:border-ink/30 hover:bg-white"
                >
                  {l.native}
                  {l.native !== l.name && <span className="ml-1.5 text-muted">{l.name}</span>}
                </span>
              ))}
            </div>
          </Reveal>

          <Reveal delay={0.15}>
            <div className="relative aspect-square max-h-[480px] w-full">
              <div className="absolute inset-0 animate-spin-slow rounded-full border border-dashed border-line" />
              <div
                className="absolute inset-10 animate-spin-slow rounded-full border border-line"
                style={{ animationDirection: "reverse", animationDuration: "26s" }}
              />
              <div className="absolute inset-20 rounded-full bg-gradient-to-br from-black/[0.08] via-black/[0.03] to-black/[0.08] blur-2xl" />
              <div className="absolute inset-0 grid place-items-center">
                <div className="text-center">
                  <AnimatePresence mode="wait">
                    <motion.div
                      key={g.word}
                      initial={{ opacity: 0, y: 24, filter: "blur(8px)" }}
                      animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
                      exit={{ opacity: 0, y: -24, filter: "blur(8px)" }}
                      transition={{ duration: 0.5 }}
                    >
                      <div className="font-display text-5xl font-semibold text-ink sm:text-6xl">{g.word}</div>
                      <div className="mt-3 font-mono text-[11px] uppercase tracking-[0.2em] text-muted">{g.lang}</div>
                    </motion.div>
                  </AnimatePresence>
                </div>
              </div>
              {GREETINGS.map((x, k) => {
                const angle = (k / GREETINGS.length) * Math.PI * 2;
                return (
                  <span
                    key={x.word}
                    className="absolute size-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full transition-all duration-500"
                    style={{
                      left: `${(50 + Math.cos(angle) * 50).toFixed(3)}%`,
                      top: `${(50 + Math.sin(angle) * 50).toFixed(3)}%`,
                      background: k === i ? "var(--color-tide)" : "var(--color-paper-3)",
                      transform: `translate(-50%, -50%) scale(${k === i ? 1.8 : 1})`,
                    }}
                  />
                );
              })}
            </div>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
