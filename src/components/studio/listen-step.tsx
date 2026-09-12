"use client";

import Link from "next/link";
import { AnimatePresence, motion } from "motion/react";
import { AudioLines, Clock, CloudCheck, CloudUpload, Headphones, RefreshCw, TriangleAlert, X } from "lucide-react";
import { retryAudioUpload } from "@/lib/client/library-sync";
import { Bars, Button, Initials } from "@/components/ui";
import { getTTS } from "@/lib/providers";
import { useJobs } from "@/lib/store/jobs";
import { useProject } from "@/lib/store/project";
import { isTTSReady, useSettings } from "@/lib/store/settings";
import { NARRATOR_COLOR, formatDuration, wordCount } from "@/lib/utils";
import { Player } from "./player";

function Producing() {
  const { produceDone, produceTotal, produceCurrent, cancelProduce } = useJobs();
  const { analysis } = useProject();
  const pct = produceTotal ? produceDone / produceTotal : 0;
  const character = analysis?.characters.find((c) => c.id === produceCurrent?.speaker);
  const color = character?.color ?? NARRATOR_COLOR;
  const name = character?.name ?? "Narrator";

  return (
    <div className="relative overflow-hidden rounded-[2rem] bg-night px-6 py-14 text-white sm:px-12">
      <div className="absolute -left-24 -top-24 size-96 animate-aurora rounded-full blur-[100px] transition-colors duration-700" style={{ background: `${color}66` }} />
      <div className="absolute -bottom-32 -right-10 size-[26rem] animate-aurora rounded-full bg-white/[0.06] blur-[110px]" style={{ animationDelay: "-8s" }} />
      <div className="relative mx-auto max-w-2xl text-center">
        <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-white/55">Recording in progress</div>
        <div className="mt-6 font-display text-5xl font-semibold tabular-nums sm:text-6xl">{Math.round(pct * 100)}%</div>
        <div className="mt-2 text-sm text-white/60">
          Line {produceDone} of {produceTotal}
        </div>
        <div className="mx-auto mt-8 h-1.5 max-w-md overflow-hidden rounded-full bg-white/10">
          <motion.div
            className="h-full rounded-full bg-white"
            animate={{ width: `${Math.max(2, pct * 100)}%` }}
            transition={{ ease: "easeOut" }}
          />
        </div>
        <AnimatePresence mode="wait">
          {produceCurrent && (
            <motion.div
              key={produceCurrent.id}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -12 }}
              transition={{ duration: 0.25 }}
              className="mx-auto mt-10 flex max-w-xl items-start gap-4 rounded-2xl bg-white/[0.06] p-5 text-left"
            >
              <Initials name={name} color={color} size={40} />
              <div className="min-w-0">
                <div className="flex items-center gap-2 text-sm font-semibold">
                  {name}
                  <span className="rounded-full bg-white/10 px-2 py-0.5 text-[10.5px] font-normal capitalize text-white/70">
                    {produceCurrent.emotion}
                  </span>
                  <Bars color={color} count={4} height={12} />
                </div>
                <p className="mt-1.5 line-clamp-2 text-base font-semibold leading-snug text-white/85">{produceCurrent.text}</p>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
        <Button onClick={cancelProduce} size="sm" className="mt-10 bg-white/10 text-white shadow-none hover:bg-white/15">
          <X className="size-3.5" /> Stop recording
        </Button>
      </div>
    </div>
  );
}

function LibraryStatus() {
  const { audioSave } = useProject();
  const storage = useSettings((s) => s.storage);
  if (!storage.db || !storage.media) {
    return <span className="text-muted">Cloud saving is off. Download the audiobook to keep it.</span>;
  }
  if (audioSave.status === "uploading") {
    return (
      <span className="inline-flex items-center gap-2 text-ink-2">
        <CloudUpload className="size-4 text-iris" /> Saving to your history… {Math.round(audioSave.progress * 100)}%
        <span className="h-1 w-24 overflow-hidden rounded-full bg-paper-3">
          <span className="block h-full bg-ink transition-all" style={{ width: `${Math.round(audioSave.progress * 100)}%` }} />
        </span>
      </span>
    );
  }
  if (audioSave.status === "saved") {
    return (
      <span className="inline-flex items-center gap-1.5 text-ink-2">
        <CloudCheck className="size-4 text-pine" /> Saved to your{" "}
        <Link href="/history" className="underline underline-offset-2 hover:text-ink">
          history
        </Link>
      </span>
    );
  }
  if (audioSave.status === "error") {
    return (
      <span className="inline-flex flex-wrap items-center gap-2 text-ember">
        <TriangleAlert className="size-4" /> Couldn&apos;t save to your history: {audioSave.error}
        <button type="button" onClick={() => void retryAudioUpload()} className="cursor-pointer font-medium underline underline-offset-2">
          Retry
        </button>
      </span>
    );
  }
  return null;
}

export function ListenStep() {
  const { analysis, segments, output, castFor, setStep } = useProject();
  const { tts } = useSettings();
  const { produceStatus, produceError, startProduce } = useJobs();

  if (!analysis) return null;
  if (produceStatus === "running") return <Producing />;

  const words = segments.reduce((n, s) => n + wordCount(s.text), 0);
  const engine = castFor ? getTTS(castFor.provider) : null;
  const ready = castFor ? isTTSReady(castFor.provider, tts[castFor.provider]) : false;

  return (
    <div className="space-y-5">
      {produceStatus === "error" && produceError && (
        <div className="flex items-start gap-3 rounded-2xl border border-ember/25 bg-ember/5 p-4 text-sm text-ink-2">
          <TriangleAlert className="mt-0.5 size-4 shrink-0 text-ember" />
          <div className="flex-1">
            <div className="font-medium text-ember">Recording stopped</div>
            <div className="mt-1 leading-relaxed">{produceError}</div>
            <div className="mt-2 flex gap-3 text-xs">
              <Link href="/settings" className="font-medium underline underline-offset-2">
                Check engine settings
              </Link>
              <button type="button" className="cursor-pointer font-medium underline underline-offset-2" onClick={() => setStep("cast")}>
                Try another voice engine
              </button>
            </div>
          </div>
        </div>
      )}

      {output ? (
        <>
          <Player />
          <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-muted">
            <div className="flex flex-col gap-1.5">
              <span>
                Recorded with {getTTS(output.provider).name} · {new Date(output.createdAt).toLocaleString()}
                {output.backup > 0 && (
                  <span className="ml-2 text-ink-2">
                    {output.backup} line(s) were performed by the backup voices.
                  </span>
                )}
                {output.failed > 0 && <span className="ml-2 text-ember">{output.failed} line(s) could not be recorded and were skipped.</span>}
              </span>
              <LibraryStatus />
            </div>
            <Button variant="outline" size="sm" onClick={() => void startProduce()} disabled={!ready}>
              <RefreshCw className="size-3.5" /> Re-record with latest edits
            </Button>
          </div>
        </>
      ) : (
        <div className="card relative overflow-hidden px-6 py-16 text-center">
          <div className="absolute inset-0 dot-grid opacity-60 [mask-image:radial-gradient(ellipse_at_center,black,transparent_70%)]" />
          <div className="relative mx-auto max-w-lg">
            <span className="mx-auto grid size-14 place-items-center rounded-2xl bg-brand text-white shadow-lg">
              <Headphones className="size-6" />
            </span>
            <h2 className="mt-6 font-display text-3xl font-semibold tracking-[-0.035em] text-ink">Ready to record</h2>
            <p className="mt-3 text-[15px] leading-relaxed text-muted">
              {analysis.characters.length + 1} voices, {segments.length} lines. Each line is performed, trimmed and loudness-matched,
              then stitched into one audiobook.
            </p>
            <div className="mt-6 flex flex-wrap items-center justify-center gap-4 text-sm text-ink-2">
              <span className="inline-flex items-center gap-1.5">
                <Clock className="size-4 text-muted" /> ≈ {formatDuration((words / 150) * 60)}
              </span>
              <span className="inline-flex items-center gap-1.5">
                <AudioLines className="size-4 text-muted" /> {engine?.name ?? "No engine"}
              </span>
            </div>
            <div className="mt-6 flex justify-center -space-x-2">
              <Initials name="Narrator" color={NARRATOR_COLOR} size={36} className="ring-2 ring-paper" />
              {analysis.characters.slice(0, 8).map((c) => (
                <Initials key={c.id} name={c.name} color={c.color} size={36} className="ring-2 ring-paper" />
              ))}
            </div>
            <Button variant="brand" size="lg" className="mt-8" disabled={!castFor || !ready} onClick={() => void startProduce()}>
              Start recording
            </Button>
            {!castFor && (
              <p className="mt-3 text-xs text-muted">
                <button type="button" className="cursor-pointer underline" onClick={() => setStep("cast")}>
                  Cast the voices
                </button>{" "}
                first.
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
