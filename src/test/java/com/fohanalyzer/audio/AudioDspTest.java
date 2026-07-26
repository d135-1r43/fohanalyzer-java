package com.fohanalyzer.audio;

import com.fohanalyzer.engine.Engine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioural port of src/lib/audioInput.test.js. The browser-only getUserMedia plumbing
 * is skipped; the spectral math is exercised directly with synthetic buffers.
 */
class AudioDspTest {

    private static final int N = AudioSource.FFT_SIZE;
    private static final float RATE = 48000f;

    private static float[] sine(double freq, double amp) {
        float[] x = new float[N];
        for (int i = 0; i < N; i++) {
            x[i] = (float) (amp * Math.sin(2 * Math.PI * freq * i / RATE));
        }
        return x;
    }

    @Test
    void spectrumPeaksAtTheInputTone() {
        double freq = 1000;
        double[] spec = AudioDsp.spectrumDb(sine(freq, 0.5));
        double binHz = RATE / N;
        int expected = (int) Math.round(freq / binHz);
        int peak = 0;
        for (int k = 1; k < spec.length; k++) if (spec[k] > spec[peak]) peak = k;
        assertEquals(expected, peak, 2, "spectral peak should land at the tone's bin");
    }

    @Test
    void bandsMatchCentersLengthAndClamp() {
        double[] centers = Engine.bandCenters(12);
        double[] spec = AudioDsp.spectrumDb(sine(1000, 0.5));
        float[] bands = AudioDsp.bands(spec, RATE / N, centers, 12);
        assertEquals(centers.length, bands.length);
        for (float v : bands) {
            assertTrue(v >= -95 && v <= -2, "band " + v + " out of [-95,-2]");
        }
    }

    @Test
    void bandNearToneIsLouderThanQuietBand() {
        double[] centers = Engine.bandCenters(24);
        float[] bands = AudioDsp.bands(AudioDsp.spectrumDb(sine(1000, 0.5)), RATE / N, centers, 24);
        int near = 0, far = 0;
        for (int i = 1; i < centers.length; i++) {
            if (Math.abs(centers[i] - 1000) < Math.abs(centers[near] - 1000)) near = i;
            if (Math.abs(centers[i] - 12000) < Math.abs(centers[far] - 12000)) far = i;
        }
        assertTrue(bands[near] > bands[far], "tone band should exceed an empty band");
    }

    @Test
    void silenceReadsAtFloor() {
        double[] centers = Engine.bandCenters(12);
        float[] bands = AudioDsp.bands(AudioDsp.spectrumDb(new float[N]), RATE / N, centers, 12);
        for (float v : bands) assertEquals(-95f, v, 1e-3);
    }

    @Test
    void rmsOfSilenceIsFloor() {
        assertEquals(-144, AudioDsp.rmsDb(new float[N]), 1e-9);
    }

    @Test
    void rmsOfFullScaleSineIsNearMinus3dB() {
        // A sine of amplitude 1 has RMS 1/sqrt(2) ≈ -3.01 dBFS.
        assertEquals(-3.01, AudioDsp.rmsDb(sine(1000, 1.0)), 0.2);
    }

    @Test
    void pitchDetectionFindsTheToneFrequency() {
        for (double freq : new double[]{220, 440, 1000, 2500}) {
            AudioDsp.Pitch p = AudioDsp.detectPitch(sine(freq, 0.5), RATE);
            assertNotNull(p, freq + " Hz tone should be pitched");
            assertEquals(freq, p.hz(), freq * 0.02, "detected pitch within 2% of " + freq);
            assertTrue(p.probability() > 0, "a detected pitch carries a confidence");
        }
    }

    @Test
    void pitchDetectionRejectsSilence() {
        assertNull(AudioDsp.detectPitch(new float[N], RATE));
    }

    @Test
    void pitchDetectionRejectsTooShortAWindow() {
        assertNull(AudioDsp.detectPitch(new float[AudioDsp.PITCH_BUFFER - 1], RATE));
    }
}
