package com.nigixmosha.app.data.model;

import com.nigixmosha.app.engine.model.Types;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProjectRecord {
    public String id;
    public String title;
    public String text;
    public String languageHint;
    public Types.Analysis analysis;
    public List<Types.Segment> segments = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();
    public Map<String, Types.CastEntry> cast = new LinkedHashMap<>();
    public Types.CastFor castFor;
    public Map<String, String> castReasons = new LinkedHashMap<>();
    public String castBy;
    public boolean shareNarrator;
    public StoredAudio audio;
    public String createdAt;
    public String updatedAt;

    public static final class StoredAudio {
        public String publicId;
        public long version;
        public String url;
        public String streamUrl;
        public String downloadUrl;
        public long bytes;
        public double duration;
        public List<Types.TimelineEntry> timeline = new ArrayList<>();
        public List<Double> peaks = new ArrayList<>();
        public String provider;
        public int failed;
        public String createdAt;
    }
}
