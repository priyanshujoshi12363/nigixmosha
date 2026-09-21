"use client";

import { useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";

const CLIENT = process.env.NEXT_PUBLIC_ADSENSE_CLIENT ?? "";
const SCRIPT_ID = "adsbygoogle-loader";

declare global {
  interface Window {
    adsbygoogle?: unknown[];
  }
}

function loadOnce() {
  if (document.getElementById(SCRIPT_ID)) return;
  const el = document.createElement("script");
  el.id = SCRIPT_ID;
  el.async = true;
  el.crossOrigin = "anonymous";
  el.src = `https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${CLIENT}`;
  document.head.appendChild(el);
}

export function AdSlot({ slot, className }: { slot?: string; className?: string }) {
  const holder = useRef<HTMLDivElement>(null);
  const filled = useRef(false);
  const [near, setNear] = useState(false);

  useEffect(() => {
    const node = holder.current;
    if (!node) return;
    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          setNear(true);
          io.disconnect();
        }
      },
      { rootMargin: "300px" },
    );
    io.observe(node);
    return () => io.disconnect();
  }, []);

  useEffect(() => {
    if (!near || filled.current) return;
    filled.current = true;
    loadOnce();
    try {
      (window.adsbygoogle = window.adsbygoogle ?? []).push({});
    } catch {}
  }, [near]);

  if (!CLIENT || !slot) return null;

  return (
    <div ref={holder} className={cn("mx-auto w-full max-w-3xl", className)}>
      <div className="mb-1.5 text-center font-mono text-[10px] uppercase tracking-[0.2em] text-muted">Advertisement</div>
      <div className="overflow-hidden rounded-2xl border border-line/70 bg-paper-2/40">
        {near ? (
          <ins
            className="adsbygoogle"
            style={{ display: "block", minHeight: 100 }}
            data-ad-client={CLIENT}
            data-ad-slot={slot}
            data-ad-format="auto"
            data-full-width-responsive="true"
          />
        ) : (
          <div style={{ minHeight: 100 }} />
        )}
      </div>
    </div>
  );
}

export const AD_SLOTS = {
  history: process.env.NEXT_PUBLIC_AD_SLOT_HISTORY,
  settings: process.env.NEXT_PUBLIC_AD_SLOT_SETTINGS,
};
