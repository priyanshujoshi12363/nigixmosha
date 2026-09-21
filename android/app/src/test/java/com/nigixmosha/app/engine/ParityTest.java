package com.nigixmosha.app.engine;

import com.google.gson.GsonBuilder;
import com.nigixmosha.app.engine.model.Types;

import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ParityTest {
    @Test
    public void writeParity() throws Exception {
        try (InputStreamReader r = new InputStreamReader(new FileInputStream("src/main/assets/catalog.json"), StandardCharsets.UTF_8)) {
            Catalog.loadFrom(r);
        }
        Catalog cat = Catalog.get();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Types.Sample s : cat.samples) {
            Heuristic.Analyzed h = Heuristic.analyze(s.text, s.title, "");
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("detected", Lang.detectLanguage(s.text));
            r.put("paragraphs", Texts.splitParagraphs(s.text).size());
            List<Integer> chunks = new ArrayList<>();
            for (String c : Texts.chunkParagraphs(Texts.splitParagraphs(s.text), 400)) chunks.add(c.length());
            r.put("chunks", chunks);
            List<Integer> tts = new ArrayList<>();
            for (String c : Texts.splitForTTS(s.text.substring(0, Math.min(1500, s.text.length())), 200)) tts.add(c.length());
            r.put("tts", tts);
            List<Object> chars = new ArrayList<>();
            for (Types.CastCharacter c : h.analysis.characters) {
                chars.add(Arrays.asList(c.id, c.name, c.gender, c.role, String.join("/", c.personality), c.color, c.lineCount));
            }
            r.put("characters", chars);
            r.put("pov", h.analysis.pov);
            List<Object> segs = new ArrayList<>();
            for (Types.Segment g : h.segments) segs.add(Arrays.asList(g.speaker, g.emotion, g.isPara() ? 1 : 0, g.text));
            r.put("segments", segs);
            for (String pid : new String[]{"nigix", "edge"}) {
                Types.ProviderMeta meta = cat.provider(pid);
                Map<String, Types.CastEntry> cast = Casting.autoCast(h.analysis, cat.staticVoices(pid, meta.defaultModel), meta, meta.defaultModel);
                List<Object> entries = new ArrayList<>();
                for (Map.Entry<String, Types.CastEntry> e : cast.entrySet()) {
                    entries.add(Arrays.asList(e.getKey(), e.getValue().voiceId, e.getValue().pitch, e.getValue().rate));
                }
                r.put("cast_" + pid, entries);
            }
            Types.Segment first = null;
            for (Types.Segment g : h.segments) if (!Types.NARRATOR_ID.equals(g.speaker)) {
                first = g;
                break;
            }
            if (first == null) first = h.segments.get(0);
            Types.SynthesisStyle st = Direction.styleFor(first, h.analysis, new Types.CastEntry("x", 3, -2));
            r.put("style", Arrays.asList(st.pitch, st.rate, st.volume, st.emotion, st.instructions));
            out.put(s.id, r);
        }
        String path = System.getProperty("parity.out", "build/parity_java.json");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(out, w);
        }
    }
}
