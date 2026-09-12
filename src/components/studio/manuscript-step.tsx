"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState, type DragEvent } from "react";
import { AnimatePresence, motion } from "motion/react";
import {
  ArrowRight,
  AudioLines,
  BookOpen,
  BrainCircuit,
  Check,
  FileUp,
  Layers,
  LoaderCircle,
  Settings2,
  Sparkles,
  TriangleAlert,
  Upload,
  WandSparkles,
  X,
} from "lucide-react";
import { Button, Initials, Input, Label, Select, Textarea } from "@/components/ui";
import { extractFile } from "@/lib/client/api";
import { LANGUAGES, detectLanguage, languageName } from "@/lib/engine/lang";
import { TTS_PROVIDERS, type TTSProviderId } from "@/lib/providers";
import { useJobs } from "@/lib/store/jobs";
import { useProject } from "@/lib/store/project";
import { isTTSReady, useSettings } from "@/lib/store/settings";
import { cn, wordCount } from "@/lib/utils";
import { SAMPLES } from "./samples";

const PHASES = [
  { id: "reading", label: "Reading the chapter" },
  { id: "scripting", label: "Scripting every line" },
  { id: "finishing", label: "Building the cast list" },
  { id: "casting", label: "Casting a voice for each character" },
] as const;

function DirectorProgressCard() {
  const { directProgress, directCharacters, cancelDirect } = useJobs();
  const phaseIndex = PHASES.findIndex((p) => p.id === directProgress?.phase);
  const value = directProgress?.value ?? 0;
  return (
    <div className="relative overflow-hidden rounded-3xl bg-night p-6 text-white">
      <div className="absolute -right-16 -top-16 size-56 animate-aurora rounded-full bg-white/10 blur-3xl" />
      <div className="absolute -bottom-20 -left-10 size-56 animate-aurora rounded-full bg-white/[0.06] blur-3xl" style={{ animationDelay: "-6s" }} />
      <div className="relative">
        <div className="flex items-center justify-between">
          <div className="font-mono text-[10.5px] uppercase tracking-[0.2em] text-white/55">The director is at work</div>
          <button type="button" onClick={cancelDirect} className="cursor-pointer rounded-full p-1 text-white/60 hover:bg-white/10 hover:text-white" aria-label="Cancel">
            <X className="size-4" />
          </button>
        </div>
        <div className="mt-4 text-lg font-medium">{directProgress?.label ?? "Warming up"}</div>
        <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-white/10">
          <motion.div
            className="h-full rounded-full bg-white"
            animate={{ width: `${Math.max(4, value * 100)}%` }}
            transition={{ ease: "easeOut", duration: 0.6 }}
          />
        </div>
        <ul className="mt-6 space-y-3">
          {PHASES.map((p, i) => {
            const done = i < phaseIndex;
            const active = i === phaseIndex;
            return (
              <li key={p.id} className={cn("flex items-center gap-3 text-sm", !done && !active && "text-white/40")}>
                <span
                  className={cn(
                    "grid size-6 place-items-center rounded-full border",
                    done ? "border-pine bg-pine text-night" : active ? "border-white/40" : "border-white/15",
                  )}
                >
                  {done ? <Check className="size-3.5" /> : active ? <LoaderCircle className="size-3.5 animate-spin" /> : null}
                </span>
                {p.label}
              </li>
            );
          })}
        </ul>
        {directCharacters.length > 0 && (
          <div className="mt-6 border-t border-white/10 pt-5">
            <div className="font-mono text-[10.5px] uppercase tracking-[0.2em] text-white/55">Characters found</div>
            <div className="mt-3 flex flex-wrap gap-2">
              <AnimatePresence>
                {directCharacters.map((c, i) => (
                  <motion.span
                    key={c.id}
                    initial={{ opacity: 0, scale: 0.8, y: 6 }}
                    animate={{ opacity: 1, scale: 1, y: 0 }}
                    transition={{ delay: i * 0.08 }}
                    className="inline-flex items-center gap-2 rounded-full bg-white/10 py-1 pl-1 pr-3 text-[13px]"
                  >
                    <Initials name={c.name} color={c.color} size={22} />
                    {c.name}
                  </motion.span>
                ))}
              </AnimatePresence>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

type VoiceState = "checking" | "ready" | "waking" | "off";

const VOICE_STATUS: Record<VoiceState, { label: string; dot: string; tone: string }> = {
  checking: { label: "Checking", dot: "bg-muted", tone: "bg-paper-2 text-muted" },
  ready: { label: "Ready", dot: "bg-pine", tone: "bg-pine/10 text-pine" },
  waking: { label: "Warming up", dot: "bg-tide", tone: "bg-tide/10 text-tide" },
  off: { label: "Backup voices", dot: "bg-ember", tone: "bg-ember/10 text-ember" },
};

function useVoiceStatus(active: boolean) {
  const [state, setState] = useState<VoiceState>("checking");

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    const check = () => {
      fetch("/api/voices/status")
        .then((r) => (r.ok ? r.json() : { state: "off" }))
        .then((d: { state?: VoiceState }) => {
          if (!cancelled) setState(d.state ?? "off");
        })
        .catch(() => {
          if (!cancelled) setState("waking");
        });
    };
    check();
    const timer = setInterval(check, 30000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [active]);

  return state;
}

export function ManuscriptStep() {
  const { title, text, languageHint, setDraft, analysis, setStep } = useProject();
  const { activeTTS, setActiveTTS, tts, brainReady } = useSettings();
  const { directStatus, directError, startDirect } = useJobs();
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const words = wordCount(text);
  const minutes = Math.max(1, Math.round(words / 150));
  const detected = useMemo(() => (text.trim().length > 40 ? detectLanguage(text) : null), [text]);
  const running = directStatus === "running";
  const tooLong = text.length > 80000;
  const voiceState = useVoiceStatus(activeTTS === "nigix");
  const voiceStatus = VOICE_STATUS[voiceState];

  async function handleFile(file: File) {
    setUploadError(null);
    setUploading(true);
    try {
      const { text: body, title: name } = await extractFile(file);
      setDraft({ text: body, title: title || name });
    } catch (e) {
      setUploadError(e instanceof Error ? e.message : String(e));
    } finally {
      setUploading(false);
    }
  }

  function onDrop(e: DragEvent) {
    e.preventDefault();
    setDragging(false);
    const file = e.dataTransfer.files?.[0];
    if (file) void handleFile(file);
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
      <div
        className={cn("card relative overflow-hidden p-0 transition", running && "opacity-60")}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
      >
        <div className="flex flex-wrap items-center gap-2 border-b border-line/70 px-5 py-3">
          <Input
            value={title}
            onChange={(e) => setDraft({ title: e.target.value })}
            placeholder="Chapter title"
            className="h-9 max-w-xs border-transparent bg-transparent px-2 font-medium focus:bg-white"
            disabled={running}
          />
          <div className="ml-auto flex flex-wrap items-center gap-1.5">
            <input
              ref={fileRef}
              type="file"
              hidden
              accept=".txt,.md,.markdown,.docx,.pdf,.html,.htm,text/plain"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) void handleFile(f);
                e.target.value = "";
              }}
            />
            <Button variant="outline" size="sm" onClick={() => fileRef.current?.click()} disabled={running || uploading}>
              {uploading ? <LoaderCircle className="size-3.5 animate-spin" /> : <Upload className="size-3.5" />}
              Upload file
            </Button>
            {SAMPLES.map((s) => (
              <Button
                key={s.id}
                variant="soft"
                size="sm"
                disabled={running}
                onClick={() => setDraft({ text: s.text, title: s.title, languageHint: "auto" })}
                title={`Load sample: ${s.title}`}
              >
                <Sparkles className="size-3.5 text-tide" />
                {s.label}
              </Button>
            ))}
          </div>
        </div>

        <Textarea
          value={text}
          onChange={(e) => setDraft({ text: e.target.value })}
          disabled={running}
          placeholder={"Paste one chapter of your novel here…\n\n“Dialogue in quotes,” she said, “helps the director hear who is speaking.”"}
          className="min-h-[520px] resize-y rounded-none border-0 bg-transparent px-7 py-6 text-[1.05rem] font-medium leading-[1.85] text-ink focus:ring-0"
        />

        <div className="flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-line/70 px-6 py-3 text-xs text-muted">
          <span className="tabular-nums">{words.toLocaleString()} words</span>
          <span className="tabular-nums">{text.length.toLocaleString()} characters</span>
          <span className="tabular-nums">≈ {minutes} min of audio</span>
          {detected && (
            <span>
              Detected <span className="font-medium text-ink-2">{languageName(detected)}</span>
            </span>
          )}
          {text && !running && (
            <button type="button" onClick={() => setDraft({ text: "" })} className="ml-auto cursor-pointer hover:text-ink">
              Clear
            </button>
          )}
        </div>

        <AnimatePresence>
          {dragging && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="pointer-events-none absolute inset-3 grid place-items-center rounded-2xl border-2 border-dashed border-tide/60 bg-paper/90 backdrop-blur"
            >
              <div className="text-center">
                <FileUp className="mx-auto size-8 text-tide" />
                <div className="mt-3 font-display text-xl font-semibold text-ink">Drop your chapter</div>
                <div className="mt-1 text-xs text-muted">.txt · .md · .docx · .pdf · .html</div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <div className="space-y-4">
        {running ? (
          <DirectorProgressCard />
        ) : (
          <div className="card p-6">
            <div className="flex items-center gap-2">
              <span className="grid size-9 place-items-center rounded-xl bg-brand text-white">
                <WandSparkles className="size-4" />
              </span>
              <div>
                <div className="font-semibold text-ink">Direct this chapter</div>
                <div className="text-xs text-muted">The director reads, the voices perform, the studio assembles</div>
              </div>
            </div>

            <ol className="relative mt-6 space-y-4">
              <span aria-hidden className="absolute bottom-8 left-[15px] top-8 w-px bg-line" />
              <li className="relative flex gap-3">
                <span className="relative z-10 grid size-8 shrink-0 place-items-center rounded-full bg-ink text-white">
                  <BrainCircuit className="size-4" />
                </span>
                <div className="min-w-0 flex-1 pt-1">
                  <div className="flex items-center gap-2 text-[13px] font-semibold text-ink">
                    1 · Director
                    <span
                      className={cn(
                        "inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10.5px] font-medium",
                        brainReady ? "bg-pine/10 text-pine" : "bg-ember/10 text-ember",
                      )}
                    >
                      <span className={cn("size-1.5 rounded-full", brainReady ? "bg-pine" : "bg-ember")} />
                      {brainReady ? "Ready" : "Resting"}
                    </span>
                  </div>
                  <p className="mt-0.5 text-[11.5px] leading-snug text-muted">
                    Reads the chapter, finds every character, scripts each line and decides which voice plays whom.
                  </p>
                </div>
              </li>
              <li className="relative flex gap-3">
                <span className="relative z-10 grid size-8 shrink-0 place-items-center rounded-full border border-line bg-white text-ink">
                  <AudioLines className="size-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="mb-1 flex items-center gap-2">
                    <Label htmlFor="tts" className="mb-0">2 · Voices</Label>
                    {activeTTS === "nigix" && (
                      <span
                        className={cn(
                          "inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10.5px] font-medium",
                          voiceStatus.tone,
                        )}
                      >
                        <span className={cn("size-1.5 rounded-full", voiceStatus.dot)} />
                        {voiceStatus.label}
                      </span>
                    )}
                  </div>
                  <Select id="tts" value={activeTTS} onChange={(e) => setActiveTTS(e.target.value as TTSProviderId)}>
                    {TTS_PROVIDERS.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                        {p.free ? " (free)" : ""}
                        {isTTSReady(p.id, tts[p.id]) ? "" : " — add key in Settings"}
                      </option>
                    ))}
                  </Select>
                  <p className="mt-1.5 text-[11.5px] leading-snug text-muted">
                    {activeTTS === "nigix" && voiceState === "waking"
                      ? "The voices are warming up. Recording will wait for them, so you can start anyway."
                      : "Performs every line in the voice the director chose."}
                  </p>
                </div>
              </li>
              <li className="relative flex gap-3">
                <span className="relative z-10 grid size-8 shrink-0 place-items-center rounded-full border border-line bg-white text-ink">
                  <Layers className="size-4" />
                </span>
                <div className="min-w-0 flex-1 pt-1">
                  <div className="text-[13px] font-semibold text-ink">3 · Studio</div>
                  <p className="mt-0.5 text-[11.5px] leading-snug text-muted">
                    Trims, balances and stitches every line into one audiobook.
                  </p>
                </div>
              </li>
            </ol>

            <div className="mt-6">
              <div className="flex items-center justify-between">
                <Label htmlFor="lang">Language</Label>
                <Link href="/settings" className="mb-1.5 inline-flex items-center gap-1 text-[11px] text-muted hover:text-ink">
                  <Settings2 className="size-3" /> Voice settings
                </Link>
              </div>
              <Select id="lang" value={languageHint} onChange={(e) => setDraft({ languageHint: e.target.value })}>
                <option value="auto">Auto-detect{detected ? ` (${languageName(detected)})` : ""}</option>
                {LANGUAGES.map((l) => (
                  <option key={l.code} value={l.code}>
                    {l.native === l.name ? l.name : `${l.native} — ${l.name}`}
                  </option>
                ))}
              </Select>
            </div>

            {!brainReady && (
              <div className="mt-4 flex items-start gap-2 rounded-xl border border-tide/25 bg-tide/5 p-3 text-xs text-ink-2">
                <TriangleAlert className="mt-0.5 size-3.5 shrink-0 text-tide" />
                <span>The director is resting right now, so chapters get a quick read with simpler characters.</span>
              </div>
            )}
            {tooLong && (
              <div className="mt-4 flex items-start gap-2 rounded-xl border border-tide/25 bg-tide/5 p-3 text-xs text-ink-2">
                <TriangleAlert className="mt-0.5 size-3.5 shrink-0 text-tide" />
                This looks longer than one chapter. It will work, but directing one chapter at a time gives a sharper cast.
              </div>
            )}
            {(directError || uploadError) && (
              <div className="mt-4 rounded-xl border border-ember/25 bg-ember/5 p-3 text-xs leading-relaxed text-ember">
                {directError || uploadError}
              </div>
            )}

            <Button
              variant="brand"
              size="lg"
              className="group mt-6 w-full"
              disabled={!text.trim()}
              onClick={() => void startDirect()}
            >
              <WandSparkles className="size-4" />
              {analysis ? "Re-direct chapter" : "Direct this chapter"}
              <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
            </Button>
            {analysis && (
              <Button variant="ghost" size="sm" className="mt-2 w-full" onClick={() => setStep("cast")}>
                Back to the current cast
              </Button>
            )}
          </div>
        )}

        <div className="card p-6">
          <div className="flex items-center gap-2 text-sm font-semibold text-ink">
            <BookOpen className="size-4 text-iris" /> Director&apos;s notes
          </div>
          <ul className="mt-3 space-y-2.5 text-[13px] leading-relaxed text-muted">
            <li>The director does the thinking: characters, script, emotions and who gets which voice.</li>
            <li>The voice engine only speaks. Switch it any time and the director recasts from its catalogue.</li>
            <li>One chapter at a time keeps every character&apos;s voice consistent.</li>
          </ul>
        </div>
      </div>
    </div>
  );
}
