"use client";

import { useEffect } from "react";
import { startProjectSync } from "@/lib/client/library-sync";
import type { ServerConfig } from "@/lib/library-types";
import { useProject } from "@/lib/store/project";
import { useSettings } from "@/lib/store/settings";

export function StoreHydrator() {
  useEffect(() => {
    let cancelled = false;
    let stopSync: (() => void) | undefined;

    Promise.all([useSettings.persist.rehydrate(), useProject.persist.rehydrate()])
      .finally(() => useSettings.setState({ hydrated: true }))
      .then(async () => {
        try {
          const res = await fetch("/api/config", { cache: "no-store" });
          const config = (await res.json()) as ServerConfig;
          if (!cancelled) useSettings.getState().setServerConfig(config);
        } catch {}
        if (!cancelled) stopSync = startProjectSync();
      });

    return () => {
      cancelled = true;
      stopSync?.();
    };
  }, []);
  return null;
}
