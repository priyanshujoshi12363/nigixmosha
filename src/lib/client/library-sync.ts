"use client";

import type { ProjectPayload } from "@/lib/library-types";
import { useProject, type AudioSave } from "@/lib/store/project";
import { useSettings } from "@/lib/store/settings";
import { createProject, updateProject, uploadAudio } from "./library";

let saving: Promise<void> | null = null;
let again = false;
let lastBlob: Blob | null = null;
let uploadToken = 0;

const message = (err: unknown) => (err instanceof Error ? err.message : String(err));

function payload(): ProjectPayload | null {
  const p = useProject.getState();
  if (!p.analysis) return null;
  return {
    title: p.analysis.title || p.title || "Untitled chapter",
    text: p.text,
    languageHint: p.languageHint,
    analysis: p.analysis,
    segments: p.segments,
    warnings: p.warnings,
    cast: p.cast,
    castFor: p.castFor,
    castReasons: p.castReasons,
    castBy: p.castBy,
    shareNarrator: p.shareNarrator,
  };
}

export function saveProjectNow(): Promise<void> {
  if (!useSettings.getState().storage.db) return Promise.resolve();
  if (saving) {
    again = true;
    return saving;
  }
  saving = (async () => {
    const body = payload();
    if (!body) return;
    const store = useProject.getState();
    const id = store.projectId;
    store.setSaveState("saving");
    try {
      if (id) await updateProject(id, body);
      else useProject.getState().setProjectId(await createProject(body));
      useProject.getState().setSaveState("saved");
    } catch (err) {
      if ((err as { status?: number }).status === 404 && id) {
        useProject.getState().setProjectId(null);
        again = true;
      } else {
        useProject.getState().setSaveState("error", message(err));
      }
    }
  })().finally(() => {
    saving = null;
    if (again) {
      again = false;
      void saveProjectNow();
    }
  });
  return saving;
}

export function startProjectSync() {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const unsubscribe = useProject.subscribe((s, prev) => {
    if (!s.analysis || s.projectId !== prev.projectId) return;
    const changed =
      s.analysis !== prev.analysis ||
      s.segments !== prev.segments ||
      s.cast !== prev.cast ||
      s.castFor !== prev.castFor ||
      s.castReasons !== prev.castReasons ||
      s.shareNarrator !== prev.shareNarrator ||
      s.title !== prev.title ||
      s.languageHint !== prev.languageHint;
    if (!changed) return;
    clearTimeout(timer);
    timer = setTimeout(() => void saveProjectNow(), 1200);
  });
  return () => {
    clearTimeout(timer);
    unsubscribe();
  };
}

export async function saveAudiobook(blob?: Blob) {
  const { storage } = useSettings.getState();
  if (!storage.db || !storage.media) return;
  if (blob) lastBlob = blob;
  const file = blob ?? lastBlob;
  if (!file) return;
  const token = ++uploadToken;
  const report = (a: AudioSave) => {
    if (token === uploadToken) useProject.getState().setAudioSave(a);
  };
  try {
    report({ status: "uploading", progress: 0 });
    await saveProjectNow();
    const { projectId, output } = useProject.getState();
    if (!projectId || !output) throw new Error("The project could not be saved to the library");
    const up = await uploadAudio(projectId, file, (progress) => report({ status: "uploading", progress }));
    const res = await updateProject(projectId, {
      audio: {
        publicId: up.publicId,
        version: up.version,
        bytes: up.bytes,
        duration: output.duration,
        timeline: output.timeline,
        peaks: output.peaks,
        provider: output.provider,
        failed: output.failed,
      },
    });
    if (token !== uploadToken) return;
    const audio = res.project?.audio;
    if (audio) useProject.getState().setRemote({ url: audio.url, streamUrl: audio.streamUrl, downloadUrl: audio.downloadUrl });
    report({ status: "saved", progress: 1 });
    lastBlob = null;
  } catch (err) {
    report({ status: "error", progress: 0, error: message(err) });
  }
}

export const retryAudioUpload = () => saveAudiobook();
