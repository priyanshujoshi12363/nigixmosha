"use client";

import { Fragment, useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { BookOpenText, Drama, Languages } from "lucide-react";
import { Bars, Initials } from "@/components/ui";
import { cn } from "@/lib/utils";

type Who = "narrator" | "meera" | "baba" | "stranger";

const CAST: Record<Who, { name: string; color: string; voice: string; traits: string[] }> = {
  narrator: { name: "Narrator", color: "#1F2637", voice: "Ava · warm storyteller", traits: ["measured", "warm"] },
  meera: { name: "Meera", color: "#0D9488", voice: "Swara · bright, youthful", traits: ["curious", "bold", "quick"] },
  baba: { name: "Baba", color: "#4F46E5", voice: "Madhur · aged, gentle", traits: ["wise", "patient"] },
  stranger: { name: "The Stranger", color: "#BE123C", voice: "Guy · low, guarded", traits: ["weary", "guarded"] },
};

const SCENE: { who: Who; text: string; emotion: string; hi: string }[] = [
  {
    who: "narrator",
    text: "The monsoon had not stopped for three days when the knock came.",
    emotion: "calm",
    hi: "तीन दिन से बारिश थमी नहीं थी, जब दरवाज़े पर दस्तक हुई।",
  },
  { who: "meera", text: "Who knocks at this hour?", emotion: "whisper", hi: "इस वक़्त कौन दस्तक दे रहा है?" },
  { who: "narrator", text: "Her grandfather folded his spectacles and smiled.", emotion: "neutral", hi: "दादाजी ने चश्मा मोड़ा और मुस्कुराए।" },
  {
    who: "baba",
    text: "Someone who has walked a long way, child. Let them in.",
    emotion: "tender",
    hi: "कोई जो बहुत दूर से चलकर आया है, बेटी। अंदर आने दो।",
  },
  { who: "stranger", text: "I'm looking for the keeper of the old library.", emotion: "serious", hi: "मुझे पुराने पुस्तकालय के रखवाले की तलाश है।" },
  { who: "meera", text: "Then you have found her.", emotion: "excited", hi: "तो आपने उसे ढूँढ लिया है।" },
];

const DURATION = 3200;

export function HeroStage() {
  const [active, setActive] = useState(0);
  const [hindi, setHindi] = useState(false);

  useEffect(() => {
    const t = setInterval(() => {
      setActive((a) => {
        const next = (a + 1) % SCENE.length;
        if (next === 0) setHindi((h) => !h);
        return next;
      });
    }, DURATION);
    return () => clearInterval(t);
  }, []);

  const line = SCENE[active];
  const who = CAST[line.who];

  return (
    <div className="relative">
      <div className="absolute -inset-x-6 -inset-y-6 rounded-[2.5rem] bg-gradient-to-br from-black/10 via-black/[0.03] to-black/10 blur-2xl" />
      <div className="relative overflow-hidden rounded-[1.75rem] border border-white/70 bg-white/70 p-2 shadow-[0_40px_120px_-40px_rgba(0,0,0,0.3)] backdrop-blur-xl">
        <div className="flex items-center gap-2 px-3 py-2">
          <span className="size-2.5 rounded-full bg-[#ff5f57]" />
          <span className="size-2.5 rounded-full bg-[#febc2e]" />
          <span className="size-2.5 rounded-full bg-[#28c840]" />
          <span className="ml-3 font-mono text-[11px] text-muted">studio / chapter-07-the-keeper</span>
          <span className="ml-auto hidden items-center gap-1.5 rounded-full bg-paper-2 px-2.5 py-1 text-[11px] text-ink-2 sm:flex">
            <Languages className="size-3" />
            <AnimatePresence mode="wait">
              <motion.span
                key={hindi ? "hi" : "en"}
                initial={{ opacity: 0, y: 4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
              >
                {hindi ? "हिन्दी · hi-IN" : "English · en-US"}
              </motion.span>
            </AnimatePresence>
          </span>
        </div>

        <div className="grid gap-2 lg:grid-cols-[1.35fr_1fr]">
          <div className="rounded-[1.25rem] border border-line/70 bg-paper/80 p-6 text-left sm:p-8">
            <div className="mb-5 flex items-center gap-2 font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted">
              <BookOpenText className="size-3.5" /> Chapter 7 · The Keeper
            </div>
            <p className="text-[1.12rem] font-semibold leading-[1.8] tracking-[-0.01em] text-ink sm:text-[1.28rem]">
              {SCENE.map((s, i) => {
                const c = CAST[s.who];
                const on = i === active;
                const text = hindi ? s.hi : s.text;
                const isDialogue = s.who !== "narrator";
                return (
                  <Fragment key={i}>
                    <span
                      className={cn(
                        "rounded-md px-0.5 transition-all duration-500 [box-decoration-break:clone]",
                        on ? "text-ink" : "text-ink/35",
                      )}
                      style={
                        on
                          ? {
                              background: `color-mix(in oklab, ${c.color} ${isDialogue ? 16 : 8}%, transparent)`,
                              boxShadow: `inset 0 -2px 0 ${c.color}`,
                            }
                          : undefined
                      }
                    >
                      {isDialogue ? `“${text}”` : text}
                    </span>{" "}
                  </Fragment>
                );
              })}
            </p>
            <div className="mt-7 flex h-2 overflow-hidden rounded-full bg-paper-3">
              {SCENE.map((s, i) => (
                <div key={i} className="relative h-full flex-1 border-r border-white/70 last:border-0">
                  <div
                    className="absolute inset-y-0 left-0"
                    style={{
                      background: CAST[s.who].color,
                      width: i < active ? "100%" : i === active ? undefined : "0%",
                      animation: i === active ? `grow ${DURATION}ms linear forwards` : undefined,
                      opacity: i <= active ? 0.85 : 0,
                    }}
                  />
                </div>
              ))}
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <div className="relative overflow-hidden rounded-[1.25rem] bg-night p-6 text-left text-white">
              <div
                className="absolute -right-10 -top-10 size-44 rounded-full opacity-50 blur-3xl transition-colors duration-700"
                style={{ background: who.color }}
              />
              <div className="relative font-mono text-[10.5px] uppercase tracking-[0.18em] text-white/50">Now performing</div>
              <AnimatePresence mode="wait">
                <motion.div
                  key={active}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  transition={{ duration: 0.35 }}
                  className="relative mt-4 flex items-center gap-4"
                >
                  <span className="relative">
                    <span className="absolute inset-0 animate-pulse-ring rounded-full" style={{ background: who.color }} />
                    <Initials name={who.name} color={who.color} size={52} className="relative" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="text-lg font-semibold">{who.name}</div>
                    <div className="truncate text-[13px] text-white/60">{who.voice}</div>
                  </div>
                  <span className="rounded-full border border-white/15 bg-white/10 px-2.5 py-1 text-[11px] capitalize">
                    {line.emotion}
                  </span>
                </motion.div>
              </AnimatePresence>
              <div className="relative mt-6 flex h-14 items-center justify-between gap-[3px]">
                {Array.from({ length: 42 }, (_, i) => (
                  <span
                    key={i}
                    className="w-full rounded-full transition-colors duration-500"
                    style={{
                      height: `${(18 + Math.abs(Math.sin(i * 1.7 + active)) * 82).toFixed(2)}%`,
                      background: who.color,
                      opacity: Number((0.35 + Math.abs(Math.cos(i * 0.6)) * 0.65).toFixed(3)),
                      animation: `wave ${(0.7 + (i % 5) * 0.12).toFixed(2)}s ease-in-out ${(i * 0.03).toFixed(2)}s infinite`,
                    }}
                  />
                ))}
              </div>
            </div>

            <div className="rounded-[1.25rem] border border-line/70 bg-paper/80 p-5 text-left">
              <div className="mb-3 flex items-center gap-2 font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted">
                <Drama className="size-3.5" /> Cast · auto-detected
              </div>
              <ul className="space-y-1.5">
                {(Object.keys(CAST) as Who[]).map((k) => {
                  const c = CAST[k];
                  const on = line.who === k;
                  return (
                    <li
                      key={k}
                      className={cn(
                        "flex items-center gap-3 rounded-xl px-2.5 py-2 transition-all duration-500",
                        on ? "bg-white shadow-sm" : "opacity-70",
                      )}
                    >
                      <Initials name={c.name} color={c.color} size={28} />
                      <span className="text-sm font-medium text-ink">{c.name}</span>
                      <span className="hidden gap-1 sm:flex">
                        {c.traits.slice(0, 2).map((t) => (
                          <span key={t} className="rounded-full bg-paper-2 px-2 py-0.5 text-[10.5px] text-muted">
                            {t}
                          </span>
                        ))}
                      </span>
                      <span className="ml-auto">
                        <Bars color={c.color} count={4} playing={on} height={14} />
                      </span>
                    </li>
                  );
                })}
              </ul>
            </div>
          </div>
        </div>
      </div>
      <style>{`@keyframes grow{from{width:0%}to{width:100%}}`}</style>
    </div>
  );
}
