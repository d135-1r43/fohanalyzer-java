package com.fohanalyzer.audio;

import org.jtransforms.fft.DoubleFFT_1D;

/**
 * Pure, testable spectral math used by {@link AudioSource}. Mirrors the browser's
 * {@code AnalyserNode.getFloatFrequencyData} (Blackman window, FFT, magnitude
 * normalised by FFT size, converted to dBFS) and the band/RMS readers in
 * {@code src/lib/audioInput.js}.
 */
public final class AudioDsp {

    private AudioDsp() {}

    /** Blackman window (α = 0.16), matching the Web Audio AnalyserNode definition. */
    public static void applyBlackman(double[] x) {
        int n = x.length;
        for (int i = 0; i < n; i++) {
            double w = 0.42
                - 0.5 * Math.cos((2 * Math.PI * i) / n)
                + 0.08 * Math.cos((4 * Math.PI * i) / n);
            x[i] *= w;
        }
    }

    /**
     * Magnitude spectrum in dBFS for the given real time-domain window.
     * Returns {@code fftSize/2} bins (bin k centred at {@code k * sampleRate / fftSize}).
     * The window is copied and Blackman-windowed before the transform.
     */
    public static double[] spectrumDb(float[] window) {
        int n = window.length;
        double[] a = new double[n];
        for (int i = 0; i < n; i++) a[i] = window[i];
        applyBlackman(a);

        DoubleFFT_1D fft = new DoubleFFT_1D(n);
        fft.realForward(a);

        int bins = n / 2;
        double[] db = new double[bins];
        double inv = 1.0 / n;
        // JTransforms packed real-FFT layout:
        //   a[0]=Re[0], a[1]=Re[n/2], a[2k]=Re[k], a[2k+1]=Im[k]
        db[0] = magToDb(Math.abs(a[0]) * inv);
        for (int k = 1; k < bins; k++) {
            double re = a[2 * k];
            double im = a[2 * k + 1];
            db[k] = magToDb(Math.sqrt(re * re + im * im) * inv);
        }
        return db;
    }

    private static double magToDb(double mag) {
        return mag > 1e-12 ? 20 * Math.log10(mag) : -240;
    }

    /**
     * Reduce a dBFS spectrum to per-band peak levels, clamped to [-95, -2].
     * Direct port of {@code AudioSource.readBands}.
     */
    public static float[] bands(double[] specDb, double binHz, double[] centers, int frac) {
        int n = centers.length;
        float[] out = new float[n];
        double halfOct = 1.0 / (2.0 * frac);
        for (int i = 0; i < n; i++) {
            double fc = centers[i];
            double fLow = fc * Math.pow(2, -halfOct);
            double fHigh = fc * Math.pow(2, halfOct);
            int binLow = Math.max(0, (int) Math.floor(fLow / binHz));
            int binHigh = Math.min(specDb.length - 1, (int) Math.ceil(fHigh / binHz));
            double peak = -200;
            for (int b = binLow; b <= binHigh; b++) {
                if (specDb[b] > peak) peak = specDb[b];
            }
            double v = peak > -200 ? peak : -95;
            out[i] = (float) Math.max(-95, Math.min(-2, v));
        }
        return out;
    }

    /** Broadband RMS of a time-domain window in dBFS (floor -144). Port of {@code readRMS}. */
    public static double rmsDb(float[] window) {
        double sum = 0;
        for (float v : window) sum += (double) v * v;
        double rms = Math.sqrt(sum / window.length);
        return rms > 1e-9 ? 20 * Math.log10(rms) : -144;
    }
}
