"use client";

import { motion } from "motion/react";
import { ArrowRight, Sparkles } from "lucide-react";
import { ButtonLink } from "@/components/ui";
import { HeroStage } from "./hero-stage";

const LINE_ONE = ["Every", "character", "deserves"];
const ease = [0.2, 0.7, 0.2, 1] as const;

export function Hero() {
  return (
    <section className="grain relative overflow-hidden pb-24 pt-32 sm:pt-40">
      <div aria-hidden className="pointer-events-none absolute inset-0">
        <div className="absolute -left-40 -top-40 h-[560px] w-[560px] animate-aurora rounded-full bg-neutral-300/50 blur-[120px]" />
        <div
          className="absolute -right-32 top-10 h-[520px] w-[520px] animate-aurora rounded-full bg-neutral-200/80 blur-[120px]"
          style={{ animationDelay: "-7s" }}
        />
        <div
          className="absolute left-1/3 top-72 h-[420px] w-[420px] animate-aurora rounded-full bg-neutral-300/40 blur-[110px]"
          style={{ animationDelay: "-13s" }}
        />
        <div className="dot-grid absolute inset-0 [mask-image:radial-gradient(ellipse_at_center,black_20%,transparent_70%)]" />
      </div>

      <div className="relative mx-auto max-w-6xl px-6 text-center">
        <motion.div
          initial={{ opacity: 0, y: 12, scale: 0.96 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ duration: 0.7, ease }}
          className="inline-flex items-center gap-2 rounded-full border border-line bg-white/70 py-1 pl-1 pr-4 text-[13px] text-ink-2 shadow-sm backdrop-blur"
        >
          <span className="inline-flex items-center gap-1 rounded-full bg-ink px-2.5 py-0.5 text-[11px] font-medium text-paper">
            <Sparkles className="size-3" /> New
          </span>
          Full-cast audiobooks<span className="-ml-1 hidden sm:inline">from a single chapter</span>
        </motion.div>

        <h1 className="mx-auto mt-8 max-w-5xl font-display text-[2.2rem] font-semibold leading-[1.04] tracking-[-0.045em] text-ink sm:text-6xl lg:text-[4.5rem]">
          <span className="block">
            {LINE_ONE.map((w, i) => (
              <span key={w} className="inline-block overflow-hidden pb-2 align-bottom">
                <motion.span
                  className="mr-[0.22em] inline-block"
                  initial={{ y: "110%" }}
                  animate={{ y: 0 }}
                  transition={{ duration: 0.9, delay: 0.15 + i * 0.09, ease }}
                >
                  {w}
                </motion.span>
              </span>
            ))}
          </span>
          <span className="block overflow-hidden pb-3">
            <motion.span
              className="text-gradient inline-block"
              initial={{ y: "110%" }}
              animate={{ y: 0 }}
              transition={{ duration: 1, delay: 0.45, ease }}
            >
              their own voice.
            </motion.span>
          </span>
        </h1>

        <motion.p
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.7, ease }}
          className="mx-auto mt-7 max-w-2xl text-[17px] leading-relaxed text-ink-2 sm:text-lg"
        >
          nigixmosha reads your novel like a director. It finds every character, understands how they think and speak,
          casts a voice that fits — then performs each line with emotion. In Hindi, Tamil, English, Japanese and 40+ more.
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.85, ease }}
          className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row"
        >
          <ButtonLink href="/signup" variant="brand" size="lg" className="group">
            Start creating — it&apos;s free
            <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
          </ButtonLink>
          <ButtonLink href="/#how" variant="outline" size="lg">
            See how it works
          </ButtonLink>
        </motion.div>
        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.8, delay: 1.1 }}
          className="mt-5 font-mono text-[11px] uppercase tracking-[0.18em] text-muted"
        >
          Free voices built in · Private history · Bring your own keys
        </motion.p>
      </div>

      <motion.div
        initial={{ opacity: 0, y: 60, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 1.1, delay: 0.9, ease }}
        className="relative mx-auto mt-16 max-w-6xl px-4 sm:px-6"
      >
        <HeroStage />
      </motion.div>
    </section>
  );
}
