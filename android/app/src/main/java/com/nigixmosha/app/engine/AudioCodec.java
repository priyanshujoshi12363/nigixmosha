package com.nigixmosha.app.engine;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public final class AudioCodec {
    public static final int RATE = 24000;

    private AudioCodec() {
    }

    public static short[] decode(byte[] data) throws IOException {
        if (data.length > 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'A' && data[10] == 'V' && data[11] == 'E') {
            short[] wav = decodeWav(data);
            if (wav != null) return wav;
        }
        return decodeCompressed(data);
    }

    private static short[] decodeWav(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12;
        int format = -1;
        int channels = 1;
        int rate = RATE;
        int bits = 16;
        int dataStart = -1;
        int dataLen = 0;
        while (pos + 8 <= data.length) {
            String id = new String(data, pos, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = b.getInt(pos + 4);
            int body = pos + 8;
            if (id.equals("fmt ") && body + 16 <= data.length) {
                format = b.getShort(body) & 0xFFFF;
                channels = Math.max(1, b.getShort(body + 2) & 0xFFFF);
                rate = b.getInt(body + 4);
                bits = b.getShort(body + 14) & 0xFFFF;
                if (format == 0xFFFE && body + 26 <= data.length) format = b.getShort(body + 24) & 0xFFFF;
            } else if (id.equals("data")) {
                dataStart = body;
                dataLen = size < 0 || body + size > data.length ? data.length - body : size;
                break;
            }
            if (size < 0) return null;
            pos = body + size + (size & 1);
        }
        if (dataStart < 0 || format == -1 || rate <= 0) return null;
        int bytesPer = bits / 8;
        if (bytesPer <= 0) return null;
        int frames = dataLen / (bytesPer * channels);
        float[] mono = new float[frames];
        for (int f = 0; f < frames; f++) {
            float sum = 0;
            for (int c = 0; c < channels; c++) {
                int at = dataStart + (f * channels + c) * bytesPer;
                float v;
                if (format == 3 && bits == 32) v = b.getFloat(at);
                else if (bits == 16) v = b.getShort(at) / 32768f;
                else if (bits == 24) {
                    int s = (data[at] & 0xFF) | ((data[at + 1] & 0xFF) << 8) | (data[at + 2] << 16);
                    v = s / 8388608f;
                } else if (bits == 32) v = b.getInt(at) / 2147483648f;
                else if (bits == 8) v = ((data[at] & 0xFF) - 128) / 128f;
                else return null;
                sum += v;
            }
            mono[f] = sum / channels;
        }
        return toShorts(resample(mono, rate, RATE));
    }

    private static short[] decodeCompressed(byte[] data) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(new BytesSource(data));
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    format = f;
                    break;
                }
            }
            if (track < 0) throw new IOException("The voice engine returned audio this device can't read");
            extractor.selectTrack(track);
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();

            int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int rate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : RATE;
            boolean floatPcm = false;
            FloatGrow out = new FloatGrow(RATE * 8);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            int idle = 0;
            while (!outputDone) {
                if (!inputDone) {
                    int in = codec.dequeueInputBuffer(10_000);
                    if (in >= 0) {
                        ByteBuffer buf = codec.getInputBuffer(in);
                        int size = buf == null ? -1 : extractor.readSampleData(buf, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(in, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int outIndex = codec.dequeueOutputBuffer(info, 10_000);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        floatPcm = of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT;
                    }
                } else if (outIndex >= 0) {
                    idle = 0;
                    ByteBuffer buf = codec.getOutputBuffer(outIndex);
                    if (buf != null && info.size > 0) {
                        buf.position(info.offset);
                        buf.limit(info.offset + info.size);
                        ByteBuffer ordered = buf.slice().order(ByteOrder.nativeOrder());
                        int ch = Math.max(1, channels);
                        if (floatPcm) {
                            int frames = info.size / 4 / ch;
                            for (int f = 0; f < frames; f++) {
                                float sum = 0;
                                for (int c = 0; c < ch; c++) sum += ordered.getFloat((f * ch + c) * 4);
                                out.add(sum / ch);
                            }
                        } else {
                            int frames = info.size / 2 / ch;
                            for (int f = 0; f < frames; f++) {
                                float sum = 0;
                                for (int c = 0; c < ch; c++) sum += ordered.getShort((f * ch + c) * 2) / 32768f;
                                out.add(sum / ch);
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                } else if (inputDone && ++idle > 200) {
                    outputDone = true;
                }
            }
            return toShorts(resample(out.toArray(), rate, RATE));
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new IOException("Couldn't decode the voice audio", e);
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception ignored) {
                }
                codec.release();
            }
            extractor.release();
        }
    }

    static float[] resample(float[] in, int from, int to) {
        if (from == to || in.length == 0) return in;
        int outLen = (int) Math.max(1, Math.round((long) in.length * (double) to / from));
        float[] out = new float[outLen];
        double step = (double) from / to;
        for (int i = 0; i < outLen; i++) {
            double src = i * step;
            int i0 = (int) src;
            if (i0 >= in.length - 1) {
                out[i] = in[in.length - 1];
                continue;
            }
            double frac = src - i0;
            out[i] = (float) (in[i0] + (in[i0 + 1] - in[i0]) * frac);
        }
        return out;
    }

    private static short[] toShorts(float[] in) {
        short[] out = new short[in.length];
        for (int i = 0; i < in.length; i++) out[i] = toShort(in[i]);
        return out;
    }

    private static short toShort(float v) {
        float s = Math.max(-1f, Math.min(1f, v));
        return (short) (s < 0 ? s * 0x8000 : s * 0x7fff);
    }

    public static short[] concat(List<short[]> parts) {
        int total = 0;
        for (short[] p : parts) total += p.length;
        short[] out = new short[total];
        int offset = 0;
        for (short[] p : parts) {
            System.arraycopy(p, 0, out, offset, p.length);
            offset += p.length;
        }
        return out;
    }

    public static int silenceSamples(int ms) {
        return Math.max(0, Math.round(ms / 1000f * RATE));
    }

    public static short[] trimSilence(short[] samples) {
        int threshold = Math.round(0.012f * 32768);
        int start = 0;
        int end = samples.length - 1;
        while (start < samples.length && Math.abs(samples[start]) < threshold) start++;
        while (end > start && Math.abs(samples[end]) < threshold) end--;
        if (start >= end) return samples;
        int pad = Math.round(0.035f * RATE);
        int from = Math.max(0, start - pad);
        int to = Math.min(samples.length, end + pad + 1);
        short[] out = new short[to - from];
        System.arraycopy(samples, from, out, 0, out.length);
        return out;
    }

    public static short[] normalizeLoudness(short[] samples) {
        double sum = 0;
        long count = 0;
        double peak = 0;
        for (short s : samples) {
            double a = Math.abs(s / 32768.0);
            if (a > peak) peak = a;
            if (a > 0.01) {
                sum += a * a;
                count++;
            }
        }
        if (count == 0 || peak == 0) return samples;
        double rms = Math.sqrt(sum / count);
        double gain = Math.min(Math.min(0.1 / rms, 0.97 / peak), 4);
        if (Math.abs(gain - 1) < 0.02) return samples;
        short[] out = new short[samples.length];
        for (int i = 0; i < samples.length; i++) out[i] = toShort((float) (samples[i] / 32768.0 * gain));
        return out;
    }

    public static final class WavWriter implements Closeable {
        private final OutputStream out;
        private final byte[] buffer = new byte[16384];
        private int fill;
        private final float[] peaks;
        private final int binSize;
        private long written;

        public WavWriter(File file, long totalSamples, int bins) throws IOException {
            out = new BufferedOutputStream(new FileOutputStream(file), 1 << 16);
            peaks = new float[bins];
            binSize = (int) Math.max(1, totalSamples / bins);
            long dataBytes = totalSamples * 2;
            ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            h.put(new byte[]{'R', 'I', 'F', 'F'}).putInt((int) (36 + dataBytes)).put(new byte[]{'W', 'A', 'V', 'E'});
            h.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) 1)
                    .putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
            h.put(new byte[]{'d', 'a', 't', 'a'}).putInt((int) dataBytes);
            out.write(h.array());
        }

        public void write(short[] samples) throws IOException {
            for (short s : samples) put(s);
        }

        public void silence(int count) throws IOException {
            for (int i = 0; i < count; i++) put((short) 0);
        }

        private void put(short s) throws IOException {
            long bin = written / binSize;
            if (bin < peaks.length && (written - bin * binSize) % 4 == 0) {
                float a = Math.abs(s / 32768f);
                if (a > peaks[(int) bin]) peaks[(int) bin] = a;
            }
            written++;
            buffer[fill++] = (byte) (s & 0xFF);
            buffer[fill++] = (byte) ((s >> 8) & 0xFF);
            if (fill == buffer.length) {
                out.write(buffer, 0, fill);
                fill = 0;
            }
        }

        public float[] peaks() {
            float top = 0.0001f;
            for (float p : peaks) top = Math.max(top, p);
            float[] norm = new float[peaks.length];
            for (int i = 0; i < peaks.length; i++) norm[i] = peaks[i] / top;
            return norm;
        }

        @Override
        public void close() throws IOException {
            if (fill > 0) out.write(buffer, 0, fill);
            fill = 0;
            out.close();
        }
    }

    public static void writeWav(File file, short[] samples) throws IOException {
        try (WavWriter w = new WavWriter(file, samples.length, 1)) {
            w.write(samples);
        }
    }

    private static final class FloatGrow {
        private float[] data;
        private int size;

        FloatGrow(int capacity) {
            data = new float[capacity];
        }

        void add(float v) {
            if (size == data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
            data[size++] = v;
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }

    private static final class BytesSource extends MediaDataSource {
        private final byte[] data;

        BytesSource(byte[] data) {
            this.data = data;
        }

        @Override
        public int readAt(long position, byte[] buffer, int offset, int size) {
            if (position >= data.length) return -1;
            int n = (int) Math.min(size, data.length - position);
            System.arraycopy(data, (int) position, buffer, offset, n);
            return n;
        }

        @Override
        public long getSize() {
            return data.length;
        }

        @Override
        public void close() {
        }
    }
}
