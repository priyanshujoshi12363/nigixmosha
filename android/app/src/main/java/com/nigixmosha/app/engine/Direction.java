package com.nigixmosha.app.engine;

import com.nigixmosha.app.engine.model.Types;

import java.util.HashMap;
import java.util.Map;

public final class Direction {
    private static final Map<String, double[]> PROSODY = new HashMap<>();
    private static final Map<String, String> MANNER = new HashMap<>();

    static {
        PROSODY.put("neutral", new double[]{0, 0, 0});
        PROSODY.put("happy", new double[]{5, 6, 0});
        PROSODY.put("sad", new double[]{-5, -10, -8});
        PROSODY.put("angry", new double[]{3, 6, 10});
        PROSODY.put("fearful", new double[]{6, 8, -4});
        PROSODY.put("surprised", new double[]{8, 5, 4});
        PROSODY.put("excited", new double[]{7, 10, 5});
        PROSODY.put("whisper", new double[]{-3, -8, -30});
        PROSODY.put("calm", new double[]{-2, -5, -2});
        PROSODY.put("serious", new double[]{-3, -3, 0});
        PROSODY.put("sarcastic", new double[]{2, -2, 0});
        PROSODY.put("tender", new double[]{-1, -7, -8});
        PROSODY.put("shouting", new double[]{8, 8, 20});

        MANNER.put("neutral", "naturally, in character");
        MANNER.put("happy", "with warmth and a smile in the voice");
        MANNER.put("sad", "with sadness, softly and a little slower");
        MANNER.put("angry", "with barely controlled anger, sharp and forceful");
        MANNER.put("fearful", "with fear, breathless and trembling");
        MANNER.put("surprised", "with genuine surprise");
        MANNER.put("excited", "with bright excitement and energy");
        MANNER.put("whisper", "in a hushed whisper");
        MANNER.put("calm", "calmly and evenly");
        MANNER.put("serious", "gravely and seriously");
        MANNER.put("sarcastic", "with dry sarcasm");
        MANNER.put("tender", "tenderly and gently");
        MANNER.put("shouting", "shouting loudly");
    }

    private Direction() {
    }

    public static Types.SynthesisStyle styleFor(Types.Segment segment, Types.Analysis analysis, Types.CastEntry entry) {
        boolean isNarrator = Types.NARRATOR_ID.equals(segment.speaker);
        double damp = isNarrator ? 0.4 : 1;
        double[] mod = PROSODY.containsKey(segment.emotion) ? PROSODY.get(segment.emotion) : PROSODY.get("neutral");
        String manner = MANNER.containsKey(segment.emotion) ? MANNER.get(segment.emotion) : MANNER.get("neutral");
        String lang = Lang.languageName(segment.lang != null ? segment.lang : analysis.language);
        String instructions;
        if (isNarrator) {
            String tone = analysis.narrator.tone == null || analysis.narrator.tone.isEmpty() ? "warm, measured storyteller" : analysis.narrator.tone;
            instructions = "You are the narrator of an audiobook: " + tone + ". Speak " + lang
                    + ". Read with an engaging storytelling cadence"
                    + (!"neutral".equals(segment.emotion) ? ", subtly " + manner : "") + ".";
        } else {
            Types.CastCharacter c = analysis.character(segment.speaker);
            String who;
            if (c != null) {
                String agePart = "adult".equals(c.age) ? "an adult" : "a " + c.age;
                String genderPart = "neutral".equals(c.gender) ? "person" : c.gender;
                String traits = c.personality.isEmpty() ? "" : " who is " + String.join(", ", c.personality);
                who = c.name + ", " + agePart + " " + genderPart + traits;
            } else {
                who = "a character in the story";
            }
            String styleNote = c != null && c.speakingStyle != null && !c.speakingStyle.isEmpty() ? " Speaking style: " + c.speakingStyle + "." : "";
            String timbre = c != null && c.voiceDescription != null && !c.voiceDescription.isEmpty() ? " Voice: " + c.voiceDescription + "." : "";
            instructions = "Perform as " + who + "." + timbre + styleNote + " Speak " + lang + ". Deliver this line " + manner + ".";
        }
        Types.SynthesisStyle style = new Types.SynthesisStyle();
        style.pitch = Util.clamp(entry.pitch + mod[0] * damp, -40, 40);
        style.rate = Util.clamp(entry.rate + mod[1] * damp, -45, 50);
        style.volume = Util.clamp(mod[2] * damp, -40, 30);
        style.emotion = segment.emotion;
        style.instructions = instructions;
        return style;
    }
}
