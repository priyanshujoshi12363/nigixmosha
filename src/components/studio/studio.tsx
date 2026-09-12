"use client";

import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import Link from "next/link";
import { Check, CloudCheck, CloudOff, Drama, FileText, Headphones, LoaderCircle, RotateCcw, ScrollText, X } from "lucide-react";
import { Button } from "@/components/ui";
import { saveProjectNow } from "@/lib/client/library-sync";
import { useJobs } from "@/lib/store/jobs";
import { useProject, type Step } from "@/lib/store/project";
import { useSettings } from "@/lib/store/settings";
import { cn } from "@/lib/utils";
import { CastStep } from "./cast-step";
import { ListenStep } from "./listen-step";
import { ManuscriptStep } from "./manuscript-step";
import { ScriptStep } from "./script-step";

const STEPS: { id: Step; label: string; icon: typeof FileText }[] = [
  { id: "manuscript", label: "Manuscript", icon: FileText },
  { id: "cast", label: "Cast & voices", icon: Drama },
  { id: "script", label: "Script", icon: ScrollText },
  { id: "listen", label: "Listen", icon: Headphones },
];

function Stepper() {
  const { step, setStep, analysis, output } = useProject();
  const current = STEPS.findIndex((s) => s.id === step);
  return (
    <div className="flex items-center gap-1 overflow-x-auto pb-1 scrollbar-thin">
      {STEPS.map((s, i) => {
        const enabled = s.id === "manuscript" || Boolean(analysis);
        const active = s.id === step;
        const complete = (i < current && Boolean(analysis)) || (s.id === "listen" && Boolean(output) && !active);
        return (
          <div key={s.id} className="flex shrink-0 items-center gap-1">
            <button
              type="button"
              disabled={!enabled}
              onClick={() => setStep(s.id)}
              className={cn(
                "relative flex cursor-pointer items-center gap-2 rounded-full py-1.5 pl-1.5 pr-4 text-[13px] font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-40",
                active ? "text-paper" : "text-ink-2 hover:bg-ink/5",
              )}
            >
              {active && (
                <motion.span
                  layoutId="studio-step"
                  className="absolute inset-0 rounded-full bg-ink"
                  transition={{ type: "spring", stiffness: 420, damping: 34 }}
                />
              )}
              <span
                className={cn(
                  "relative grid size-6 place-items-center rounded-full text-[11px]",
                  active ? "bg-white/15" : complete ? "bg-pine text-white" : "bg-paper-2",
                )}
              >
                {complete ? <Check className="size-3.5" /> : <s.icon className="size-3.5" />}
              </span>
              <span className="relative">{s.label}</span>
            </button>
            {i < STEPS.length - 1 && <span className="mx-1 h-px w-6 bg-line" />}
          </div>
        );
      })}
    </div>
  );
}

function SaveIndicator() {
  const { saveState, saveError, analysis } = useProject();
  const db = useSettings((s) => s.storage.db);
  if (!db || !analysis) return null;
  return (
    <div className="mt-2 flex h-5 items-center gap-1.5 text-xs text-muted">
      {saveState === "saving" && (
        <>
          <LoaderCircle className="size-3.5 animate-spin" /> Saving to your history…
        </>
      )}
      {saveState === "saved" && (
        <>
          <CloudCheck className="size-3.5 text-pine" /> Saved to your{" "}
          <Link href="/history" className="underline underline-offset-2 hover:text-ink">
            history
          </Link>
        </>
      )}
      {saveState === "error" && (
        <button
          type="button"
          onClick={() => void saveProjectNow()}
          title={saveError ?? undefined}
          className="inline-flex cursor-pointer items-center gap-1.5 text-ember hover:underline"
        >
          <CloudOff className="size-3.5" /> Not saved — retry
        </button>
      )}
    </div>
  );
}

function PreviewToast() {
  const { previewError, clearPreviewError } = useJobs();
  useEffect(() => {
    if (!previewError) return;
    const t = setTimeout(clearPreviewError, 7000);
    return () => clearTimeout(t);
  }, [previewError, clearPreviewError]);
  return (
    <AnimatePresence>
      {previewError && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 20 }}
          className="fixed bottom-6 left-1/2 z-50 flex w-[min(92vw,520px)] -translate-x-1/2 items-start gap-3 rounded-2xl bg-night px-4 py-3 text-sm text-white shadow-2xl"
        >
          <span className="mt-1 size-2 shrink-0 rounded-full bg-ember" />
          <span className="flex-1 leading-relaxed">{previewError}</span>
          <button type="button" onClick={clearPreviewError} className="cursor-pointer text-white/60 hover:text-white" aria-label="Dismiss">
            <X className="size-4" />
          </button>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

export function Studio() {
  const hydrated = useSettings((s) => s.hydrated);
  const { step, analysis, title, reset } = useProject();
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    if (!confirming) return;
    const t = setTimeout(() => setConfirming(false), 3000);
    return () => clearTimeout(t);
  }, [confirming]);

  if (!hydrated) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
        <div className="h-8 w-64 animate-pulse rounded-full bg-paper-2" />
        <div className="mt-8 h-[520px] animate-pulse rounded-3xl bg-paper-2" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl px-4 pb-24 pt-8 sm:px-6">
      <div className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
        <div className="min-w-0">
          <div className="font-mono text-[11px] uppercase tracking-[0.2em] text-muted">Studio</div>
          <h1 className="mt-1 line-clamp-2 font-display text-3xl font-semibold leading-tight tracking-[-0.035em] text-ink sm:text-4xl">
            {analysis?.title || title || <span className="text-muted">A new chapter</span>}
          </h1>
          <SaveIndicator />
        </div>
        <div className="flex shrink-0 items-center gap-3">
          <Stepper />
          <Button
            variant={confirming ? "danger" : "ghost"}
            size="sm"
            onClick={() => {
              if (!confirming) return setConfirming(true);
              useJobs.getState().cancelDirect();
              useJobs.getState().cancelProduce();
              reset();
              setConfirming(false);
            }}
            title="Start a new project"
          >
            <RotateCcw className="size-3.5" />
            {confirming ? "Clear everything?" : "New"}
          </Button>
        </div>
      </div>

      <div className="mt-8">
        <AnimatePresence mode="wait">
          <motion.div
            key={step}
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            transition={{ duration: 0.35, ease: [0.2, 0.7, 0.2, 1] }}
          >
            {step === "manuscript" && <ManuscriptStep />}
            {step === "cast" && analysis && <CastStep />}
            {step === "script" && analysis && <ScriptStep />}
            {step === "listen" && analysis && <ListenStep />}
            {step !== "manuscript" && !analysis && <ManuscriptStep />}
          </motion.div>
        </AnimatePresence>
      </div>
      <PreviewToast />
    </div>
  );
}
