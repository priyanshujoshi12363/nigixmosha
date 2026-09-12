export type Gender = "male" | "female" | "neutral";
export type AgeGroup = "child" | "teen" | "young" | "adult" | "middle" | "elderly";

export const EMOTIONS = [
  "neutral",
  "happy",
  "sad",
  "angry",
  "fearful",
  "surprised",
  "excited",
  "whisper",
  "calm",
  "serious",
  "sarcastic",
  "tender",
  "shouting",
] as const;
export type Emotion = (typeof EMOTIONS)[number];

export const NARRATOR_ID = "narrator";

export interface ProviderConfig {
  apiKey: string;
  baseUrl: string;
  model: string;
}

export interface Character {
  id: string;
  name: string;
  aliases: string[];
  gender: Gender;
  age: AgeGroup;
  role: string;
  personality: string[];
  speakingStyle: string;
  voiceDescription: string;
  color: string;
  lineCount: number;
}

export interface Segment {
  id: string;
  speaker: string;
  emotion: Emotion;
  text: string;
  para?: boolean;
  lang?: string;
}

export interface NarratorProfile {
  gender: Gender;
  tone: string;
  characterId?: string | null;
}

export interface Analysis {
  title: string;
  language: string;
  mixedLanguages: string[];
  pov: "first" | "third";
  summary: string;
  narrator: NarratorProfile;
  characters: Character[];
  engine: string;
}

export interface VoiceProfile {
  id: string;
  name: string;
  gender: Gender;
  age?: AgeGroup;
  langs: string[];
  tags: string[];
  multilingual?: boolean;
}

export interface CastEntry {
  voiceId: string;
  pitch: number;
  rate: number;
}

export interface SynthesisStyle {
  pitch: number;
  rate: number;
  volume: number;
  emotion: Emotion;
  instructions: string;
}

export interface TimelineEntry {
  segmentId: string;
  speaker: string;
  start: number;
  end: number;
}
