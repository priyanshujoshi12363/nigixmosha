"use client";

import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { motion } from "motion/react";
import {
  BookOpenText,
  Clock,
  Download,
  History,
  LoaderCircle,
  Pause,
  Play,
  Plus,
  Search,
  Trash2,
  TriangleAlert,
  Users,
} from "lucide-react";
import { Bars, Button, ButtonLink, Initials, Input } from "@/components/ui";
import { deleteProject, getProject, listProjects } from "@/lib/client/library";
import { languageName } from "@/lib/engine/lang";
import type { ProjectSummary } from "@/lib/library-types";
import { useJobs } from "@/lib/store/jobs";
import { useProject } from "@/lib/store/project";
import { useSettings } from "@/lib/store/settings";
import { cn, formatDuration } from "@/lib/utils";

type Filter = "all" | "recorded" | "drafts";

let previewAudio: HTMLAudioElement | null = null;

function audioElement() {
  previewAudio ??= new Audio();
  return previewAudio;
}

function stopAudio() {
  previewAudio?.pause();
}

function timeAgo(iso: string) {
  const t = Date.parse(iso);
  if (!t) return "";
  const s = Math.max(1, Math.round((Date.now() - t) / 1000));
  if (s < 60) return "just now";
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  const d = Math.round(h / 24);
  if (d < 30) return `${d} d ago`;
  return new Date(t).toLocaleDateString();
}

function cover(item: ProjectSummary) {
  const colors = item.characters.map((c) => c.color).slice(0, 3);
  const [a, b, c] = [colors[0] ?? "#0D9488", colors[1] ?? "#1E40AF", colors[2] ?? "#4338CA"];
  return `radial-gradient(circle at 20% 20%, ${a}, transparent 55%), radial-gradient(circle at 80% 30%, ${b}, transparent 55%), radial-gradient(circle at 50% 100%, ${c}, transparent 60%), #0a0a0a`;
}

export function HistoryView() {
  const router = useRouter();
  const hydrated = useSettings((s) => s.hydrated);
  const loadProject = useProject((s) => s.loadProject);
  const [items, setItems] = useState<ProjectSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [opening, setOpening] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<string | null>(null);
  const [playing, setPlaying] = useState<string | null>(null);
  const [progress, setProgress] = useState(0);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<Filter>("all");

  useEffect(() => {
    if (!hydrated) return;
    let cancelled = false;
    listProjects()
      .then((p) => {
        if (!cancelled) setItems(p);
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message);
      });
    return () => {
      cancelled = true;
    };
  }, [hydrated]);

  useEffect(() => {
    if (!confirm) return;
    const t = setTimeout(() => setConfirm(null), 3000);
    return () => clearTimeout(t);
  }, [confirm]);

  useEffect(() => stopAudio, []);

  const counts = useMemo(
    () => ({
      all: items?.length ?? 0,
      recorded: items?.filter((i) => i.audio).length ?? 0,
      drafts: items?.filter((i) => !i.audio).length ?? 0,
    }),
    [items],
  );

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (items ?? []).filter((i) => {
      if (filter === "recorded" && !i.audio) return false;
      if (filter === "drafts" && i.audio) return false;
      if (!q) return true;
      return (
        i.title.toLowerCase().includes(q) ||
        i.characters.some((c) => c.name.toLowerCase().includes(q)) ||
        (i.language ? languageName(i.language).toLowerCase().includes(q) : false)
      );
    });
  }, [items, filter, query]);

  function togglePlay(item: ProjectSummary) {
    if (!item.audio) return;
    const audio = audioElement();
    if (playing === item.id) {
      audio.pause();
      setPlaying(null);
      return;
    }
    const remote = item.audio;
    audio.src = remote.streamUrl;
    audio.ontimeupdate = () => setProgress(audio.duration ? audio.currentTime / audio.duration : 0);
    audio.onended = () => setPlaying(null);
    audio.onerror = () => {
      if (audio.src !== remote.url) {
        audio.src = remote.url;
        void audio.play();
      }
    };
    setProgress(0);
    setPlaying(item.id);
    void audio.play();
  }

  async function open(id: string) {
    setOpening(id);
    try {
      const record = await getProject(id);
      stopAudio();
      useJobs.getState().stopPreview();
      loadProject(record);
      router.push("/studio");
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setOpening(null);
    }
  }

  async function remove(id: string) {
    if (confirm !== id) {
      setConfirm(id);
      return;
    }
    setConfirm(null);
    if (playing === id) {
      stopAudio();
      setPlaying(null);
    }
    const previous = items;
    setItems((xs) => xs?.filter((x) => x.id !== id) ?? null);
    try {
      await deleteProject(id);
      if (useProject.getState().projectId === id) useProject.getState().setProjectId(null, null);
    } catch (e) {
      setItems(previous);
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  const FILTERS: { id: Filter; label: string }[] = [
    { id: "all", label: "All" },
    { id: "recorded", label: "Recorded" },
    { id: "drafts", label: "Drafts" },
  ];

  return (
    <div className="mx-auto max-w-7xl px-4 pb-24 pt-10 sm:px-6">
      <div className="flex flex-col gap-5 md:flex-row md:items-end md:justify-between">
        <div className="max-w-2xl">
          <div className="flex items-center gap-2 font-mono text-[11px] uppercase tracking-[0.2em] text-muted">
            <History className="size-3.5" /> History
          </div>
          <h1 className="mt-1 font-display text-4xl font-semibold leading-tight tracking-[-0.035em] text-ink">Your audiobooks</h1>
          <p className="mt-3 text-[15px] leading-relaxed text-muted">
            Every chapter you direct is saved to your account automatically, with its cast, script and recording.
          </p>
        </div>
        <ButtonLink href="/studio" variant="brand">
          <Plus className="size-4" /> New chapter
        </ButtonLink>
      </div>

      {items && items.length > 0 && (
        <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex rounded-full border border-line bg-white/60 p-1">
            {FILTERS.map((f) => (
              <button
                key={f.id}
                type="button"
                onClick={() => setFilter(f.id)}
                className={cn(
                  "cursor-pointer rounded-full px-3.5 py-1.5 text-[13px] font-medium transition",
                  filter === f.id ? "bg-ink text-paper" : "text-ink-2 hover:text-ink",
                )}
              >
                {f.label}
                <span className={cn("ml-1.5 tabular-nums", filter === f.id ? "text-paper/60" : "text-muted")}>{counts[f.id]}</span>
              </button>
            ))}
          </div>
          <div className="relative sm:w-72">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-3.5 -translate-y-1/2 text-muted" />
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search titles, characters, languages"
              className="h-9 pl-8"
            />
          </div>
        </div>
      )}

      {error && (
        <div className="mt-8 flex items-start gap-3 rounded-2xl border border-ember/25 bg-ember/5 p-4 text-sm text-ember">
          <TriangleAlert className="mt-0.5 size-4 shrink-0" /> {error}
        </div>
      )}

      {!items && !error && (
        <div className="mt-10 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 3 }, (_, i) => (
            <div key={i} className="h-80 animate-pulse rounded-3xl bg-paper-2" />
          ))}
        </div>
      )}

      {items && items.length === 0 && (
        <div className="card mt-10 grid place-items-center px-6 py-20 text-center">
          <span className="grid size-14 place-items-center rounded-2xl bg-brand text-white shadow-lg">
            <BookOpenText className="size-6" />
          </span>
          <h2 className="mt-6 font-display text-2xl font-semibold text-ink">No audiobooks yet</h2>
          <p className="mt-2 max-w-md text-[15px] text-muted">
            Direct a chapter in the Studio and it will appear here, ready to replay or reopen.
          </p>
          <ButtonLink href="/studio" className="mt-6">
            Open the Studio
          </ButtonLink>
        </div>
      )}

      {items && items.length > 0 && visible.length === 0 && (
        <div className="mt-10 rounded-3xl border border-dashed border-line py-16 text-center text-sm text-muted">
          Nothing matches “{query}”.
        </div>
      )}

      {visible.length > 0 && (
        <div className="mt-6 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {visible.map((item, i) => {
            const isPlaying = playing === item.id;
            return (
              <motion.div
                key={item.id}
                initial={{ opacity: 0, y: 18 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: Math.min(i, 8) * 0.05, duration: 0.45 }}
                className="card flex flex-col overflow-hidden p-0"
              >
                <div className="relative aspect-[16/9] overflow-hidden" style={{ background: cover(item) }}>
                  <div className="absolute inset-0 opacity-25 mix-blend-overlay dot-grid" />
                  <span
                    className={cn(
                      "absolute left-4 top-4 rounded-full px-2.5 py-0.5 text-[11px] font-medium backdrop-blur",
                      item.audio ? "bg-white/20 text-white" : "bg-black/30 text-white/80",
                    )}
                  >
                    {item.audio ? "Recorded" : "Draft"}
                  </span>
                  <div className="absolute inset-x-0 bottom-0 p-5 text-white">
                    <div className="font-mono text-[10px] uppercase tracking-[0.2em] text-white/70">
                      {item.language ? languageName(item.language) : "Unknown language"}
                    </div>
                    <div className="mt-1 line-clamp-2 font-display text-xl font-semibold leading-[1.15] tracking-[-0.02em]">{item.title}</div>
                  </div>
                  {item.audio && (
                    <button
                      type="button"
                      onClick={() => togglePlay(item)}
                      aria-label={isPlaying ? "Pause" : "Play"}
                      className="absolute right-4 top-4 grid size-11 cursor-pointer place-items-center rounded-full bg-white text-night shadow-xl transition hover:scale-105"
                    >
                      {isPlaying ? <Pause className="size-4 fill-current" /> : <Play className="ml-0.5 size-4 fill-current" />}
                    </button>
                  )}
                  {isPlaying && (
                    <>
                      <span className="absolute right-[4.25rem] top-7">
                        <Bars color="white" count={5} height={16} />
                      </span>
                      <span className="absolute inset-x-0 bottom-0 h-1 bg-white/20">
                        <span className="block h-full bg-white" style={{ width: `${progress * 100}%` }} />
                      </span>
                    </>
                  )}
                </div>

                <div className="flex flex-1 flex-col p-5">
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex -space-x-2">
                      {item.characters.slice(0, 5).map((c) => (
                        <Initials key={c.name} name={c.name} color={c.color} size={28} className="ring-2 ring-white" />
                      ))}
                      {item.characters.length > 5 && (
                        <span className="grid size-7 place-items-center rounded-full bg-paper-2 text-[10px] text-ink-2 ring-2 ring-white">
                          +{item.characters.length - 5}
                        </span>
                      )}
                    </div>
                    <span className="text-xs text-muted" title={new Date(item.updatedAt).toLocaleString()}>
                      {timeAgo(item.updatedAt)}
                    </span>
                  </div>
                  <div className="mt-4 flex flex-wrap gap-x-4 gap-y-1 text-[13px] text-ink-2">
                    <span className="inline-flex items-center gap-1.5">
                      <Clock className="size-3.5 text-muted" />
                      {item.audio ? formatDuration(item.audio.duration) : "Not recorded yet"}
                    </span>
                    <span className="inline-flex items-center gap-1.5">
                      <Users className="size-3.5 text-muted" /> {item.characters.length + 1} voices
                    </span>
                    <span>{item.segments} lines</span>
                    {item.words > 0 && <span>{item.words.toLocaleString()} words</span>}
                  </div>
                  <div className="mt-auto flex items-center gap-2 pt-5">
                    <Button size="sm" onClick={() => void open(item.id)} disabled={opening === item.id}>
                      {opening === item.id && <LoaderCircle className="size-3.5 animate-spin" />}
                      Open in Studio
                    </Button>
                    {item.audio && (
                      <a
                        href={item.audio.downloadUrl}
                        className="grid size-8 place-items-center rounded-full text-ink-2 transition hover:bg-paper-2"
                        aria-label="Download MP3"
                        title="Download MP3"
                      >
                        <Download className="size-4" />
                      </a>
                    )}
                    <button
                      type="button"
                      onClick={() => void remove(item.id)}
                      className={cn(
                        "ml-auto inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-full px-3 text-xs transition",
                        confirm === item.id ? "bg-ember/10 text-ember" : "text-muted hover:bg-ember/10 hover:text-ember",
                      )}
                    >
                      <Trash2 className="size-3.5" />
                      {confirm === item.id ? "Delete forever?" : "Delete"}
                    </button>
                  </div>
                </div>
              </motion.div>
            );
          })}
        </div>
      )}
    </div>
  );
}
