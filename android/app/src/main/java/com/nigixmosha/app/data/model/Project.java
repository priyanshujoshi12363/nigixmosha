package com.nigixmosha.app.data.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Project {
    public String id;
    public String title;
    public String language;
    public List<CastMember> characters;
    public int segments;
    public int words;
    public AudioInfo audio;
    public String createdAt;
    public String updatedAt;

    public String displayTitle() {
        return title == null || title.trim().isEmpty() ? "Untitled chapter" : title.trim();
    }

    public List<CastMember> cast() {
        return characters == null ? Collections.emptyList() : characters;
    }

    public boolean hasAudio() {
        return audio != null && audioUrl() != null;
    }

    public String audioUrl() {
        if (audio == null) return null;
        if (audio.streamUrl != null && !audio.streamUrl.isEmpty()) return audio.streamUrl;
        if (audio.url != null && !audio.url.isEmpty()) return audio.url;
        return null;
    }

    public long durationMs() {
        return audio == null ? 0 : Math.round(audio.duration * 1000);
    }

    public boolean sameContent(Project other) {
        return other != null
                && Objects.equals(id, other.id)
                && Objects.equals(title, other.title)
                && Objects.equals(language, other.language)
                && Objects.equals(updatedAt, other.updatedAt)
                && words == other.words
                && segments == other.segments
                && cast().size() == other.cast().size()
                && hasAudio() == other.hasAudio();
    }
}
