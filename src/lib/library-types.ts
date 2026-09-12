import type { TTSProviderId } from "./providers";
import type { Analysis, CastEntry, Segment, TimelineEntry } from "./types";

export interface ProjectPayload {
  title?: string;
  text?: string;
  languageHint?: string;
  analysis?: Analysis | null;
  segments?: Segment[];
  warnings?: string[];
  cast?: Record<string, CastEntry>;
  castFor?: { provider: TTSProviderId; model: string } | null;
  castReasons?: Record<string, string>;
  castBy?: "ai" | "rules" | null;
  shareNarrator?: boolean;
}

export interface AudioUploadPayload {
  publicId: string;
  version: number;
  bytes: number;
  duration: number;
  timeline: TimelineEntry[];
  peaks: number[];
  provider: TTSProviderId;
  failed: number;
}

export interface StoredAudio {
  publicId: string;
  version: number;
  url: string;
  streamUrl: string;
  downloadUrl: string;
  bytes: number;
  duration: number;
  timeline: TimelineEntry[];
  peaks: number[];
  provider: TTSProviderId;
  failed: number;
  createdAt: string;
}

export interface ProjectRecord extends Required<ProjectPayload> {
  id: string;
  audio: StoredAudio | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectSummary {
  id: string;
  title: string;
  language: string | null;
  characters: { name: string; color: string }[];
  segments: number;
  words: number;
  audio: Pick<StoredAudio, "url" | "streamUrl" | "downloadUrl" | "duration"> | null;
  createdAt: string;
  updatedAt: string;
}

export interface UploadTicket {
  uploadUrl: string;
  apiKey: string;
  params: Record<string, string | number>;
  signature: string;
  maxBytes: number;
}

export interface AccountUser {
  id: string;
  username: string;
  displayName: string;
  createdAt: string;
}

export interface ServerConfig {
  brain: boolean;
  serverKeys: Record<string, boolean>;
  storage: { db: boolean; media: boolean };
}
