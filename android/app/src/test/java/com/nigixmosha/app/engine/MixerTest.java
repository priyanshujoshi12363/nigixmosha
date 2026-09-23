package com.nigixmosha.app.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.nigixmosha.app.engine.model.Types;

import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;

public class MixerTest {
    private static final int RATE = AudioCodec.RATE;

    @Before
    public void loadCatalog() throws Exception {
        Sounds.load(new FileInputStream("src/main/assets/sounds.json"));
    }

    private static short[] tone(int seconds, double amplitude) {
        short[] out = new short[seconds * RATE];
        for (int i = 0; i < out.length; i++) out[i] = (short) (Math.sin(i * 2 * Math.PI * 220 / RATE) * amplitude * 32767);
        return out;
    }

    private static double rms(short[] samples, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) sum += (double) samples[i] * samples[i];
        return Math.sqrt(sum / Math.max(1, to - from));
    }

    private static String firstTag(String kind) {
        Sounds sounds = Sounds.get();
        String menu = sounds.menu(kind);
        return menu.substring(0, menu.indexOf(' '));
    }

    @Test
    public void catalogLoads() {
        assertFalse("catalog should not be empty", Sounds.get().isEmpty());
        assertTrue(Sounds.get().tag(firstTag("bed")).variants.size() > 0);
    }

    @Test
    public void resolvesLooseTags() {
        String bed = firstTag("bed");
        assertEquals(bed, Sounds.get().resolve(bed.replace('_', ' '), null));
        assertEquals(bed, Sounds.get().resolve("nothing-like-this", bed));
        assertEquals(null, Sounds.get().resolve("zzzz", "qqqq"));
    }

    @Test
    public void bedPlaysUnderSilenceAndDucksUnderVoice() throws Exception {
        String bed = firstTag("bed");
        Types.Soundscape plan = new Types.Soundscape();
        Types.SoundScene scene = new Types.SoundScene();
        scene.from = 0;
        scene.to = 0;
        scene.tag = bed;
        scene.intensity = 1.0;
        plan.scenes.add(scene);

        short[] clip = tone(3, 0.5);
        double[] starts = {4.0};
        double[] ends = {8.0};
        Mixer mixer = Mixer.build(plan, "cinematic", starts, ends, (tag, nth) -> clip, null, new Cancel());
        assertTrue(mixer.hasWork());
        assertTrue(mixer.used().contains(bed));

        short[] quiet = new short[10 * RATE];
        mixer.process(quiet, 0);
        double bedOnly = rms(quiet, 5 * RATE, 6 * RATE);
        assertTrue("ambience should be audible in a gap: " + bedOnly, bedOnly > 60);
        assertEquals("nothing before the scene lead-in", 0, rms(quiet, 0, (int) (3.0 * RATE)), 0.001);

        Mixer ducking = Mixer.build(plan, "cinematic", starts, ends, (tag, nth) -> clip, null, new Cancel());
        short[] loud = tone(10, 0.6);
        double before = rms(loud, 5 * RATE, 6 * RATE);
        ducking.process(loud, 0);
        double after = rms(loud, 5 * RATE, 6 * RATE);
        double added = after - before;
        assertTrue("ambience under speech must be quieter than in a gap: " + added + " vs " + bedOnly,
                Math.abs(added) < bedOnly * 0.8);
    }

    @Test
    public void loopHasNoSilentSeam() throws Exception {
        String bed = firstTag("bed");
        Types.Soundscape plan = new Types.Soundscape();
        Types.SoundScene scene = new Types.SoundScene();
        scene.from = 0;
        scene.to = 0;
        scene.tag = bed;
        plan.scenes.add(scene);

        short[] clip = tone(2, 0.6);
        Mixer mixer = Mixer.build(plan, "cinematic", new double[]{1.0}, new double[]{20.0}, (tag, nth) -> clip, null, new Cancel());
        short[] track = new short[22 * RATE];
        mixer.process(track, 0);
        double lowest = Double.MAX_VALUE;
        for (int second = 4; second < 18; second++) {
            double level = rms(track, second * RATE, (second + 1) * RATE);
            if (level < lowest) lowest = level;
        }
        assertTrue("every second of the loop should carry sound, weakest was " + lowest, lowest > 40);
    }

    @Test
    public void cueLandsBeforeItsLine() throws Exception {
        String shot = firstTag("shot");
        Types.Soundscape plan = new Types.Soundscape();
        Types.SoundCue cue = new Types.SoundCue();
        cue.at = 0;
        cue.tag = shot;
        cue.placement = "before";
        cue.gain = 1.0;
        plan.cues.add(cue);

        short[] clip = tone(1, 0.7);
        Mixer mixer = Mixer.build(plan, "cinematic", new double[]{5.0}, new double[]{7.0}, (tag, nth) -> clip, null, new Cancel());
        short[] track = new short[10 * RATE];
        mixer.process(track, 0);
        double beforeLine = rms(track, (int) (4.2 * RATE), (int) (4.7 * RATE));
        double duringLine = rms(track, (int) (5.5 * RATE), (int) (6.5 * RATE));
        assertTrue("effect should sound just before the line: " + beforeLine, beforeLine > 100);
        assertEquals("nothing should bleed into the line", 0, duringLine, 0.001);
    }

    @Test
    public void neverClips() throws Exception {
        String bed = firstTag("bed");
        Types.Soundscape plan = new Types.Soundscape();
        Types.SoundScene scene = new Types.SoundScene();
        scene.from = 0;
        scene.to = 0;
        scene.tag = bed;
        scene.intensity = 1.0;
        plan.scenes.add(scene);
        Mixer mixer = Mixer.build(plan, "cinematic", new double[]{0.0}, new double[]{9.0}, (tag, nth) -> tone(3, 1.0), null, new Cancel());
        short[] loud = tone(10, 0.99);
        mixer.process(loud, 0);
        for (short s : loud) assertTrue("sample out of range: " + s, s <= 31785 && s >= -31785);
    }
}
