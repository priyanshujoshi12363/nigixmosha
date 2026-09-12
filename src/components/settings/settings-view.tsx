"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { motion } from "motion/react";
import {
  ArrowUpRight,
  AudioLines,
  Check,
  Eye,
  EyeOff,
  KeyRound,
  LoaderCircle,
  LogOut,
  ShieldCheck,
  SlidersHorizontal,
  UserRound,
  Zap,
} from "lucide-react";
import { Badge, Button, Initials, Input, Label, Slider, Toggle } from "@/components/ui";
import { requestSpeech } from "@/lib/client/api";
import { changePassword, logout, resetLocalSession } from "@/lib/client/auth";
import type { AccountUser } from "@/lib/library-types";
import { TTS_PROVIDERS, type TTSProviderMeta } from "@/lib/providers";
import { DEFAULT_ADVANCED, isTTSReady, useSettings } from "@/lib/store/settings";
import type { ProviderConfig } from "@/lib/types";
import { cn } from "@/lib/utils";
import { staticVoices } from "@/lib/voices";

type TestState = { status: "idle" | "running" | "ok" | "error"; message?: string };

function KeyField({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  const [show, setShow] = useState(false);
  return (
    <div className="relative">
      <Input
        type={show ? "text" : "password"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        autoComplete="off"
        spellCheck={false}
        className="pr-10 font-mono text-[13px]"
      />
      <button
        type="button"
        onClick={() => setShow((s) => !s)}
        className="absolute right-2 top-1/2 grid size-7 -translate-y-1/2 cursor-pointer place-items-center rounded-lg text-muted hover:bg-paper-2 hover:text-ink"
        aria-label={show ? "Hide key" : "Show key"}
      >
        {show ? <EyeOff className="size-3.5" /> : <Eye className="size-3.5" />}
      </button>
    </div>
  );
}

function VoiceEngineCard({
  meta,
  config,
  active,
  ready,
  onChange,
  onActivate,
  onTest,
  index,
  serverKey = false,
}: {
  meta: TTSProviderMeta;
  config: ProviderConfig;
  active: boolean;
  ready: boolean;
  onChange: (patch: Partial<ProviderConfig>) => void;
  onActivate: () => void;
  onTest: () => Promise<string>;
  index: number;
  serverKey?: boolean;
}) {
  const [test, setTest] = useState<TestState>({ status: "idle" });
  const showKey = meta.needsKey || meta.editableBaseUrl;
  const showModel = meta.models.length > 1 || meta.editableBaseUrl;
  const local = meta.editableBaseUrl && /localhost|127\.0\.0\.1/.test(meta.defaultBaseUrl);
  const listId = `tts-${meta.id}-models`;

  async function runTest() {
    setTest({ status: "running" });
    try {
      const message = await onTest();
      setTest({ status: "ok", message });
    } catch (e) {
      setTest({ status: "error", message: e instanceof Error ? e.message : String(e) });
    }
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-40px" }}
      transition={{ duration: 0.45, delay: (index % 3) * 0.05 }}
      className={cn("card relative flex flex-col p-5 transition-shadow", active && "ring-2 ring-tide/50")}
    >
      <div className="flex items-start gap-3">
        <span
          className="grid size-10 shrink-0 place-items-center rounded-xl text-sm font-semibold text-white"
          style={{ background: "linear-gradient(135deg, #3a3a3a, #0a0a0a)" }}
        >
          {meta.name.replace(/[^A-Za-z]/g, "").slice(0, 2)}
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="font-semibold text-ink">{meta.name}</span>
            {meta.free && <Badge tone="pine">Free</Badge>}
            {local && <Badge tone="cobalt">Local</Badge>}
            {serverKey && <Badge tone="iris">Ready to use</Badge>}
            {active && (
              <Badge tone="tide">
                <Check className="size-3" /> Active
              </Badge>
            )}
          </div>
          <p className="mt-1 text-[12.5px] leading-relaxed text-muted">{meta.tagline}</p>
        </div>
      </div>

      <div className="mt-4 space-y-3">
        {showKey && (
          <div>
            <Label>API key{meta.needsKey && !serverKey ? "" : " (optional)"}</Label>
            <KeyField
              value={config.apiKey}
              onChange={(apiKey) => onChange({ apiKey })}
              placeholder={serverKey ? "Included with nigixmosha" : meta.needsKey ? "Paste your key" : "Not required"}
            />
          </div>
        )}
        {meta.editableBaseUrl && (
          <div>
            <Label>Base URL</Label>
            <Input
              value={config.baseUrl}
              onChange={(e) => onChange({ baseUrl: e.target.value })}
              placeholder={meta.defaultBaseUrl}
              className="font-mono text-[13px]"
            />
          </div>
        )}
        {showModel && (
          <div>
            <Label>Model</Label>
            <Input
              value={config.model}
              list={listId}
              onChange={(e) => onChange({ model: e.target.value })}
              placeholder={meta.defaultModel || "model name"}
              className="font-mono text-[13px]"
            />
            <datalist id={listId}>
              {meta.models.map((m) => (
                <option key={m} value={m} />
              ))}
            </datalist>
          </div>
        )}
      </div>

      {test.status !== "idle" && test.status !== "running" && (
        <div
          className={cn(
            "mt-3 rounded-xl px-3 py-2 text-xs leading-relaxed",
            test.status === "ok" ? "bg-pine/10 text-pine" : "bg-ember/5 text-ember",
          )}
        >
          {test.message}
        </div>
      )}

      <div className="mt-auto flex flex-wrap items-center gap-2 pt-5">
        <Button variant={active ? "soft" : "primary"} size="sm" onClick={onActivate} disabled={active}>
          {active ? "In use" : "Use for voices"}
        </Button>
        <Button variant="outline" size="sm" onClick={() => void runTest()} disabled={!ready || test.status === "running"}>
          {test.status === "running" ? <LoaderCircle className="size-3.5 animate-spin" /> : <Zap className="size-3.5" />}
          Test
        </Button>
        {meta.keyUrl && !serverKey && (
          <a
            href={meta.keyUrl}
            target="_blank"
            rel="noreferrer"
            className="ml-auto inline-flex items-center gap-1 text-xs text-muted hover:text-ink"
          >
            {meta.needsKey ? "Get a key" : "Setup guide"} <ArrowUpRight className="size-3" />
          </a>
        )}
      </div>
    </motion.div>
  );
}

async function testVoice(meta: TTSProviderMeta, config: ProviderConfig) {
  const model = config.model || meta.defaultModel;
  const voice = staticVoices(meta.id, model)[0];
  const lang = meta.id === "sarvam-tts" ? "hi-IN" : "en-US";
  const text = meta.id === "sarvam-tts" ? "नमस्ते! nigixmosha में आपका स्वागत है।" : "Hello! Your voice engine is connected to nigixmosha.";
  const started = performance.now();
  const buf = await requestSpeech(meta.id, config, voice.id, text, lang, {
    pitch: 0,
    rate: 0,
    volume: 0,
    emotion: "happy",
    instructions: "Speak warmly and clearly, like a friendly studio host.",
  });
  const ms = Math.round(performance.now() - started);
  const url = URL.createObjectURL(new Blob([buf]));
  const audio = new Audio(url);
  audio.onended = () => URL.revokeObjectURL(url);
  await audio.play().catch(() => undefined);
  return `Speaking with ${voice.name} · generated in ${ms} ms`;
}

function AccountSection({ user }: { user: AccountUser }) {
  const router = useRouter();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [state, setState] = useState<{ status: "idle" | "busy" | "ok" | "error"; message?: string }>({ status: "idle" });
  const [leaving, setLeaving] = useState<"one" | "all" | null>(null);
  const name = user.displayName || user.username;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (next.length < 8) return setState({ status: "error", message: "The new password must be at least 8 characters." });
    if (next !== confirm) return setState({ status: "error", message: "The new passwords don't match." });
    setState({ status: "busy" });
    try {
      await changePassword(current, next);
      setCurrent("");
      setNext("");
      setConfirm("");
      setState({ status: "ok", message: "Password updated. Your other devices were signed out." });
    } catch (err) {
      setState({ status: "error", message: err instanceof Error ? err.message : String(err) });
    }
  }

  async function signOut(all: boolean) {
    setLeaving(all ? "all" : "one");
    try {
      await logout(all);
    } finally {
      resetLocalSession();
      router.replace("/login");
      router.refresh();
    }
  }

  return (
    <section id="account" className="scroll-mt-24">
      <h2 className="flex items-center gap-2 text-xl font-semibold text-ink">
        <UserRound className="size-5 text-ink" /> Account
      </h2>
      <p className="mt-1 text-sm text-muted">Your identity, password and sessions.</p>
      <div className="card mt-5 grid gap-8 p-6 md:grid-cols-[1fr_1.3fr]">
        <div className="flex flex-col">
          <div className="flex items-center gap-4">
            <Initials name={name} color="#262626" size={56} />
            <div className="min-w-0">
              <div className="truncate text-lg font-semibold text-ink">{name}</div>
              <div className="truncate text-sm text-muted">@{user.username}</div>
              <div className="mt-1 text-xs text-muted">Member since {new Date(user.createdAt).toLocaleDateString()}</div>
            </div>
          </div>
          <div className="mt-6 flex flex-wrap gap-2 md:mt-auto md:pt-6">
            <Button variant="outline" size="sm" onClick={() => void signOut(false)} disabled={leaving !== null}>
              {leaving === "one" ? <LoaderCircle className="size-3.5 animate-spin" /> : <LogOut className="size-3.5" />}
              Sign out
            </Button>
            <Button variant="danger" size="sm" onClick={() => void signOut(true)} disabled={leaving !== null}>
              {leaving === "all" && <LoaderCircle className="size-3.5 animate-spin" />}
              Sign out of all devices
            </Button>
          </div>
        </div>
        <form onSubmit={submit} className="space-y-3" noValidate>
          <div className="text-sm font-semibold text-ink">Change password</div>
          <div>
            <Label htmlFor="current-password">Current password</Label>
            <Input
              id="current-password"
              type="password"
              autoComplete="current-password"
              value={current}
              onChange={(e) => setCurrent(e.target.value)}
            />
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <Label htmlFor="new-password">New password</Label>
              <Input id="new-password" type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} />
            </div>
            <div>
              <Label htmlFor="confirm-password">Confirm new password</Label>
              <Input
                id="confirm-password"
                type="password"
                autoComplete="new-password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
              />
            </div>
          </div>
          {state.message && (
            <div
              role="status"
              className={cn(
                "rounded-xl px-3 py-2 text-xs leading-relaxed",
                state.status === "ok" ? "bg-pine/10 text-pine" : "bg-ember/5 text-ember",
              )}
            >
              {state.message}
            </div>
          )}
          <Button type="submit" size="sm" disabled={state.status === "busy" || !current || !next}>
            {state.status === "busy" && <LoaderCircle className="size-3.5 animate-spin" />}
            Update password
          </Button>
        </form>
      </div>
    </section>
  );
}

const NAV = [
  { id: "account", label: "Account", icon: UserRound },
  { id: "voices", label: "Voices", icon: AudioLines },
  { id: "finishing", label: "Studio finishing", icon: SlidersHorizontal },
  { id: "privacy", label: "Privacy", icon: ShieldCheck },
];

export function SettingsView({ user }: { user: AccountUser }) {
  const s = useSettings();
  const [cleared, setCleared] = useState(false);

  if (!s.hydrated) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
        <div className="h-10 w-72 animate-pulse rounded-full bg-paper-2" />
        <div className="mt-8 grid gap-4 md:grid-cols-3">
          {Array.from({ length: 6 }, (_, i) => (
            <div key={i} className="h-60 animate-pulse rounded-3xl bg-paper-2" />
          ))}
        </div>
      </div>
    );
  }

  const adv = s.advanced;

  return (
    <div className="mx-auto max-w-7xl px-4 pb-24 pt-10 sm:px-6">
      <div className="max-w-2xl">
        <div className="font-mono text-[11px] uppercase tracking-[0.2em] text-muted">Settings</div>
        <h1 className="mt-1 font-display text-4xl font-semibold leading-tight tracking-[-0.035em] text-ink">Voices &amp; account</h1>
        <p className="mt-3 text-[15px] leading-relaxed text-muted">
          The director is built in, so there is nothing to set up for it. Choose the voice engine that performs your
          chapters; any keys you add stay in this browser.
        </p>
      </div>

      <div className="mt-10 grid gap-10 lg:grid-cols-[200px_minmax(0,1fr)]">
        <nav className="hidden lg:block">
          <div className="sticky top-24 space-y-1">
            {NAV.map((n) => (
              <a key={n.id} href={`#${n.id}`} className="flex items-center gap-2.5 rounded-xl px-3 py-2 text-sm text-ink-2 transition hover:bg-white">
                <n.icon className="size-4 text-muted" /> {n.label}
              </a>
            ))}
          </div>
        </nav>

        <div className="space-y-16">
          <AccountSection user={user} />

          <section id="voices" className="scroll-mt-24">
            <h2 className="flex items-center gap-2 text-xl font-semibold text-ink">
              <AudioLines className="size-5 text-ink" /> Voice engines
            </h2>
            <p className="mt-1 text-sm text-muted">
              The director picks a voice for every character from the engine you choose here. nigixmosha Voices are built
              in and free, and Edge Neural Voices are free too.
            </p>
            <div className="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {TTS_PROVIDERS.map((p, i) => (
                <VoiceEngineCard
                  key={p.id}
                  index={i}
                  meta={p}
                  config={s.tts[p.id]}
                  active={s.activeTTS === p.id}
                  ready={isTTSReady(p.id, s.tts[p.id], s.serverKeys)}
                  serverKey={Boolean(s.serverKeys[p.id])}
                  onChange={(patch) => s.setTTS(p.id, patch)}
                  onActivate={() => s.setActiveTTS(p.id)}
                  onTest={() => testVoice(p, s.tts[p.id])}
                />
              ))}
            </div>
          </section>

          <section id="finishing" className="scroll-mt-24">
            <h2 className="flex items-center gap-2 text-xl font-semibold text-ink">
              <SlidersHorizontal className="size-5 text-ink" /> Studio finishing
            </h2>
            <p className="mt-1 text-sm text-muted">How lines are recorded and stitched together.</p>
            <div className="card mt-5 grid gap-x-10 gap-y-6 p-6 md:grid-cols-2">
              <Slider
                label="Parallel voice requests"
                value={adv.ttsConcurrency}
                min={1}
                max={8}
                unit=""
                onChange={(ttsConcurrency) => s.setAdvanced({ ttsConcurrency })}
              />
              <Slider
                label="Pause between lines"
                value={adv.lineGapMs}
                min={0}
                max={1000}
                step={20}
                unit=" ms"
                onChange={(lineGapMs) => s.setAdvanced({ lineGapMs })}
              />
              <Slider
                label="Pause on speaker change"
                value={adv.speakerGapMs}
                min={0}
                max={1200}
                step={20}
                unit=" ms"
                onChange={(speakerGapMs) => s.setAdvanced({ speakerGapMs })}
              />
              <Slider
                label="Pause between paragraphs"
                value={adv.paragraphGapMs}
                min={0}
                max={2000}
                step={20}
                unit=" ms"
                onChange={(paragraphGapMs) => s.setAdvanced({ paragraphGapMs })}
              />
              <div className="flex items-center justify-between md:col-span-2">
                <Toggle
                  checked={adv.normalize}
                  onChange={(normalize) => s.setAdvanced({ normalize })}
                  label="Match loudness across voices"
                />
                <Button variant="ghost" size="sm" onClick={() => s.setAdvanced(DEFAULT_ADVANCED)}>
                  Reset to defaults
                </Button>
              </div>
            </div>
          </section>

          <section id="privacy" className="scroll-mt-24">
            <h2 className="flex items-center gap-2 text-xl font-semibold text-ink">
              <ShieldCheck className="size-5 text-ink" /> Privacy
            </h2>
            <div className="card mt-5 flex flex-col gap-4 p-6 md:flex-row md:items-center md:justify-between">
              <div className="flex max-w-2xl gap-3">
                <KeyRound className="mt-0.5 size-5 shrink-0 text-muted" />
                <p className="text-sm leading-relaxed text-ink-2">
                  Voice keys you add are stored in this browser&apos;s local storage and sent only with your own requests to
                  the voice engine you picked. Your chapters live in your private history.
                </p>
              </div>
              <Button
                variant="danger"
                onClick={() => {
                  s.clearKeys();
                  setCleared(true);
                  setTimeout(() => setCleared(false), 2500);
                }}
              >
                {cleared ? <Check className="size-4" /> : null}
                {cleared ? "Keys removed" : "Remove all keys"}
              </Button>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
