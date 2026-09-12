"use client";

import { useMemo, useState } from "react";
import { ArrowRight, ArrowUpToLine, LoaderCircle, Play, Search, Square, Trash2 } from "lucide-react";
import { Bars, Button, Initials, Input } from "@/components/ui";
import { useJobs } from "@/lib/store/jobs";
import { useProject } from "@/lib/store/project";
import { isTTSReady, useSettings } from "@/lib/store/settings";
import type { Emotion, Segment } from "@/lib/types";
import { EMOTIONS, NARRATOR_ID } from "@/lib/types";
import { NARRATOR_COLOR, cn, formatDuration, wordCount } from "@/lib/utils";

const EMOTION_TINT: Partial<Record<Emotion, string>> = {
  happy: "#B45309",
  sad: "#1D4ED8",
  angry: "#B91C1C",
  fearful: "#6D28D9",
  surprised: "#BE185D",
  excited: "#0E7490",
  whisper: "#4338CA",
  calm: "#0F766E",
  serious: "#334155",
  sarcastic: "#047857",
  tender: "#9D174D",
  shouting: "#991B1B",
};

function Row({ seg, index }: { seg: Segment; index: number }) {
  const { analysis, updateSegment, mergeSegmentUp, deleteSegment } = useProject();
  const { previewKey, previewState, preview, stopPreview } = useJobs();
  if (!analysis) return null;
  const character = analysis.characters.find((c) => c.id === seg.speaker);
  const color = character?.color ?? NARRATOR_COLOR;
  const name = character?.name ?? "Narrator";
  const key = `line:${seg.id}`;
  const mine = previewKey === key;
  const tint = EMOTION_TINT[seg.emotion];
  const isNarration = seg.speaker === NARRATOR_ID;

  return (
    <div
      className={cn(
        "group grid grid-cols-[minmax(0,1fr)_auto] items-start gap-x-2 gap-y-1 rounded-xl px-3 py-2 transition-colors [contain-intrinsic-size:auto_52px] [content-visibility:auto] hover:bg-white sm:grid-cols-[150px_minmax(0,1fr)_auto] sm:gap-3",
        seg.para && index > 0 && "mt-3",
        mine && "bg-white shadow-sm",
      )}
    >
      <div className="relative col-start-1 row-start-1">
        <span
          className="flex h-7 items-center gap-1.5 truncate rounded-full py-0.5 pl-0.5 pr-2.5 text-[12px] font-medium"
          style={{ background: `color-mix(in oklab, ${color} 12%, white)`, color: `color-mix(in oklab, ${color} 80%, black)` }}
        >
          <Initials name={name} color={color} size={24} />
          <span className="truncate">{name}</span>
        </span>
        <select
          aria-label="Speaker"
          value={seg.speaker}
          onChange={(e) => updateSegment(seg.id, { speaker: e.target.value })}
          className="absolute inset-0 cursor-pointer opacity-0"
        >
          <option value={NARRATOR_ID}>Narrator</option>
          {analysis.characters.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>

      <textarea
        key={`${seg.id}:${seg.text}`}
        defaultValue={seg.text}
        rows={Math.max(1, Math.ceil(seg.text.length / 95))}
        onBlur={(e) => {
          const v = e.target.value.trim();
          if (v && v !== seg.text) updateSegment(seg.id, { text: v });
        }}
        className={cn(
          "col-span-2 row-start-2 w-full resize-none rounded-lg bg-transparent px-1 py-0.5 text-[15px] leading-relaxed outline-none [field-sizing:content] focus:bg-paper-2 sm:col-span-1 sm:col-start-2 sm:row-start-1",
          isNarration ? "text-ink-2" : "font-semibold text-ink",
        )}
      />

      <div className="col-start-2 row-start-1 flex items-center justify-end gap-1 sm:col-start-3">
        <div className="relative">
          <span
            className="inline-flex h-7 items-center rounded-full border border-line px-2.5 text-[11px] capitalize text-ink-2"
            style={tint ? { borderColor: `${tint}55`, background: `${tint}14`, color: tint } : undefined}
          >
            {seg.emotion}
          </span>
          <select
            aria-label="Emotion"
            value={seg.emotion}
            onChange={(e) => updateSegment(seg.id, { emotion: e.target.value as Emotion })}
            className="absolute inset-0 cursor-pointer opacity-0"
          >
            {EMOTIONS.map((e) => (
              <option key={e} value={e}>
                {e}
              </option>
            ))}
          </select>
        </div>
        <button
          type="button"
          onClick={() => (mine ? stopPreview() : void preview(key, seg))}
          className="grid size-7 cursor-pointer place-items-center rounded-full text-ink-2 hover:bg-paper-2"
          aria-label="Preview line"
        >
          {mine && previewState === "loading" ? (
            <LoaderCircle className="size-3.5 animate-spin" />
          ) : mine ? (
            <Bars color={color} count={3} height={12} />
          ) : (
            <Play className="size-3.5 fill-current" />
          )}
        </button>
        <div className="flex transition-opacity sm:opacity-0 sm:group-hover:opacity-100">
          {index > 0 && (
            <button
              type="button"
              onClick={() => mergeSegmentUp(seg.id)}
              className="grid size-7 cursor-pointer place-items-center rounded-full text-muted hover:bg-paper-2 hover:text-ink"
              title="Merge into the line above"
            >
              <ArrowUpToLine className="size-3.5" />
            </button>
          )}
          <button
            type="button"
            onClick={() => deleteSegment(seg.id)}
            className="grid size-7 cursor-pointer place-items-center rounded-full text-muted hover:bg-ember/10 hover:text-ember"
            title="Remove line"
          >
            <Trash2 className="size-3.5" />
          </button>
        </div>
        {mine && previewState === "playing" && (
          <button type="button" onClick={stopPreview} className="grid size-7 cursor-pointer place-items-center rounded-full text-ink hover:bg-paper-2">
            <Square className="size-3 fill-current" />
          </button>
        )}
      </div>
    </div>
  );
}

export function ScriptStep() {
  const { analysis, segments, castFor } = useProject();
  const { tts } = useSettings();
  const startProduce = useJobs((s) => s.startProduce);
  const [filter, setFilter] = useState<string>("all");
  const [query, setQuery] = useState("");

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return segments
      .map((seg, index) => ({ seg, index }))
      .filter(({ seg }) => (filter === "all" || seg.speaker === filter) && (!q || seg.text.toLowerCase().includes(q)));
  }, [segments, filter, query]);

  if (!analysis) return null;
  const words = segments.reduce((n, s) => n + wordCount(s.text), 0);
  const narratorLines = segments.filter((s) => s.speaker === NARRATOR_ID).length;
  const canProduce = Boolean(castFor) && (castFor ? isTTSReady(castFor.provider, tts[castFor.provider]) : false);

  const chips = [
    { id: "all", name: "Everyone", color: "#0A0A0A", count: segments.length },
    { id: NARRATOR_ID, name: "Narrator", color: NARRATOR_COLOR, count: narratorLines },
    ...analysis.characters.map((c) => ({ id: c.id, name: c.name, color: c.color, count: c.lineCount })),
  ];

  return (
    <div className="space-y-4">
      <div className="card flex flex-col gap-3 p-4 md:flex-row md:items-center">
        <div className="flex flex-1 flex-wrap gap-1.5">
          {chips.map((c) => (
            <button
              key={c.id}
              type="button"
              onClick={() => setFilter(c.id)}
              className={cn(
                "inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-3 py-1 text-[12.5px] transition",
                filter === c.id ? "border-ink bg-ink text-paper" : "border-line bg-white/60 text-ink-2 hover:bg-white",
              )}
            >
              <span className="size-2 rounded-full" style={{ background: c.color }} />
              {c.name}
              <span className={cn("tabular-nums", filter === c.id ? "text-paper/60" : "text-muted")}>{c.count}</span>
            </button>
          ))}
        </div>
        <div className="relative md:w-64">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-3.5 -translate-y-1/2 text-muted" />
          <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search lines" className="h-9 pl-8" />
        </div>
      </div>

      <div className="card p-3 sm:p-4">
        {visible.length === 0 ? (
          <div className="py-16 text-center text-sm text-muted">No lines match.</div>
        ) : (
          visible.map(({ seg, index }) => <Row key={seg.id} seg={seg} index={index} />)
        )}
      </div>

      <div className="sticky bottom-4 z-20 flex flex-col items-stretch justify-between gap-3 rounded-2xl border border-line bg-paper/85 p-3 pl-5 shadow-[0_20px_50px_-24px_rgba(0,0,0,0.25)] backdrop-blur-xl sm:flex-row sm:items-center">
        <div className="text-sm text-ink-2">
          <span className="font-medium text-ink">{segments.length}</span> lines ·{" "}
          <span className="font-medium text-ink">{words.toLocaleString()}</span> words · about{" "}
          <span className="font-medium text-ink">{formatDuration((words / 150) * 60)}</span> of audio
          <span className="ml-2 hidden text-muted lg:inline">Click a speaker or emotion to change it.</span>
        </div>
        <Button variant="brand" disabled={!canProduce} onClick={() => void startProduce()} className="group">
          Produce audiobook
          <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
        </Button>
      </div>
    </div>
  );
}
