import type { Analysis, CastEntry, Emotion, Segment, SynthesisStyle } from "@/lib/types";
import { NARRATOR_ID } from "@/lib/types";
import { clamp } from "@/lib/utils";
import { languageName } from "./lang";

const EMOTION_PROSODY: Record<Emotion, { pitch: number; rate: number; volume: number }> = {
  neutral: { pitch: 0, rate: 0, volume: 0 },
  happy: { pitch: 5, rate: 6, volume: 0 },
  sad: { pitch: -5, rate: -10, volume: -8 },
  angry: { pitch: 3, rate: 6, volume: 10 },
  fearful: { pitch: 6, rate: 8, volume: -4 },
  surprised: { pitch: 8, rate: 5, volume: 4 },
  excited: { pitch: 7, rate: 10, volume: 5 },
  whisper: { pitch: -3, rate: -8, volume: -30 },
  calm: { pitch: -2, rate: -5, volume: -2 },
  serious: { pitch: -3, rate: -3, volume: 0 },
  sarcastic: { pitch: 2, rate: -2, volume: 0 },
  tender: { pitch: -1, rate: -7, volume: -8 },
  shouting: { pitch: 8, rate: 8, volume: 20 },
};

const EMOTION_DIRECTION: Record<Emotion, string> = {
  neutral: "naturally, in character",
  happy: "with warmth and a smile in the voice",
  sad: "with sadness, softly and a little slower",
  angry: "with barely controlled anger, sharp and forceful",
  fearful: "with fear, breathless and trembling",
  surprised: "with genuine surprise",
  excited: "with bright excitement and energy",
  whisper: "in a hushed whisper",
  calm: "calmly and evenly",
  serious: "gravely and seriously",
  sarcastic: "with dry sarcasm",
  tender: "tenderly and gently",
  shouting: "shouting loudly",
};

export function styleFor(segment: Segment, analysis: Analysis, entry: CastEntry): SynthesisStyle {
  const isNarrator = segment.speaker === NARRATOR_ID;
  const damp = isNarrator ? 0.4 : 1;
  const mod = EMOTION_PROSODY[segment.emotion] ?? EMOTION_PROSODY.neutral;
  const lang = languageName(segment.lang ?? analysis.language);
  let instructions: string;
  if (isNarrator) {
    const tone = analysis.narrator.tone || "warm, measured storyteller";
    instructions = `You are the narrator of an audiobook: ${tone}. Speak ${lang}. Read with an engaging storytelling cadence${
      segment.emotion !== "neutral" ? `, subtly ${EMOTION_DIRECTION[segment.emotion]}` : ""
    }.`;
  } else {
    const c = analysis.characters.find((x) => x.id === segment.speaker);
    const who = c
      ? `${c.name}, ${c.age === "adult" ? "an adult" : `a ${c.age}`} ${c.gender === "neutral" ? "person" : c.gender}${
          c.personality.length ? ` who is ${c.personality.join(", ")}` : ""
        }`
      : "a character in the story";
    const manner = c?.speakingStyle ? ` Speaking style: ${c.speakingStyle}.` : "";
    const timbre = c?.voiceDescription ? ` Voice: ${c.voiceDescription}.` : "";
    instructions = `Perform as ${who}.${timbre}${manner} Speak ${lang}. Deliver this line ${EMOTION_DIRECTION[segment.emotion]}.`;
  }
  return {
    pitch: clamp(entry.pitch + mod.pitch * damp, -40, 40),
    rate: clamp(entry.rate + mod.rate * damp, -45, 50),
    volume: clamp(mod.volume * damp, -40, 30),
    emotion: segment.emotion,
    instructions,
  };
}
