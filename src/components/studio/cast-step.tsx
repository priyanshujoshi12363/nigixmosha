"use client";

import Link from "next/link";
import { useEffect, useMemo } from "react";
import { motion } from "motion/react";
import {
  ArrowRight,
  AudioLines,
  BookOpenText,
  BrainCircuit,
  LoaderCircle,
  Play,
  RefreshCw,
  ScrollText,
  Sparkles,
  Square,
  TriangleAlert,
  Users,
} from "lucide-react";
import { Badge, Bars, Button, Initials, Input, Label, Select, Slider, Toggle } from "@/components/ui";
import { candidateVoices, supportsPitch } from "@/lib/engine/casting";
import { langPrefix, languageName } from "@/lib/engine/lang";
import { TTS_PROVIDERS, getTTS, type TTSProviderId } from "@/lib/providers";
import { useJobs } from "@/lib/store/jobs";
import { useProject } from "@/lib/store/project";
import { isTTSReady, useSettings } from "@/lib/store/settings";
import type { AgeGroup, Character, Gender, Segment, VoiceProfile } from "@/lib/types";
import { NARRATOR_ID } from "@/lib/types";
import { NARRATOR_COLOR } from "@/lib/utils";
import { useVoices } from "./use-voices";

const SAMPLE_LINES: Record<string, string> = {
  en: "Every story begins with a single voice. This one is mine.",
  hi: "हर कहानी एक आवाज़ से शुरू होती है। यह मेरी आवाज़ है।",
  bn: "প্রতিটি গল্প একটি কণ্ঠ দিয়ে শুরু হয়। এটি আমার কণ্ঠ।",
  ta: "ஒவ்வொரு கதையும் ஒரு குரலில் தொடங்குகிறது. இது என் குரல்.",
  es: "Toda historia empieza con una sola voz. Esta es la mía.",
  fr: "Chaque histoire commence par une seule voix. Voici la mienne.",
};

const AGES: AgeGroup[] = ["child", "teen", "young", "adult", "middle", "elderly"];
const GENDERS: Gender[] = ["female", "male", "neutral"];

function sampleFor(speaker: string, segments: Segment[], language: string): Segment {
  const lines = segments.filter((s) => s.speaker === speaker);
  const pick = lines.find((s) => s.text.length > 25 && s.text.length < 240) ?? lines[0];
  if (pick) return pick;
  return {
    id: `sample-${speaker}`,
    speaker,
    emotion: "neutral",
    text: SAMPLE_LINES[langPrefix(language)] ?? SAMPLE_LINES.en,
  };
}

function voiceLabel(v: VoiceProfile) {
  const locale = v.langs[0] === "*" ? "" : ` · ${v.langs[0]}`;
  const tags = v.tags.filter((t) => !["general", "novel", "news", "conversation", "copilot"].includes(t)).slice(0, 2);
  return `${v.name} · ${v.gender}${locale}${tags.length ? ` · ${tags.join(", ")}` : ""}`;
}

function PreviewButton({ speaker }: { speaker: string }) {
  const { previewKey, previewState, preview, stopPreview } = useJobs();
  const { segments, analysis } = useProject();
  const key = `cast:${speaker}`;
  const mine = previewKey === key;
  if (!analysis) return null;
  return (
    <Button
      variant={mine ? "primary" : "outline"}
      size="sm"
      onClick={() => (mine ? stopPreview() : void preview(key, sampleFor(speaker, segments, analysis.language)))}
    >
      {mine && previewState === "loading" ? (
        <LoaderCircle className="size-3.5 animate-spin" />
      ) : mine ? (
        <Square className="size-3 fill-current" />
      ) : (
        <Play className="size-3.5 fill-current" />
      )}
      {mine && previewState === "playing" ? <Bars color="white" count={4} height={12} /> : mine ? "Casting…" : "Hear voice"}
    </Button>
  );
}

function SpeakerCard({
  speaker,
  character,
  pool,
  pitchOK,
  index,
}: {
  speaker: string;
  character: Character | null;
  pool: { voice: VoiceProfile; native: boolean }[];
  pitchOK: boolean;
  index: number;
}) {
  const { cast, castReasons, updateCast, updateCharacter, analysis, shareNarrator, setShareNarrator } = useProject();
  const entry = cast[speaker];
  const color = character?.color ?? NARRATOR_COLOR;
  const name = character?.name ?? "Narrator";
  const narratorLink = !character && analysis?.narrator.characterId
    ? analysis.characters.find((c) => c.id === analysis.narrator.characterId)
    : undefined;
  const linked = !character && shareNarrator && Boolean(narratorLink);
  const native = pool.filter((p) => p.native);
  const others = pool.filter((p) => !p.native);
  const inPool = pool.some((p) => p.voice.id === entry?.voiceId);

  return (
    <motion.div
      initial={{ opacity: 0, y: 18 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: Math.min(index, 8) * 0.05, duration: 0.45 }}
      className="card relative flex flex-col overflow-hidden p-5"
    >
      <div className="absolute inset-x-0 top-0 h-1" style={{ background: color }} />
      <div className="flex items-start gap-3">
        <Initials name={name} color={color} size={44} />
        <div className="min-w-0 flex-1">
          {character ? (
            <input
              value={character.name}
              onChange={(e) => updateCharacter(character.id, { name: e.target.value })}
              className="w-full rounded-md bg-transparent text-[17px] font-semibold text-ink outline-none focus:bg-paper-2"
            />
          ) : (
            <div className="text-[17px] font-semibold text-ink">Narrator</div>
          )}
          <div className="mt-0.5 flex flex-wrap items-center gap-1.5 text-xs text-muted">
            {character ? (
              <>
                <span className="capitalize">{character.role}</span>
                <span>·</span>
                <span>{character.lineCount} lines</span>
              </>
            ) : (
              <span>{analysis?.narrator.tone}</span>
            )}
          </div>
        </div>
      </div>

      {character && (
        <>
          <div className="mt-4 grid grid-cols-2 gap-2">
            <Select
              value={character.gender}
              onChange={(e) => updateCharacter(character.id, { gender: e.target.value as Gender })}
              className="h-8 text-xs capitalize"
              aria-label="Gender"
            >
              {GENDERS.map((g) => (
                <option key={g} value={g}>
                  {g}
                </option>
              ))}
            </Select>
            <Select
              value={character.age}
              onChange={(e) => updateCharacter(character.id, { age: e.target.value as AgeGroup })}
              className="h-8 text-xs capitalize"
              aria-label="Age"
            >
              {AGES.map((a) => (
                <option key={a} value={a}>
                  {a}
                </option>
              ))}
            </Select>
          </div>
          {character.personality.length > 0 && (
            <div className="mt-3 flex flex-wrap gap-1">
              {character.personality.map((t) => (
                <span key={t} className="rounded-full bg-paper-2 px-2 py-0.5 text-[11px] text-ink-2">
                  {t}
                </span>
              ))}
            </div>
          )}
          {(character.speakingStyle || character.voiceDescription) && (
            <p className="mt-3 line-clamp-3 text-[12.5px] leading-relaxed text-muted">
              {character.speakingStyle}
              {character.speakingStyle && character.voiceDescription ? " — " : ""}
              {character.voiceDescription && <span className="text-ink-2">{character.voiceDescription}</span>}
            </p>
          )}
        </>
      )}

      {narratorLink && (
        <div className="mt-4 rounded-xl bg-paper-2 p-3">
          <Toggle
            checked={shareNarrator}
            onChange={setShareNarrator}
            label={<span className="text-xs">First person — narrate in {narratorLink.name}&apos;s voice</span>}
          />
        </div>
      )}

      <div className="mt-auto space-y-3 pt-5">
        <div>
          <Label>Voice</Label>
          <Select
            value={entry?.voiceId ?? ""}
            disabled={linked}
            onChange={(e) => updateCast(speaker, { voiceId: e.target.value })}
            className="h-9 text-[13px]"
          >
            {!inPool && entry?.voiceId && <option value={entry.voiceId}>{entry.voiceId}</option>}
            {native.length > 0 && (
              <optgroup label={`Native ${analysis ? languageName(analysis.language) : ""}`}>
                {native.map(({ voice }) => (
                  <option key={voice.id} value={voice.id}>
                    {voiceLabel(voice)}
                  </option>
                ))}
              </optgroup>
            )}
            {others.length > 0 && (
              <optgroup label="Multilingual">
                {others.map(({ voice }) => (
                  <option key={voice.id} value={voice.id}>
                    {voiceLabel(voice)}
                  </option>
                ))}
              </optgroup>
            )}
          </Select>
          {castReasons[speaker] && !linked && (
            <p className="mt-2 flex gap-1.5 text-[11.5px] leading-snug text-muted">
              <Sparkles className="mt-0.5 size-3 shrink-0 text-ink" />
              {castReasons[speaker]}
            </p>
          )}
        </div>
        {!linked && entry && (
          <div className="grid grid-cols-2 gap-4">
            <Slider
              label="Pitch"
              value={entry.pitch}
              min={-30}
              max={30}
              disabled={!pitchOK}
              hint="This engine doesn't support pitch shifting"
              onChange={(pitch) => updateCast(speaker, { pitch })}
            />
            <Slider label="Pace" value={entry.rate} min={-35} max={35} onChange={(rate) => updateCast(speaker, { rate })} />
          </div>
        )}
        <div className="flex items-center justify-between pt-1">
          <span className="flex items-center gap-1.5 text-[11px] text-muted">
            <span className="size-2 rounded-full" style={{ background: color }} />
            {linked ? `Shares ${narratorLink?.name}'s voice` : pool.find((p) => p.voice.id === entry?.voiceId)?.voice.name ?? "—"}
          </span>
          <PreviewButton speaker={speaker} />
        </div>
      </div>
    </motion.div>
  );
}

export function CastStep() {
  const { analysis, segments, castFor, castBy, setStep, warnings, updateAnalysis } = useProject();
  const { activeTTS, setActiveTTS, tts, setTTS } = useSettings();
  const { startProduce, startCast, castStatus, castNote } = useJobs();
  const meta = getTTS(activeTTS);
  const cfg = tts[activeTTS];
  const model = cfg.model || meta.defaultModel;
  const { voices, live, loading, error } = useVoices(activeTTS, model, cfg.apiKey);
  const ready = isTTSReady(activeTTS, cfg);

  const pool = useMemo(
    () => (analysis ? candidateVoices(voices, meta, analysis.language) : []),
    [voices, meta, analysis],
  );
  const pitchOK = supportsPitch(activeTTS, model);

  useEffect(() => {
    if (!analysis || castStatus !== "idle") return;
    if (castFor && castFor.provider === activeTTS && castFor.model === model) return;
    void startCast();
  }, [analysis, castStatus, castFor, activeTTS, model, startCast]);

  if (!analysis) return null;

  const casting = castStatus === "running";
  const castReady = castFor?.provider === activeTTS && castFor.model === model && !casting;
  const unsupported = !meta.anyLanguage && meta.languages && !meta.languages.includes(langPrefix(analysis.language));

  return (
    <div className="space-y-6">
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_420px]">
        <div className="card p-6">
          <div className="flex items-center gap-2 font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted">
            <BookOpenText className="size-3.5" /> Chapter overview
          </div>
          <Input
            value={analysis.title}
            onChange={(e) => updateAnalysis({ title: e.target.value })}
            className="mt-3 h-auto border-transparent bg-transparent px-0 font-display text-2xl font-semibold tracking-[-0.025em] focus:bg-white focus:px-2"
          />
          <div className="mt-3 flex flex-wrap gap-1.5">
            <Badge tone="iris">{languageName(analysis.language)}</Badge>
            {analysis.mixedLanguages.map((l) => (
              <Badge key={l} tone="cobalt">+ {languageName(l)}</Badge>
            ))}
            <Badge>{analysis.pov === "first" ? "First person" : "Third person"}</Badge>
            <Badge>
              <Users className="size-3" /> {analysis.characters.length} characters
            </Badge>
            <Badge>{segments.length} lines</Badge>
          </div>
          {analysis.summary && <p className="mt-4 max-w-3xl text-[14.5px] leading-relaxed text-ink-2">{analysis.summary}</p>}
          {warnings.length > 0 && (
            <ul className="mt-4 space-y-1.5">
              {warnings.map((w, i) => (
                <li key={i} className="flex items-start gap-2 text-xs text-muted">
                  <TriangleAlert className="mt-0.5 size-3.5 shrink-0 text-tide" /> {w}
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="card p-6">
          <div className="flex items-center gap-2 font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted">
            <AudioLines className="size-3.5" /> Voice engine
          </div>
          <div className="mt-4 grid grid-cols-[1fr_auto] gap-2">
            <Select value={activeTTS} onChange={(e) => setActiveTTS(e.target.value as TTSProviderId)}>
              {TTS_PROVIDERS.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                  {p.free ? " (free)" : ""}
                  {isTTSReady(p.id, tts[p.id]) ? "" : " — needs key"}
                </option>
              ))}
            </Select>
            <Button
              variant="outline"
              onClick={() => void startCast()}
              disabled={casting}
              title="Let the director cast every voice again"
            >
              {casting ? <LoaderCircle className="size-3.5 animate-spin" /> : <RefreshCw className="size-3.5" />}
              Recast
            </Button>
          </div>
          {meta.models.length > 1 && (
            <div className="mt-2">
              <Select value={model} onChange={(e) => setTTS(activeTTS, { model: e.target.value })} className="h-9 text-[13px]">
                {meta.models.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </Select>
            </div>
          )}
          <div className="mt-3 flex items-center gap-2 text-xs text-muted">
            {loading ? (
              <>
                <LoaderCircle className="size-3.5 animate-spin" /> Loading voice library…
              </>
            ) : (
              <>
                <span className={live ? "size-1.5 rounded-full bg-pine" : "size-1.5 rounded-full bg-paper-3"} />
                {pool.length} voices for {languageName(analysis.language)}
                {live ? " · live library" : " · built-in list"}
              </>
            )}
          </div>
          {castReady && (
            <div className="mt-3 flex items-center gap-2 rounded-xl bg-paper-2 px-3 py-2 text-xs text-ink-2">
              <BrainCircuit className="size-3.5 shrink-0" />
              {castBy === "ai" ? "Every voice was hand-picked by the director." : "Voices were matched automatically by gender, age and personality."}
            </div>
          )}
          {castNote && <div className="mt-2 text-xs leading-relaxed text-muted">{castNote}</div>}
          {error && <div className="mt-2 text-xs text-muted">Live voice list unavailable ({error}); using built-in voices.</div>}
          {!ready && (
            <div className="mt-3 flex items-start gap-2 rounded-xl border border-tide/25 bg-tide/5 p-3 text-xs text-ink-2">
              <TriangleAlert className="mt-0.5 size-3.5 shrink-0 text-tide" />
              <span>
                {meta.name} needs an API key before it can speak.{" "}
                <Link href="/settings" className="font-medium underline underline-offset-2">
                  Add it in Settings
                </Link>
                .
              </span>
            </div>
          )}
          {unsupported && (
            <div className="mt-3 flex items-start gap-2 rounded-xl border border-tide/25 bg-tide/5 p-3 text-xs text-ink-2">
              <TriangleAlert className="mt-0.5 size-3.5 shrink-0 text-tide" />
              {meta.name} doesn&apos;t officially support {languageName(analysis.language)}. Edge Neural Voices cover it for free.
            </div>
          )}
        </div>
      </div>

      {castReady ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <SpeakerCard speaker={NARRATOR_ID} character={null} pool={pool} pitchOK={pitchOK} index={0} />
          {analysis.characters.map((c, i) => (
            <SpeakerCard key={c.id} speaker={c.id} character={c} pool={pool} pitchOK={pitchOK} index={i + 1} />
          ))}
        </div>
      ) : (
        <div className="card grid h-64 place-items-center">
          <div className="flex flex-col items-center gap-3 text-center text-sm text-muted">
            <span className="grid size-11 place-items-center rounded-2xl bg-ink text-white">
              <BrainCircuit className="size-5" />
            </span>
            <span className="inline-flex items-center gap-2">
              <LoaderCircle className="size-4 animate-spin" />
              {`The director is choosing a ${meta.name} voice for every character…`}
            </span>
          </div>
        </div>
      )}

      <div className="sticky bottom-4 z-20 flex flex-col items-stretch justify-between gap-3 rounded-2xl border border-line bg-paper/85 p-3 pl-5 shadow-[0_20px_50px_-24px_rgba(0,0,0,0.25)] backdrop-blur-xl sm:flex-row sm:items-center">
        <div className="flex -space-x-2">
          <Initials name="Narrator" color={NARRATOR_COLOR} size={30} className="ring-2 ring-paper" />
          {analysis.characters.slice(0, 7).map((c) => (
            <Initials key={c.id} name={c.name} color={c.color} size={30} className="ring-2 ring-paper" />
          ))}
          <span className="ml-4 self-center pl-3 text-sm text-ink-2">
            {analysis.characters.length + 1} voices cast for {segments.length} lines
          </span>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setStep("script")}>
            <ScrollText className="size-4" /> Review script
          </Button>
          <Button variant="brand" disabled={!castReady || !ready} onClick={() => void startProduce()} className="group">
            Produce audiobook
            <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
          </Button>
        </div>
      </div>
    </div>
  );
}
