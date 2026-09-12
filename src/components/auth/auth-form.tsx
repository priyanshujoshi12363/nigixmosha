"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { motion } from "motion/react";
import { ArrowRight, Eye, EyeOff, LoaderCircle, ShieldCheck } from "lucide-react";
import { Logo } from "@/components/logo";
import { Bars, Button, Initials, Input, Label } from "@/components/ui";
import { login, resetLocalSession, signup } from "@/lib/client/auth";
import { cn } from "@/lib/utils";

const LINES = [
  { name: "Meera", color: "#0D9488", text: "Who knocks at this hour?", emotion: "whisper" },
  { name: "Baba", color: "#4F46E5", text: "Someone who has walked a long way, child.", emotion: "tender" },
  { name: "Arjun", color: "#2563EB", text: "I'm looking for the keeper of the old library.", emotion: "serious" },
];

const STRENGTH = ["Too short", "Weak", "Okay", "Good", "Strong"];

function strength(pw: string) {
  if (pw.length < 8) return 0;
  let s = 1;
  if (pw.length >= 12) s++;
  if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) s++;
  if (/\d/.test(pw) && /[^A-Za-z0-9]/.test(pw)) s++;
  return Math.min(4, s);
}

export function AuthForm({ mode, next }: { mode: "login" | "signup"; next: string }) {
  const router = useRouter();
  const isSignup = mode === "signup";
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const score = strength(password);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const u = username.trim().toLowerCase();
    if (!/^[a-z0-9_]{3,24}$/.test(u)) return setError("Username must be 3–24 characters: letters, numbers or underscore.");
    if (password.length < 8) return setError("Password must be at least 8 characters.");
    if (isSignup && password !== confirm) return setError("The passwords don't match.");
    setBusy(true);
    try {
      if (isSignup) await signup(u, password, displayName.trim());
      else await login(u, password);
      resetLocalSession();
      router.replace(next);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      setBusy(false);
    }
  }

  const other = isSignup ? `/login?next=${encodeURIComponent(next)}` : `/signup?next=${encodeURIComponent(next)}`;

  return (
    <div className="grid min-h-screen flex-1 lg:grid-cols-[1.05fr_1fr]">
      <aside className="relative hidden overflow-hidden bg-night p-12 text-white lg:flex lg:flex-col">
        <div aria-hidden className="pointer-events-none absolute inset-0">
          <div className="absolute -left-24 -top-24 size-[30rem] animate-aurora rounded-full bg-white/10 blur-[120px]" />
          <div
            className="absolute -bottom-32 -right-16 size-[32rem] animate-aurora rounded-full bg-white/[0.07] blur-[130px]"
            style={{ animationDelay: "-9s" }}
          />
          <div className="absolute left-1/3 top-1/2 size-80 animate-aurora rounded-full bg-white/[0.05] blur-[110px]" style={{ animationDelay: "-4s" }} />
        </div>
        <Link href="/" className="relative w-fit" aria-label="nigixmosha home">
          <Logo />
        </Link>
        <div className="relative mt-auto max-w-lg">
          <h2 className="font-display text-4xl font-semibold leading-[1.1] tracking-[-0.035em]">
            Every character deserves <span className="text-gradient-light">their own voice.</span>
          </h2>
          <div className="mt-10 space-y-3">
            {LINES.map((l, i) => (
              <motion.div
                key={l.name}
                initial={{ opacity: 0, x: -16 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.3 + i * 0.18, duration: 0.6 }}
                className="flex items-center gap-3 rounded-2xl bg-white/[0.06] p-3 pr-4 backdrop-blur"
              >
                <Initials name={l.name} color={l.color} size={36} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2 text-xs text-white/60">
                    {l.name} <span className="rounded-full bg-white/10 px-1.5 py-px text-[10px] capitalize">{l.emotion}</span>
                  </div>
                  <div className="truncate text-[15px] font-semibold text-white/90">“{l.text}”</div>
                </div>
                <Bars color={l.color} count={4} height={14} />
              </motion.div>
            ))}
          </div>
          <p className="mt-10 flex items-center gap-2 text-sm text-white/55">
            <ShieldCheck className="size-4" /> Your chapters, casts and recordings stay in your private history.
          </p>
        </div>
      </aside>

      <div className="flex items-center justify-center px-6 py-12">
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="w-full max-w-sm"
        >
          <Link href="/" className="lg:hidden" aria-label="nigixmosha home">
            <Logo />
          </Link>
          <h1 className="mt-8 font-display text-3xl font-semibold leading-tight tracking-[-0.035em] text-ink lg:mt-0">
            {isSignup ? "Create your studio" : "Welcome back"}
          </h1>
          <p className="mt-2 text-[15px] text-muted">
            {isSignup ? "Pick a username and a password. That's all it takes." : "Sign in to continue to your studio."}
          </p>

          <form onSubmit={submit} className="mt-8 space-y-4" noValidate>
            {isSignup && (
              <div>
                <Label htmlFor="displayName">Display name (optional)</Label>
                <Input
                  id="displayName"
                  value={displayName}
                  maxLength={40}
                  autoComplete="name"
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder="Priyanshu"
                />
              </div>
            )}
            <div>
              <Label htmlFor="username">Username</Label>
              <div className="relative">
                <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-sm text-muted">@</span>
                <Input
                  id="username"
                  value={username}
                  maxLength={24}
                  autoComplete="username"
                  autoCapitalize="none"
                  spellCheck={false}
                  onChange={(e) => setUsername(e.target.value.toLowerCase().replace(/\s+/g, ""))}
                  placeholder="storyteller"
                  className="pl-8"
                  autoFocus
                />
              </div>
            </div>
            <div>
              <Label htmlFor="password">Password</Label>
              <div className="relative">
                <Input
                  id="password"
                  type={show ? "text" : "password"}
                  value={password}
                  autoComplete={isSignup ? "new-password" : "current-password"}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder={isSignup ? "At least 8 characters" : "Your password"}
                  className="pr-10"
                />
                <button
                  type="button"
                  onClick={() => setShow((s) => !s)}
                  aria-label={show ? "Hide password" : "Show password"}
                  className="absolute right-2 top-1/2 grid size-7 -translate-y-1/2 cursor-pointer place-items-center rounded-lg text-muted hover:bg-paper-2 hover:text-ink"
                >
                  {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
              {isSignup && password && (
                <div className="mt-2 flex items-center gap-2">
                  <div className="flex flex-1 gap-1">
                    {[1, 2, 3, 4].map((n) => (
                      <span
                        key={n}
                        className={cn(
                          "h-1 flex-1 rounded-full transition-colors",
                          score >= n ? (score <= 1 ? "bg-ember" : score === 2 ? "bg-neutral-500" : "bg-pine") : "bg-paper-3",
                        )}
                      />
                    ))}
                  </div>
                  <span className="w-16 text-right text-[11px] text-muted">{STRENGTH[score]}</span>
                </div>
              )}
            </div>
            {isSignup && (
              <div>
                <Label htmlFor="confirm">Confirm password</Label>
                <Input
                  id="confirm"
                  type={show ? "text" : "password"}
                  value={confirm}
                  autoComplete="new-password"
                  onChange={(e) => setConfirm(e.target.value)}
                  placeholder="Type it again"
                />
              </div>
            )}

            {error && (
              <div role="alert" className="rounded-xl border border-ember/25 bg-ember/5 px-3.5 py-2.5 text-sm text-ember">
                {error}
              </div>
            )}

            <Button type="submit" variant="brand" size="lg" className="group w-full" disabled={busy}>
              {busy && <LoaderCircle className="size-4 animate-spin" />}
              {isSignup ? "Create account" : "Sign in"}
              {!busy && <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />}
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-muted">
            {isSignup ? "Already have an account?" : "New to nigixmosha?"}{" "}
            <Link href={other} className="font-medium text-ink underline underline-offset-4">
              {isSignup ? "Sign in" : "Create an account"}
            </Link>
          </p>
        </motion.div>
      </div>
    </div>
  );
}
