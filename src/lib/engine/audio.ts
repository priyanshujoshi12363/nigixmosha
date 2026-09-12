export const SAMPLE_RATE = 24000;

export async function decodeToMono(data: ArrayBuffer): Promise<Float32Array> {
  const ctx = new OfflineAudioContext(1, 1, SAMPLE_RATE);
  const buf = await ctx.decodeAudioData(data.slice(0));
  const out = new Float32Array(buf.length);
  const channels = buf.numberOfChannels;
  for (let c = 0; c < channels; c++) {
    const ch = buf.getChannelData(c);
    for (let i = 0; i < ch.length; i++) out[i] += ch[i] / channels;
  }
  return out;
}

export function concat(parts: Float32Array[]): Float32Array {
  const total = parts.reduce((n, p) => n + p.length, 0);
  const out = new Float32Array(total);
  let offset = 0;
  for (const p of parts) {
    out.set(p, offset);
    offset += p.length;
  }
  return out;
}

export function silence(ms: number) {
  return new Float32Array(Math.max(0, Math.round((ms / 1000) * SAMPLE_RATE)));
}

export function trimSilence(samples: Float32Array, threshold = 0.012, padMs = 35): Float32Array {
  let start = 0;
  let end = samples.length - 1;
  while (start < samples.length && Math.abs(samples[start]) < threshold) start++;
  while (end > start && Math.abs(samples[end]) < threshold) end--;
  if (start >= end) return samples;
  const pad = Math.round((padMs / 1000) * SAMPLE_RATE);
  return samples.subarray(Math.max(0, start - pad), Math.min(samples.length, end + pad + 1));
}

export function normalizeLoudness(samples: Float32Array, targetRms = 0.1): Float32Array {
  let sum = 0;
  let count = 0;
  let peak = 0;
  for (let i = 0; i < samples.length; i++) {
    const a = Math.abs(samples[i]);
    if (a > peak) peak = a;
    if (a > 0.01) {
      sum += samples[i] * samples[i];
      count++;
    }
  }
  if (!count || !peak) return samples;
  const rms = Math.sqrt(sum / count);
  const gain = Math.min(targetRms / rms, 0.97 / peak, 4);
  if (Math.abs(gain - 1) < 0.02) return samples;
  const out = new Float32Array(samples.length);
  for (let i = 0; i < samples.length; i++) out[i] = samples[i] * gain;
  return out;
}

export function computePeaks(samples: Float32Array, bins = 600): number[] {
  const size = Math.max(1, Math.floor(samples.length / bins));
  const peaks: number[] = [];
  for (let b = 0; b < bins; b++) {
    let max = 0;
    const from = b * size;
    const to = Math.min(samples.length, from + size);
    for (let i = from; i < to; i += 4) {
      const a = Math.abs(samples[i]);
      if (a > max) max = a;
    }
    peaks.push(max);
  }
  const top = Math.max(...peaks, 0.0001);
  return peaks.map((p) => p / top);
}

export function encodeWav(samples: Float32Array, rate = SAMPLE_RATE): Blob {
  const buffer = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(buffer);
  const write = (offset: number, s: string) => {
    for (let i = 0; i < s.length; i++) view.setUint8(offset + i, s.charCodeAt(i));
  };
  write(0, "RIFF");
  view.setUint32(4, 36 + samples.length * 2, true);
  write(8, "WAVE");
  write(12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, rate, true);
  view.setUint32(28, rate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  write(36, "data");
  view.setUint32(40, samples.length * 2, true);
  let o = 44;
  for (let i = 0; i < samples.length; i++, o += 2) {
    const s = Math.max(-1, Math.min(1, samples[i]));
    view.setInt16(o, s < 0 ? s * 0x8000 : s * 0x7fff, true);
  }
  return new Blob([buffer], { type: "audio/wav" });
}
