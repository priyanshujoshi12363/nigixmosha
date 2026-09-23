package com.nigixmosha.app.engine.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Types {
    public static final String NARRATOR_ID = "narrator";
    public static final List<String> EMOTIONS = Collections.unmodifiableList(Arrays.asList(
            "neutral", "happy", "sad", "angry", "fearful", "surprised", "excited",
            "whisper", "calm", "serious", "sarcastic", "tender", "shouting"));
    public static final List<String> AGES = Collections.unmodifiableList(Arrays.asList(
            "child", "teen", "young", "adult", "middle", "elderly"));
    public static final List<String> GENDERS = Collections.unmodifiableList(Arrays.asList(
            "female", "male", "neutral"));

    private Types() {
    }

    public static final class CastCharacter {
        public String id;
        public String name;
        public List<String> aliases = new ArrayList<>();
        public String gender = "neutral";
        public String age = "adult";
        public String role = "minor";
        public List<String> personality = new ArrayList<>();
        public String speakingStyle = "";
        public String voiceDescription = "";
        public String color;
        public int lineCount;
    }

    public static final class Segment {
        public String id;
        public String speaker;
        public String emotion = "neutral";
        public String text;
        public Boolean para;
        public String lang;

        public boolean isPara() {
            return para != null && para;
        }
    }

    public static final class NarratorProfile {
        public String gender = "neutral";
        public String tone = "";
        public String characterId;
    }

    public static final class Analysis {
        public String title;
        public String language;
        public List<String> mixedLanguages = new ArrayList<>();
        public String pov = "third";
        public String summary = "";
        public NarratorProfile narrator = new NarratorProfile();
        public List<CastCharacter> characters = new ArrayList<>();
        public String engine;

        public CastCharacter character(String id) {
            if (id == null || characters == null) return null;
            for (CastCharacter c : characters) if (id.equals(c.id)) return c;
            return null;
        }
    }

    public static final class CastEntry {
        public String voiceId;
        public double pitch;
        public double rate;

        public CastEntry() {
        }

        public CastEntry(String voiceId, double pitch, double rate) {
            this.voiceId = voiceId;
            this.pitch = pitch;
            this.rate = rate;
        }

        public CastEntry copy() {
            return new CastEntry(voiceId, pitch, rate);
        }
    }

    public static final class VoiceProfile {
        public String id;
        public String name;
        public String gender = "neutral";
        public String age;
        public List<String> langs = new ArrayList<>();
        public List<String> tags = new ArrayList<>();
        public Boolean multilingual;

        public boolean isMultilingual() {
            return multilingual != null && multilingual;
        }
    }

    public static final class ProviderMeta {
        public String id;
        public String name;
        public String tagline;
        public String defaultBaseUrl;
        public boolean editableBaseUrl;
        public boolean needsKey;
        public List<String> models = new ArrayList<>();
        public String defaultModel;
        public Boolean free;
        public boolean anyLanguage;
        public List<String> languages;
        public int maxChars;
        public boolean instructable;
        public boolean liveVoices;
        public String keyUrl;
        public int concurrency;

        public boolean isFree() {
            return free != null && free;
        }
    }

    public static final class SynthesisStyle {
        public double pitch;
        public double rate;
        public double volume;
        public String emotion;
        public String instructions;
    }

    public static final class TimelineEntry {
        public String segmentId;
        public String speaker;
        public double start;
        public double end;
    }

    public static final class Language {
        public String code;
        public String name;
        @SerializedName("native")
        public String nativeName;
    }

    public static final class Sample {
        public String id;
        public String title;
        public String label;
        public String language;
        public String text;
    }

    public static final class CastFor {
        public String provider;
        public String model;

        public CastFor() {
        }

        public CastFor(String provider, String model) {
            this.provider = provider;
            this.model = model;
        }
    }

    public static final class ProviderConfig {
        public String apiKey = "";
        public String baseUrl = "";
        public String model = "";
    }

    public static final class Advanced {
        public int ttsConcurrency = 4;
        public int lineGapMs = 200;
        public int speakerGapMs = 340;
        public int paragraphGapMs = 600;
        public boolean normalize = true;
        public String soundscape = "subtle";
    }

    public static final class SoundVariant {
        public long id;
        public String url;
        public double seconds;
        public int channels;
        public String name;
        public String user;
        public String page;
        public String license;
    }

    public static final class SoundTag {
        public String tag;
        public String kind;
        public String label;
        public double gain;
        public List<SoundVariant> variants = new ArrayList<>();
    }

    public static final class SoundCatalog {
        public int version;
        public String generatedAt;
        public String source;
        public List<SoundTag> tags = new ArrayList<>();
    }

    public static final class SoundScene {
        public int from;
        public int to;
        public String tag;
        public double intensity = 0.6;
        public String wanted = "";
    }

    public static final class SoundCue {
        public int at;
        public String tag;
        public String placement = "before";
        public double gain = 0.8;
        public String wanted = "";
    }

    public static final class Soundscape {
        public List<SoundScene> scenes = new ArrayList<>();
        public List<SoundCue> cues = new ArrayList<>();
        public List<String> missing = new ArrayList<>();
        public String engine = "";

        public boolean isEmpty() {
            return scenes.isEmpty() && cues.isEmpty();
        }
    }
}
