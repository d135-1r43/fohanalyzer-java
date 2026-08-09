package com.fohanalyzer.audio;

import be.tarsos.dsp.pitch.FastYin;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.util.fft.BlackmanWindow;
import be.tarsos.dsp.util.fft.FFT;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure, testable spectral math used by {@link AudioSource}, built on
 * <a href="https://github.com/JorenSix/TarsosDSP">TarsosDSP</a>.
 * {@link #spectrumDb} takes a time-domain window to a dBFS magnitude spectrum
 * (Blackman window, FFT, magnitude normalised by FFT size, converted to dBFS);
 * {@link #bands} and {@link #rmsDb} reduce that to per-band levels and a
 * broadband RMS.
 */
public final class AudioDsp
{
	/**
	 * Window length handed to YIN — TarsosDSP's own default for
	 * {@link FastYin}.
	 */
	public static final int PITCH_BUFFER = 2048;

	/**
	 * Blocks of {@link #PITCH_BUFFER} samples voted on by {@link #detectPitch}.
	 */
	private static final int PITCH_BLOCKS = 4;

	/**
	 * Windowed FFTs are stateful (twiddle tables plus a precomputed window
	 * curve), so they are built once per size and kept per thread rather than
	 * per call.
	 */
	private static final ThreadLocal<Map<Integer, FFT>> FFTS = ThreadLocal.withInitial(HashMap::new);

	private AudioDsp()
	{
	}

	/**
	 * Magnitude spectrum in dBFS for the given real time-domain window. Returns
	 * {@code fftSize/2} bins (bin k centred at
	 * {@code k * sampleRate / fftSize}). The window is copied and
	 * Blackman-windowed (α = 0.16) before the transform.
	 */
	public static double[] spectrumDb(float[] window)
	{
		int n = window.length;
		FFT fft = FFTS.get().computeIfAbsent(n, size -> new FFT(size, new BlackmanWindow()));

		float[] a = window.clone();
		// applies the Blackman curve, then the real FFT
		fft.forwardTransform(a);

		int bins = n / 2;
		double[] db = new double[bins];
		double inv = 1.0 / n;
		// Packed real-FFT layout: a[0]=Re[0], a[1]=Re[n/2], a[2k]=Re[k],
		// a[2k+1]=Im[k]. FFT.modulus() reads that pair layout, so it is only
		// correct from k=1 up — at k=0 it would fold Nyquist into DC.
		db[0] = magToDb(Math.abs(a[0]) * inv);
		for (int k = 1; k < bins; k++)
		{
			db[k] = magToDb(fft.modulus(a, k) * inv);
		}
		return db;
	}

	private static double magToDb(double mag)
	{
		return mag > 1e-12 ? 20 * Math.log10(mag) : -240;
	}

	/**
	 * Reduce a dBFS spectrum to per-band peak levels, clamped to [-95, -2]. The
	 * band-reading half of {@link AudioSource#readBands}.
	 */
	public static float[] bands(double[] specDb, double binHz, double[] centers, int frac)
	{
		int n = centers.length;
		float[] out = new float[n];
		double halfOct = 1.0 / (2.0 * frac);
		for (int i = 0; i < n; i++)
		{
			double fc = centers[i];
			double fLow = fc * Math.pow(2, -halfOct);
			double fHigh = fc * Math.pow(2, halfOct);
			int binLow = Math.max(0, (int)Math.floor(fLow / binHz));
			int binHigh = Math.min(specDb.length - 1, (int)Math.ceil(fHigh / binHz));
			double peak = -200;
			for (int b = binLow; b <= binHigh; b++)
			{
				if (specDb[b] > peak) peak = specDb[b];
			}
			double v = peak > -200 ? peak : -95;
			out[i] = (float)Math.clamp(v, -95, -2);
		}
		return out;
	}

	/**
	 * Broadband RMS of a time-domain window in dBFS (floor -144). Backs
	 * {@link AudioSource#readRMS}.
	 *
	 * <p>
	 * Deliberately not TarsosDSP's {@code SilenceDetector.soundPressureLevel}:
	 * that divides the energy by the buffer length instead of its square root,
	 * so it does not agree with the RMS the SPL readout is calibrated against.
	 */
	public static double rmsDb(float[] window)
	{
		double sum = 0;
		for (float v : window)
			sum += (double)v * v;
		double rms = Math.sqrt(sum / window.length);
		return rms > 1e-9 ? 20 * Math.log10(rms) : -144;
	}

	/**
	 * A detected fundamental: frequency in Hz plus YIN's own confidence in it.
	 */
	public record Pitch(double hz, double probability)
	{
	}

	/**
	 * Fundamental of the tail of {@code window} via TarsosDSP's FastYin, or
	 * {@code null} when nothing steady is playing. Up to {@link #PITCH_BLOCKS}
	 * consecutive blocks ending at the newest sample are each estimated
	 * separately and the median is returned, so a single glitched block cannot
	 * move the result — which matters when the answer is fed straight into the
	 * feedback log as a frequency to notch.
	 */
	public static Pitch detectPitch(float[] window, float sampleRate)
	{
		if (window == null || window.length < PITCH_BUFFER) return null;

		FastYin yin = new FastYin(sampleRate, PITCH_BUFFER);
		int blocks = Math.min(PITCH_BLOCKS, window.length / PITCH_BUFFER);
		double[] hz = new double[blocks];
		double probSum = 0;
		int found = 0;

		for (int b = 0; b < blocks; b++)
		{
			int off = window.length - (b + 1) * PITCH_BUFFER;
			float[] block = Arrays.copyOfRange(window, off, off + PITCH_BUFFER);
			// getPitch() hands back a reused result object — read it before
			// the next call.
			PitchDetectionResult r = yin.getPitch(block);
			if (r.isPitched() && r.getPitch() > 0)
			{
				hz[found++] = r.getPitch();
				probSum += r.getProbability();
			}
		}
		if (found == 0) return null;

		double[] sorted = Arrays.copyOf(hz, found);
		Arrays.sort(sorted);
		return new Pitch(sorted[found / 2], probSum / found);
	}
}
