"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { ArrowRight, Menu, X } from "lucide-react";
import { Logo } from "@/components/logo";
import { ButtonLink } from "@/components/ui";
import { fetchMe } from "@/lib/client/auth";
import type { AccountUser } from "@/lib/library-types";
import { cn } from "@/lib/utils";

const LINKS = [
  { href: "/#how", label: "How it works" },
  { href: "/#features", label: "Features" },
  { href: "/#languages", label: "Languages" },
  { href: "/#engines", label: "Engines" },
];

export function SiteNav() {
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);
  const [user, setUser] = useState<AccountUser | null | undefined>(undefined);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    let cancelled = false;
    fetchMe().then((u) => {
      if (!cancelled) setUser(u);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <header className="fixed inset-x-0 top-0 z-50 flex justify-center px-4 pt-3 sm:pt-4">
      <motion.nav
        initial={{ y: -24, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.7, ease: [0.2, 0.7, 0.2, 1] }}
        className={cn(
          "flex w-full max-w-6xl items-center justify-between rounded-full border py-2 pl-4 pr-2 transition-all duration-500",
          scrolled || open
            ? "border-line/80 bg-paper/80 shadow-[0_12px_40px_-20px_rgba(0,0,0,0.18)] backdrop-blur-xl"
            : "border-transparent bg-transparent",
        )}
      >
        <Link href="/" aria-label="nigixmosha home">
          <Logo />
        </Link>
        <div className="hidden items-center gap-1 md:flex">
          {LINKS.map((l) => (
            <Link
              key={l.href}
              href={l.href}
              className="rounded-full px-3.5 py-2 text-sm text-ink-2 transition hover:bg-ink/5 hover:text-ink"
            >
              {l.label}
            </Link>
          ))}
        </div>
        <div className="flex items-center gap-1.5">
          {user ? (
            <ButtonLink href="/history" variant="ghost" size="sm" className="hidden sm:inline-flex">
              History
            </ButtonLink>
          ) : (
            <ButtonLink href="/login" variant="ghost" size="sm" className={cn("hidden sm:inline-flex", user === undefined && "invisible")}>
              Sign in
            </ButtonLink>
          )}
          <ButtonLink href={user === null ? "/signup" : "/studio"} size="sm" className="group">
            {user === null ? "Get started" : "Open Studio"}
            <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-0.5" />
          </ButtonLink>
          <button
            type="button"
            aria-label="Menu"
            onClick={() => setOpen((o) => !o)}
            className="grid size-8 cursor-pointer place-items-center rounded-full text-ink-2 hover:bg-ink/5 md:hidden"
          >
            {open ? <X className="size-4" /> : <Menu className="size-4" />}
          </button>
        </div>
      </motion.nav>
      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            className="card absolute inset-x-4 top-[4.4rem] p-2 md:hidden"
          >
            {[...LINKS, user ? { href: "/history", label: "History" } : { href: "/login", label: "Sign in" }].map((l) => (
              <Link
                key={l.href}
                href={l.href}
                onClick={() => setOpen(false)}
                className="block rounded-xl px-4 py-3 text-sm text-ink-2 hover:bg-paper-2"
              >
                {l.label}
              </Link>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  );
}
