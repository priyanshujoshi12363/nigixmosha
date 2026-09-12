"use client";

import { useEffect, useMemo, useRef, useState, type MouseEvent } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Download, Pause, Play, RotateCcw, RotateCw } from "lucide-react";
import { LogoMark } from "@/components/logo";
import { Initials, buttonClass } from "@/components/ui";
import { languageName } from "@/lib/engine/lang";
import { useProject } from "@/lib/store/project";
import type { TimelineEntry } from "@/lib/types";
import { NARRATOR_ID } from "@/lib/types";
import { NARRATOR_COLOR, NARRATOR_ON_DARK, cn, formatDuration } from "@/lib/utils";

const RATES = [0.75, 1, 1.25, 1.5, 2];
const BINS = 220;

function findEntry(timeline: TimelineEntry[], t: number) {
  let lo = 0;
  let hi = timeline.length - 1;
  let best = -1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (timeline[mid].start <= t) {
      best = mid;
      lo = mid + 1;
    } else hi = mid - 1;
  }
  return best;
}

function slugify(s: string) {
  return (
    s
      .toLowerCase()
      .replace(/[^\p{L}\p{N}]+/gu, "-")
      .replace(/^-+|-+$/g, "")
      .slice(0, 60) || "audiobook"
  );
}

export function Player() {
  const { output, analysis, segments } = useProject();
  const audioRef = useRef<HTMLAudioElement>(null);
  const [playing, setPlaying] = useState(false);
  const [time, setTime] = useState(0);
  const [rate, setRate] = useState(1);
  const [failedUrl, setFailedUrl] = useState<string | null>(null);

  const colorOf = useMemo(() => {
    const m = new Map<string, string>([[NARRATOR_ID, NARRATOR_ON_DARK]]);
    analysis?.characters.forEach((c) => m.set(c.id, c.color));
    return m;
  }, [analysis]);

  const bars = useMemo(() => {
    if (!output) return [];
    const per = output.peaks.length / BINS;
    return Array.from({ length: BINS }, (_, i) => {
      const slice = output.peaks.slice(Math.floor(i * per), Math.floor((i + 1) * per));
      const peak = slice.length ? Math.max(...slice) : 0;
      const t = ((i + 0.5) / BINS) * output.duration;
      const idx = findEntry(output.timeline, t);
      const entry = output.timeline[idx];
      const speaking = entry && t <= entry.end + 0.05;
      return { h: 0.12 + peak * 0.88, color: speaking ? (colorOf.get(entry.speaker) ?? NARRATOR_ON_DARK) : "#334155" };
    });
  }, [output, colorOf]);

  useEffect(() => {
    const el = audioRef.current;
    if (!el || !playing) return;
    let raf = 0;
    const tick = () => {
      setTime(el.currentTime);
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [playing]);

  useEffect(() => {
    if (audioRef.current) audioRef.current.playbackRate = rate;
  }, [rate]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement)?.tagName;
      if (e.code !== "Space" || tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return;
      e.preventDefault();
      const el = audioRef.current;
      if (!el) return;
      if (el.paused) void el.play();
      else el.pause();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  if (!output || !analysis) return null;

  const idx = findEntry(output.timeline, time);
  const entry = idx >= 0 ? output.timeline[idx] : undefined;
  const seg = entry ? segments.find((s) => s.id === entry.segmentId) : undefined;
  const upcoming = output.timeline
    .slice(idx + 1, idx + 3)
    .map((e) => segments.find((s) => s.id === e.segmentId))
    .filter(Boolean);
  const speaker = entry ? analysis.characters.find((c) => c.id === entry.speaker) : undefined;
  const speakerName = speaker?.name ?? "Narrator";
  const speakerColor = speaker?.color ?? NARRATOR_COLOR;
  const progress = output.duration ? time / output.duration : 0;
  const src = failedUrl === output.url && output.remote ? output.remote.url : output.url;
  const downloadHref = output.remote?.downloadUrl ?? output.url;

  const seek = (t: number) => {
    const el = audioRef.current;
    if (!el) return;
    el.currentTime = Math.max(0, Math.min(output.duration, t));
    setTime(el.currentTime);
  };

  const onWave = (e: MouseEvent<HTMLDivElement>) => {
    const r = e.currentTarget.getBoundingClientRect();
    seek(((e.clientX - r.left) / r.width) * output.duration);
  };

  const toggle = () => {
    const el = audioRef.current;
    if (!el) return;
    if (el.paused) void el.play();
    else el.pause();
  };

  const jumpToSpeaker = (id: string) => {
    const next = output.timeline.find((e) => e.speaker === id && e.start > time + 0.2) ?? output.timeline.find((e) => e.speaker === id);
    if (next) {
      seek(next.start);
      void audioRef.current?.play();
    }
  };

  const speakers = [
    { id: NARRATOR_ID, name: "Narrator", color: NARRATOR_COLOR },
    ...analysis.characters.map((c) => ({ id: c.id, name: c.name, color: c.color })),
  ];

  return (
    <div className="overflow-hidden rounded-[2rem] bg-night text-white shadow-[0_40px_120px_-40px_rgba(7,11,20,0.6)]">
      <audio
        ref={audioRef}
        src={src}
        preload="auto"
        onError={() => {
          if (output.remote && src !== output.remote.url) setFailedUrl(output.url);
        }}
        onPlay={() => setPlaying(true)}
        onPause={() => {
          setPlaying(false);
          if (audioRef.current) setTime(audioRef.current.currentTime);
        }}
        onEnded={() => setPlaying(false)}
        onSeeked={() => audioRef.current && setTime(audioRef.current.currentTime)}
      />
      <div className="relative grid gap-8 p-6 sm:p-10 lg:grid-cols-[300px_minmax(0,1fr)]">
        <div className="absolute -left-20 -top-20 size-96 rounded-full blur-[110px] transition-colors duration-1000" style={{ background: `${speakerColor}55` }} />
        <div className="absolute -bottom-28 right-0 size-96 rounded-full bg-white/[0.06] blur-[120px]" />

        <div className="relative">
          <div className="relative aspect-square overflow-hidden rounded-3xl bg-brand p-6 shadow-2xl">
            <div className="absolute inset-0 opacity-30 mix-blend-overlay dot-grid" />
            <motion.div
              className="absolute -right-10 -top-10 size-48 rounded-full bg-white/25 blur-2xl"
              animate={playing ? { scale: [1, 1.25, 1], opacity: [0.5, 0.9, 0.5] } : { scale: 1, opacity: 0.5 }}
              transition={{ duration: 3, repeat: playing ? Infinity : 0 }}
            />
            <div className="relative flex h-full flex-col justify-between">
              <LogoMark animated={playing} className="size-10 drop-shadow" />
              <div>
                <div className="font-mono text-[10px] uppercase tracking-[0.2em] text-white/75">A full-cast audiobook</div>
                <div className="mt-2 line-clamp-3 font-display text-2xl font-semibold leading-[1.12] tracking-[-0.03em]">{analysis.title}</div>
                <div className="mt-2 text-xs text-white/75">
                  {languageName(analysis.language)} · {analysis.characters.length + 1} voices
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="relative flex min-w-0 flex-col">
          <div className="flex items-center justify-between gap-3">
            <div className="font-mono text-[10.5px] uppercase tracking-[0.2em] text-white/50">Now speaking</div>
            <a
              href={downloadHref}
              download={output.remote ? undefined : `${slugify(analysis.title)}.wav`}
              className={buttonClass("outline", "sm", "border-white/15 bg-white/10 text-white hover:bg-white/15")}
            >
              <Download className="size-3.5" /> {output.remote ? "Download MP3" : "Download WAV"}
            </a>
          </div>

          <div className="mt-4 min-h-[150px]">
            <AnimatePresence mode="wait">
              <motion.div
                key={seg?.id ?? "idle"}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.3 }}
              >
                <div className="flex items-center gap-3">
                  <Initials name={speakerName} color={speakerColor} size={36} />
                  <div className="font-semibold">{speakerName}</div>
                  {seg && (
                    <span className="rounded-full bg-white/10 px-2.5 py-0.5 text-[11px] capitalize text-white/70">{seg.emotion}</span>
                  )}
                </div>
                <p className="mt-4 line-clamp-4 text-xl font-semibold leading-snug text-white sm:text-2xl">
                  {seg?.text ?? "Press play to begin."}
                </p>
              </motion.div>
            </AnimatePresence>
          </div>
          {upcoming.length > 0 && (
            <div className="mt-3 space-y-1 text-sm text-white/35">
              {upcoming.map((u) => (
                <p key={u!.id} className="truncate">
                  {u!.text}
                </p>
              ))}
            </div>
          )}

          <div className="mt-auto pt-8">
            <div className="group relative flex h-20 cursor-pointer items-center gap-[2px]" onClick={onWave} role="slider" aria-label="Seek" aria-valuemin={0} aria-valuemax={output.duration} aria-valuenow={time}>
              {bars.map((b, i) => {
                const played = (i + 0.5) / BINS <= progress;
                return (
                  <span
                    key={i}
                    className="flex-1 rounded-full transition-opacity duration-150"
                    style={{ height: `${b.h * 100}%`, background: b.color, opacity: played ? 1 : 0.28 }}
                  />
                );
              })}
              <span className="pointer-events-none absolute inset-y-0 w-px bg-white/80" style={{ left: `${progress * 100}%` }} />
            </div>
            <div className="mt-2 flex justify-between font-mono text-[11px] tabular-nums text-white/50">
              <span>{formatDuration(time)}</span>
              <span>{formatDuration(output.duration)}</span>
            </div>

            <div className="mt-5 flex flex-wrap items-center gap-3">
              <button type="button" onClick={() => seek(time - 10)} className="grid size-10 cursor-pointer place-items-center rounded-full text-white/70 hover:bg-white/10 hover:text-white" aria-label="Back 10 seconds">
                <RotateCcw className="size-4" />
              </button>
              <button
                type="button"
                onClick={toggle}
                className="grid size-14 cursor-pointer place-items-center rounded-full bg-white text-night shadow-xl transition hover:scale-105 active:scale-95"
                aria-label={playing ? "Pause" : "Play"}
              >
                {playing ? <Pause className="size-5 fill-current" /> : <Play className="ml-0.5 size-5 fill-current" />}
              </button>
              <button type="button" onClick={() => seek(time + 10)} className="grid size-10 cursor-pointer place-items-center rounded-full text-white/70 hover:bg-white/10 hover:text-white" aria-label="Forward 10 seconds">
                <RotateCw className="size-4" />
              </button>
              <div className="ml-2 flex rounded-full bg-white/10 p-0.5">
                {RATES.map((r) => (
                  <button
                    key={r}
                    type="button"
                    onClick={() => setRate(r)}
                    className={cn(
                      "cursor-pointer rounded-full px-2.5 py-1 text-[11px] tabular-nums transition",
                      rate === r ? "bg-white text-night" : "text-white/60 hover:text-white",
                    )}
                  >
                    {r}×
                  </button>
                ))}
              </div>
            </div>

            <div className="mt-6 flex flex-wrap gap-1.5">
              {speakers.map((s) => (
                <button
                  key={s.id}
                  type="button"
                  onClick={() => jumpToSpeaker(s.id)}
                  className={cn(
                    "inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-2.5 py-1 text-[12px] transition",
                    entry?.speaker === s.id ? "border-white/40 bg-white/15 text-white" : "border-white/10 text-white/60 hover:text-white",
                  )}
                  title={`Jump to ${s.name}'s next line`}
                >
                  <span className="size-2 rounded-full" style={{ background: s.id === NARRATOR_ID ? NARRATOR_ON_DARK : s.color }} />
                  {s.name}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
