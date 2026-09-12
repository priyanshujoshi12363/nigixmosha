"use client";

import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import type { ServerConfig } from "@/lib/library-types";
import { TTS_PROVIDERS, type TTSProviderId } from "@/lib/providers";
import type { ProviderConfig } from "@/lib/types";

export interface AdvancedSettings {
  ttsConcurrency: number;
  lineGapMs: number;
  speakerGapMs: number;
  paragraphGapMs: number;
  normalize: boolean;
}

interface SettingsState {
  tts: Record<TTSProviderId, ProviderConfig>;
  activeTTS: TTSProviderId;
  advanced: AdvancedSettings;
  hydrated: boolean;
  brainReady: boolean;
  serverKeys: ServerConfig["serverKeys"];
  storage: ServerConfig["storage"];
  setTTS: (id: TTSProviderId, patch: Partial<ProviderConfig>) => void;
  setActiveTTS: (id: TTSProviderId) => void;
  setAdvanced: (patch: Partial<AdvancedSettings>) => void;
  setServerConfig: (config: ServerConfig) => void;
  clearKeys: () => void;
}

export const DEFAULT_ADVANCED: AdvancedSettings = {
  ttsConcurrency: 4,
  lineGapMs: 200,
  speakerGapMs: 340,
  paragraphGapMs: 600,
  normalize: true,
};

const defaultTTS = () =>
  Object.fromEntries(
    TTS_PROVIDERS.map((p) => [p.id, { apiKey: "", baseUrl: p.defaultBaseUrl, model: p.defaultModel }]),
  ) as Record<TTSProviderId, ProviderConfig>;

export const useSettings = create<SettingsState>()(
  persist(
    (set) => ({
      tts: defaultTTS(),
      activeTTS: "nigix",
      advanced: DEFAULT_ADVANCED,
      hydrated: false,
      brainReady: true,
      serverKeys: {},
      storage: { db: false, media: false },
      setTTS: (id, patch) => set((s) => ({ tts: { ...s.tts, [id]: { ...s.tts[id], ...patch } } })),
      setActiveTTS: (id) => set({ activeTTS: id }),
      setAdvanced: (patch) => set((s) => ({ advanced: { ...s.advanced, ...patch } })),
      setServerConfig: (config) =>
        set({ brainReady: config.brain, serverKeys: config.serverKeys, storage: config.storage }),
      clearKeys: () =>
        set((s) => ({
          tts: Object.fromEntries(
            Object.entries(s.tts).map(([k, v]) => [k, { ...v, apiKey: "" }]),
          ) as SettingsState["tts"],
        })),
    }),
    {
      name: "nigixmosha.settings",
      version: 3,
      storage: createJSONStorage(() => localStorage),
      skipHydration: true,
      partialize: ({ tts, activeTTS, advanced }) => ({ tts, activeTTS, advanced }),
      migrate: (persisted) => {
        const p = (persisted ?? {}) as Partial<SettingsState>;
        return { tts: p.tts, activeTTS: p.activeTTS, advanced: p.advanced } as Partial<SettingsState>;
      },
      merge: (persisted, current) => {
        const p = (persisted ?? {}) as Partial<SettingsState>;
        const activeTTS = p.activeTTS && TTS_PROVIDERS.some((x) => x.id === p.activeTTS) ? p.activeTTS : current.activeTTS;
        return {
          ...current,
          activeTTS,
          tts: { ...current.tts, ...(p.tts ?? {}) },
          advanced: { ...current.advanced, ...(p.advanced ?? {}) },
        };
      },
    },
  ),
);

export function isTTSReady(
  id: TTSProviderId,
  cfg: ProviderConfig | undefined,
  serverKeys: Record<string, boolean> = useSettings.getState().serverKeys,
) {
  const meta = TTS_PROVIDERS.find((p) => p.id === id);
  if (!meta || !cfg) return false;
  if (id === "nigix") return Boolean(serverKeys.nigix);
  return !meta.needsKey || Boolean(cfg.apiKey.trim()) || Boolean(serverKeys[id]);
}
