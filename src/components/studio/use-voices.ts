"use client";

import { useEffect, useMemo, useState } from "react";
import { requestVoices } from "@/lib/client/api";
import { getTTS, type TTSProviderId } from "@/lib/providers";
import type { VoiceProfile } from "@/lib/types";
import { staticVoices } from "@/lib/voices";

const cache = new Map<string, VoiceProfile[]>();

export function useVoices(provider: TTSProviderId, model: string, apiKey: string) {
  const meta = getTTS(provider);
  const liveKey = meta.liveVoices && (provider === "edge" || apiKey.trim()) ? `${provider}:${apiKey.trim().slice(-8)}` : null;
  const fallback = useMemo(() => staticVoices(provider, model), [provider, model]);
  const [live, setLive] = useState<{ key: string; voices: VoiceProfile[] } | null>(null);
  const [failed, setFailed] = useState<{ key: string; error: string } | null>(null);

  const cached = liveKey ? cache.get(liveKey) : undefined;

  useEffect(() => {
    if (!liveKey || cache.has(liveKey)) return;
    let cancelled = false;
    requestVoices(provider, apiKey.trim())
      .then((voices) => {
        cache.set(liveKey, voices);
        if (!cancelled) setLive({ key: liveKey, voices });
      })
      .catch((e: Error) => {
        if (!cancelled) setFailed({ key: liveKey, error: e.message });
      });
    return () => {
      cancelled = true;
    };
  }, [liveKey, provider, apiKey]);

  const liveVoices = cached ?? (live && live.key === liveKey ? live.voices : undefined);
  const hasLive = Boolean(liveVoices?.length);
  const error = failed && failed.key === liveKey ? failed.error : null;
  return {
    voices: hasLive && liveVoices ? liveVoices : fallback,
    live: hasLive,
    error,
    loading: Boolean(liveKey && !hasLive && !error),
  };
}
