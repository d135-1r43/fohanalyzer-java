package com.fohanalyzer.audio;

import com.fohanalyzer.engine.Engine;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the spectral math directly with synthetic buffers; the device
 * capture plumbing around it is not covered here.
 */
class AudioDspTest
{
	private static final int N = AudioSource.FFT_SIZE;
	private static final float RATE = 48000f;

	private static float[] sine(double freq, double amp)
	{
		float[] x = new float[N];
		for (int i = 0; i < N; i++)
		{
			x[i] = (float)(amp * Math.sin(2 * Math.PI * freq * i / RATE));
		}
		return x;
	}

	@Test
	void spectrumPeaksAtTheInputTone()
	{
		double freq = 1000;
		double[] spec = AudioDsp.spectrumDb(sine(freq, 0.5));
		double binHz = RATE / N;
		int expected = (int)Math.round(freq / binHz);
		int peak = 0;
		for (int k = 1; k < spec.length; k++)
			if (spec[k] > spec[peak]) peak = k;
		assertEquals(expected, peak, 2, "spectral peak should land at the tone's bin");
	}

	@Test
	void bandsMatchCentersLengthAndClamp()
	{
		double[] centers = Engine.bandCenters(12);
		double[] spec = AudioDsp.spectrumDb(sine(1000, 0.5));
		float[] bands = AudioDsp.bands(spec, RATE / N, centers, 12);
		assertEquals(centers.length, bands.length);
		for (float v : bands)
		{
			assertTrue(v >= -95 && v <= -2, "band " + v + " out of [-95,-2]");
		}
	}

	@Test
	void bandNearToneIsLouderThanQuietBand()
	{
		double[] centers = Engine.bandCenters(24);
		float[] bands = AudioDsp.bands(AudioDsp.spectrumDb(sine(1000, 0.5)), RATE / N, centers, 24);
		int near = 0;
		int far = 0;

		for (int i = 1; i < centers.length; i++)
		{
			if (Math.abs(centers[i] - 1000) < Math.abs(centers[near] - 1000)) near = i;
			if (Math.abs(centers[i] - 12000) < Math.abs(centers[far] - 12000)) far = i;
		}
		assertTrue(bands[near] > bands[far], "tone band should exceed an empty band");
	}

	@Test
	void silenceReadsAtFloor()
	{
		double[] centers = Engine.bandCenters(12);
		float[] bands = AudioDsp.bands(AudioDsp.spectrumDb(new float[N]), RATE / N, centers, 12);
		for (float v : bands)
			assertEquals(-95f, v, 1e-3);
	}

	@Test
	void rmsOfSilenceIsFloor()
	{
		assertEquals(-144, AudioDsp.rmsDb(new float[N]), 1e-9);
	}

	@Test
	void rmsOfFullScaleSineIsNearMinus3dB()
	{
		// A sine of amplitude 1 has RMS 1/sqrt(2) ≈ -3.01 dBFS.
		assertEquals(-3.01, AudioDsp.rmsDb(sine(1000, 1.0)), 0.2);
	}

	@Test
	void pitchDetectionFindsTheToneFrequency()
	{
		for (double freq : new double[] { 220, 440, 1000, 2500 })
		{
			AudioDsp.Pitch p = AudioDsp.detectPitch(sine(freq, 0.5), RATE);
			assertNotNull(p, freq + " Hz tone should be pitched");
			assertEquals(freq, p.hz(), freq * 0.02, "detected pitch within 2% of " + freq);
			assertTrue(p.probability() > 0, "a detected pitch carries a confidence");
		}
	}

	@Test
	void pitchDetectionRejectsSilence()
	{
		assertNull(AudioDsp.detectPitch(new float[N], RATE));
	}

	@Test
	void pitchDetectionRejectsTooShortAWindow()
	{
		assertNull(AudioDsp.detectPitch(new float[AudioDsp.PITCH_BUFFER - 1], RATE));
	}

	@Nested
	class PowerMerge
	{
		@Test
		void twoEqualLevelsAverageToThatSameLevel()
		{
			// Not +3 dB: a centred mono source must read the same whether it
			// arrives on one channel or on both sides of a pair.
			assertEquals(-20, AudioDsp.powerMeanDb(-20, -20), 1e-9);
		}

		@Test
		void aSourceOnOneSideOnlyReadsThreeDbDown()
		{
			assertEquals(-23.0103, AudioDsp.powerMeanDb(-20, -240), 1e-4);
		}

		@Test
		void theLouderSideDominates()
		{
			double merged = AudioDsp.powerMeanDb(-10, -40);
			assertTrue(merged < -10, "cannot exceed the louder side");
			assertTrue(merged > -13.1, "should sit just under -10-3 dB");
		}

		@Test
		void isSymmetric()
		{
			assertEquals(AudioDsp.powerMeanDb(-12, -30), AudioDsp.powerMeanDb(-30, -12), 1e-12);
		}

		@Test
		void silenceOnBothSidesStaysAtTheFloor()
		{
			assertEquals(-240, AudioDsp.powerMeanDb(-240, -240), 1e-9);
		}

		@Test
		void mergesBinByBinAndTakesTheShorterLength()
		{
			double[] a = { -20, -30, -40 };
			double[] b = { -20, -240 };
			double[] merged = AudioDsp.mergePower(a, b);
			assertEquals(2, merged.length);
			assertEquals(-20, merged[0], 1e-9);
			assertEquals(-33.0103, merged[1], 1e-4);
		}

		/**
		 * The property that motivates power merging: two sides that would
		 * cancel completely in a time-domain sum still read their true level
		 * here.
		 */
		@Test
		void oppositePolarityDoesNotCancel()
		{
			float[] left = sine(1000, 0.5f);
			float[] right = new float[left.length];
			for (int i = 0; i < left.length; i++)
				right[i] = -left[i];

			double[] merged = AudioDsp.mergePower(
				AudioDsp.spectrumDb(left), AudioDsp.spectrumDb(right));
			double[] solo = AudioDsp.spectrumDb(left);

			double[] centers = Engine.bandCenters(12);
			double binHz = RATE / N;
			float[] mergedBands = AudioDsp.bands(merged, binHz, centers, 12);
			float[] soloBands = AudioDsp.bands(solo, binHz, centers, 12);

			int peak = 0;
			for (int i = 1; i < centers.length; i++)
				if (soloBands[i] > soloBands[peak]) peak = i;
			assertEquals(soloBands[peak], mergedBands[peak], 0.01,
				"merged pair should match the single side, not cancel to silence");
		}
	}
}
