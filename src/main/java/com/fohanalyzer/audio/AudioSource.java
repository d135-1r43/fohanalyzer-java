package com.fohanalyzer.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * Live capture from a {@link TargetDataLine}, exposing per-band dBFS and broadband
 * RMS the way {@code src/lib/audioInput.js} does for a Web Audio {@code AnalyserNode}.
 *
 * <p>A daemon thread continuously reads PCM frames and maintains the most recent
 * {@link #FFT_SIZE} samples of the selected channel in a ring buffer. {@link #readBands}
 * / {@link #readRMS} snapshot that buffer and run the transform off the capture thread.
 */
public final class AudioSource {

    public static final int FFT_SIZE = 16384;
    private static final float TARGET_RATE = 48000f;

    private TargetDataLine line;
    private Thread captureThread;
    private volatile boolean running;

    private volatile int channelCount = 1;
    private volatile int channelIndex = 0;
    private float sampleRate = TARGET_RATE;
    private String deviceId;
    private volatile String error;

    // Ring buffer of the most recent FFT_SIZE mono samples for the selected channel.
    private final float[] ring = new float[FFT_SIZE];
    private int ringPos = 0;
    private final Object ringLock = new Object();

    public boolean isConnected() { return line != null && running; }
    public int getChannelCount() { return channelCount; }
    public int getChannelIndex() { return channelIndex; }
    public String getError() { return error; }
    public float getSampleRate() { return sampleRate; }

    /**
     * Open {@code device} and begin capturing. If already connected to the same device,
     * only the channel selection changes. Returns the channel count (1 on failure).
     */
    public synchronized int connect(AudioDevice device, int channelIndex) {
        if (isConnected() && device != null && device.id().equals(deviceId)) {
            setChannel(channelIndex);
            return channelCount;
        }
        disconnect();
        error = null;
        try {
            Mixer mixer = AudioSystem.getMixer(device.mixerInfo());
            AudioFormat format = chooseFormat(mixer);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            line = (TargetDataLine) mixer.getLine(info);
            line.open(format, (int) (format.getFrameSize() * format.getSampleRate() * 0.1));
            line.start();

            sampleRate = format.getSampleRate();
            channelCount = format.getChannels();
            this.channelIndex = Math.min(Math.max(0, channelIndex), channelCount - 1);
            deviceId = device.id();

            running = true;
            captureThread = new Thread(() -> captureLoop(format), "audio-capture");
            captureThread.setDaemon(true);
            captureThread.start();
        } catch (Exception e) {
            error = e.getMessage();
            disconnect();
        }
        return channelCount;
    }

    /**
     * Open 16-bit PCM at the device's full channel count (so every input of a
     * multi-channel interface like a Scarlett 18i20 or Behringer Wing is reachable),
     * preferring 48 kHz then 44.1 kHz. Channel counts are read from the line's own
     * supported formats rather than guessed, then tried largest-first.
     */
    private AudioFormat chooseFormat(Mixer mixer) {
        int maxCh = maxSupportedChannels(mixer);
        // Distinct channel counts to try, largest first: full count, then stereo, then mono.
        java.util.List<Integer> tries = new java.util.ArrayList<>();
        if (maxCh >= 1) tries.add(maxCh);
        if (maxCh > 2 && !tries.contains(2)) tries.add(2);
        if (!tries.contains(1)) tries.add(1);
        for (float rate : new float[]{TARGET_RATE, 44100f}) {
            for (int ch : tries) {
                AudioFormat f = new AudioFormat(rate, 16, ch, true, false);
                if (mixer.isLineSupported(new DataLine.Info(TargetDataLine.class, f))) return f;
            }
        }
        // Last resort: full count at the target rate, let the system negotiate.
        return new AudioFormat(TARGET_RATE, 16, Math.max(1, maxCh), true, false);
    }

    /** Largest channel count advertised by any of the mixer's capture lines (default 2). */
    private static int maxSupportedChannels(Mixer mixer) {
        int max = 0;
        for (javax.sound.sampled.Line.Info li : mixer.getTargetLineInfo()) {
            if (li instanceof DataLine.Info dli
                && TargetDataLine.class.isAssignableFrom(dli.getLineClass())) {
                for (AudioFormat f : dli.getFormats()) {
                    if (f.getChannels() != javax.sound.sampled.AudioSystem.NOT_SPECIFIED) {
                        max = Math.max(max, f.getChannels());
                    }
                }
            }
        }
        return max > 0 ? max : 2;
    }

    public synchronized void setChannel(int channelIndex) {
        if (channelCount <= 1) { this.channelIndex = 0; return; }
        this.channelIndex = Math.min(Math.max(0, channelIndex), channelCount - 1);
    }

    public synchronized void disconnect() {
        running = false;
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        channelCount = 1;
        channelIndex = 0;
        deviceId = null;
        synchronized (ringLock) {
            java.util.Arrays.fill(ring, 0f);
            ringPos = 0;
        }
    }

    private void captureLoop(AudioFormat format) {
        int frameSize = format.getFrameSize();
        int ch = format.getChannels();
        int bytesPerSample = frameSize / ch;
        byte[] buf = new byte[frameSize * 2048];
        try {
            while (running && line != null) {
                int read = line.read(buf, 0, buf.length);
                if (read <= 0) continue;
                int frames = read / frameSize;
                int idx = channelIndex;
                synchronized (ringLock) {
                    for (int fr = 0; fr < frames; fr++) {
                        int off = fr * frameSize + idx * bytesPerSample;
                        float s = sampleToFloat(buf, off, bytesPerSample);
                        ring[ringPos] = s;
                        ringPos = (ringPos + 1) % FFT_SIZE;
                    }
                }
            }
        } catch (Exception ignored) {
            // line closed underneath us — exit quietly
        }
    }

    /** Little-endian signed PCM sample -> [-1, 1] float. Handles 8/16/24/32-bit. */
    private static float sampleToFloat(byte[] b, int off, int bytesPerSample) {
        return switch (bytesPerSample) {
            case 1 -> b[off] / 128f;
            case 2 -> (short) ((b[off] & 0xff) | (b[off + 1] << 8)) / 32768f;
            case 3 -> ((b[off] & 0xff) | ((b[off + 1] & 0xff) << 8) | (b[off + 2] << 16)) / 8388608f;
            default -> ((b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16) | (b[off + 3] << 24)) / 2147483648f;
        };
    }

    /** Latest FFT window in chronological order. */
    private float[] snapshotWindow() {
        float[] w = new float[FFT_SIZE];
        synchronized (ringLock) {
            int start = ringPos;
            for (int i = 0; i < FFT_SIZE; i++) {
                w[i] = ring[(start + i) % FFT_SIZE];
            }
        }
        return w;
    }

    /** Per-band dBFS levels, or {@code null} if not connected. */
    public float[] readBands(double[] centers, int frac) {
        if (!isConnected()) return null;
        double[] spec = AudioDsp.spectrumDb(snapshotWindow());
        double binHz = sampleRate / FFT_SIZE;
        return AudioDsp.bands(spec, binHz, centers, frac);
    }

    /** Broadband RMS in dBFS, or {@code null} if not connected. */
    public Double readRMS() {
        if (!isConnected()) return null;
        return AudioDsp.rmsDb(snapshotWindow());
    }

    /**
     * Fundamental of whatever is currently ringing, or {@code null} if not connected or
     * nothing steady is present. Drives the ring-out assist's "detect from mic" action.
     */
    public AudioDsp.Pitch readPitch() {
        if (!isConnected()) return null;
        return AudioDsp.detectPitch(snapshotWindow(), sampleRate);
    }
}
