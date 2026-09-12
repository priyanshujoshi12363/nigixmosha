"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { AudioLines, ChevronDown, Clapperboard, History, LogOut, Settings2, UserRound } from "lucide-react";
import { Logo } from "@/components/logo";
import { Initials } from "@/components/ui";
import { logout, resetLocalSession } from "@/lib/client/auth";
import type { AccountUser } from "@/lib/library-types";
import { getTTS } from "@/lib/providers";
import { isTTSReady, useSettings } from "@/lib/store/settings";
import { cn } from "@/lib/utils";

const TABS = [
  { href: "/studio", label: "Studio", icon: Clapperboard },
  { href: "/history", label: "History", icon: History },
  { href: "/settings", label: "Settings", icon: Settings2 },
];

function UserMenu({ user }: { user: AccountUser }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  async function signOut() {
    setLeaving(true);
    try {
      await logout();
    } finally {
      resetLocalSession();
      router.replace("/login");
      router.refresh();
    }
  }

  const name = user.displayName || user.username;
  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-haspopup="menu"
        className="flex cursor-pointer items-center gap-2 rounded-full border border-line bg-white/60 py-1 pl-1 pr-2.5 text-[13px] text-ink-2 transition hover:bg-white"
      >
        <Initials name={name} color="#262626" size={26} />
        <span className="hidden max-w-[120px] truncate font-medium sm:inline">{name}</span>
        <ChevronDown className={cn("size-3.5 transition-transform", open && "rotate-180")} />
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            role="menu"
            initial={{ opacity: 0, y: -6, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -6, scale: 0.98 }}
            transition={{ duration: 0.15 }}
            className="card absolute right-0 top-[calc(100%+8px)] z-50 w-60 p-1.5"
          >
            <div className="px-3 py-2.5">
              <div className="truncate text-sm font-semibold text-ink">{name}</div>
              <div className="truncate text-xs text-muted">@{user.username}</div>
            </div>
            <div className="my-1 h-px bg-line" />
            <Link
              href="/history"
              onClick={() => setOpen(false)}
              className="flex items-center gap-2.5 rounded-xl px-3 py-2 text-sm text-ink-2 hover:bg-paper-2"
            >
              <History className="size-4 text-muted" /> My history
            </Link>
            <Link
              href="/settings#account"
              onClick={() => setOpen(false)}
              className="flex items-center gap-2.5 rounded-xl px-3 py-2 text-sm text-ink-2 hover:bg-paper-2"
            >
              <UserRound className="size-4 text-muted" /> Account
            </Link>
            <button
              type="button"
              onClick={() => void signOut()}
              disabled={leaving}
              className="flex w-full cursor-pointer items-center gap-2.5 rounded-xl px-3 py-2 text-left text-sm text-ember hover:bg-ember/5 disabled:opacity-50"
            >
              <LogOut className="size-4" /> {leaving ? "Signing out…" : "Sign out"}
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export function AppNav({ user }: { user: AccountUser }) {
  const pathname = usePathname();
  const { activeTTS, tts, hydrated, serverKeys } = useSettings();
  const ttsOk = isTTSReady(activeTTS, tts[activeTTS], serverKeys);

  return (
    <header className="sticky top-0 z-40 border-b border-line/70 bg-paper/80 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-3 px-4 sm:px-6">
        <div className="flex min-w-0 items-center gap-4 sm:gap-6">
          <Link href="/" aria-label="nigixmosha home" className="hidden sm:block">
            <Logo />
          </Link>
          <nav className="flex items-center rounded-full border border-line bg-white/60 p-1">
            {TABS.map((t) => {
              const active = pathname.startsWith(t.href);
              return (
                <Link
                  key={t.href}
                  href={t.href}
                  className={cn(
                    "relative flex items-center gap-1.5 rounded-full px-3 py-1.5 text-[13px] font-medium transition-colors",
                    active ? "text-paper" : "text-ink-2 hover:text-ink",
                  )}
                >
                  {active && (
                    <motion.span
                      layoutId="app-tab"
                      className="absolute inset-0 rounded-full bg-ink"
                      transition={{ type: "spring", stiffness: 420, damping: 34 }}
                    />
                  )}
                  <t.icon className="relative size-3.5" />
                  <span className="relative">{t.label}</span>
                </Link>
              );
            })}
          </nav>
        </div>
        <div className="flex items-center gap-2">
          {hydrated && (
            <Link
              href="/settings"
              className="hidden items-center gap-3 rounded-full border border-line bg-white/60 px-3 py-1.5 text-xs text-ink-2 transition hover:bg-white lg:flex"
            >
              <span className="flex items-center gap-1.5">
                <AudioLines className={cn("size-3.5", ttsOk ? "text-pine" : "text-ember")} />
                <span className="text-muted">Voice</span>{getTTS(activeTTS).name}
              </span>
            </Link>
          )}
          <UserMenu user={user} />
        </div>
      </div>
    </header>
  );
}
