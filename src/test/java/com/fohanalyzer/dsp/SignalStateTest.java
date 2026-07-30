package com.fohanalyzer.dsp;

import com.fohanalyzer.engine.Voice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Band allocation, averaging, smoothing, peak hold, and voicing. */
class SignalStateTest
{
	static final Voice NO_VOICE = Voice.NEUTRAL;
	static final double[] centers = { 100, 500, 1000, 4000, 10000 };
	static final int n = centers.length;

	SignalState sig;

	@BeforeEach
	void setUp()
	{
		sig = new SignalState();
		sig.ensureBands(12, n);
	}

	static float[] fill(int n, float v)
	{
		float[] a = new float[n];
		Arrays.fill(a, v);
		return a;
	}

	static boolean all(float[] a, float v)
	{
		for (float x : a)
			if (x != v) return false;
		return true;
	}

	@Nested
	class EnsureBands
	{
		@Test
		void initialisesArrayLengths()
		{
			assertEquals(n, sig.dispMic.length);
			assertEquals(n, sig.dispSolo.length);
			assertEquals(n, sig.holdMic.length);
			assertEquals(n, sig.holdSolo.length);
		}

		@Test
		void fillsDispAtMinus65AndHoldAtMinus95()
		{
			assertTrue(all(sig.dispMic, -65));
			assertTrue(all(sig.dispSolo, -65));
			assertTrue(all(sig.holdMic, -95));
			assertTrue(all(sig.holdSolo, -95));
		}

		@Test
		void noOpWhenUnchanged()
		{
			sig.dispMic[0] = -30;
			sig.ensureBands(12, n);
			assertEquals(-30, sig.dispMic[0]);
		}

		@Test
		void reinitialisesWhenFracChanges()
		{
			sig.dispMic[0] = -30;
			sig.ensureBands(6, n);
			assertEquals(-65, sig.dispMic[0]);
		}

		@Test
		void reinitialisesWhenBandCountChanges()
		{
			sig.dispMic[0] = -30;
			sig.ensureBands(12, n + 1);
			assertEquals(n + 1, sig.dispMic.length);
			assertEquals(-65, sig.dispMic[0]);
		}
	}

	@Nested
	class ResetHold
	{
		@Test
		void resetsHoldToMinus95()
		{
			sig.update(fill(n, -30), fill(n, -30), NO_VOICE, NO_VOICE, centers, 0, 1);
			sig.resetHold();
			assertTrue(all(sig.holdMic, -95));
			assertTrue(all(sig.holdSolo, -95));
		}

		@Test
		void doesNotAffectDisp()
		{
			sig.update(fill(n, -30), fill(n, -30), NO_VOICE, NO_VOICE, centers, 0, 1);
			float[] before = sig.dispMic.clone();
			sig.resetHold();
			assertArrayEquals(before, sig.dispMic);
		}
	}

	@Nested
	class Averaging
	{
		@Test
		void avgN1ConvergesInOneUpdate()
		{
			sig.update(fill(n, -40), fill(n, -50), NO_VOICE, NO_VOICE, centers, 0, 1);
			assertEquals(-40, sig.dispMic[2], 1e-4);
			assertEquals(-50, sig.dispSolo[2], 1e-4);
		}

		@Test
		void avgN4ApproachesGradually()
		{
			float[] target = fill(n, -30);
			sig.update(target, target, NO_VOICE, NO_VOICE, centers, 0, 4);
			assertTrue(sig.dispMic[0] > -65);
			assertTrue(sig.dispMic[0] < -30);
		}

		@Test
		void avgN4ConvergesWithRepetition()
		{
			float[] target = fill(n, -30);
			for (int i = 0; i < 30; i++)
				sig.update(target, target, NO_VOICE, NO_VOICE, centers, 0, 4);
			assertEquals(-30, sig.dispMic[0], 0.5);
		}
	}

	@Nested
	class Smoothing
	{
		@Test
		void smoothingZeroTracksInstantly()
		{
			sig.update(fill(n, -20), fill(n, -20), NO_VOICE, NO_VOICE, centers, 0, 1);
			assertEquals(-20, sig.dispMic[0], 1e-4);
			sig.update(fill(n, -60), fill(n, -60), NO_VOICE, NO_VOICE, centers, 0, 1);
			assertEquals(-60, sig.dispMic[0], 1e-4);
		}

		@Test
		void fastAttackJumpsRegardlessOfSmoothing()
		{
			sig.update(fill(n, -20), fill(n, -20), NO_VOICE, NO_VOICE, centers, 0.9, 1);
			assertEquals(-20, sig.dispMic[0], 1e-4);
		}

		@Test
		void slowReleaseSmoothsFall()
		{
			sig.update(fill(n, -20), fill(n, -20), NO_VOICE, NO_VOICE, centers, 0, 1);
			sig.update(fill(n, -80), fill(n, -80), NO_VOICE, NO_VOICE, centers, 0.8, 1);
			assertTrue(sig.dispMic[0] > -80);
			assertTrue(sig.dispMic[0] < -20);
		}
	}

	@Nested
	class Voicing
	{
		@Test
		void appliesGainToMic()
		{
			sig.update(fill(n, -50), fill(n, -50), new Voice(6, 0), NO_VOICE, centers, 0, 1);
			assertEquals(-44, sig.dispMic[2], 1e-3);
		}

		@Test
		void appliesGainToSoloIndependently()
		{
			sig.update(fill(n, -50), fill(n, -50), NO_VOICE, new Voice(-3, 0), centers, 0, 1);
			assertEquals(-53, sig.dispSolo[2], 1e-3);
		}

		@Test
		void appliesPositiveTilt()
		{
			sig.update(fill(n, -50), fill(n, -50), new Voice(0, 3), NO_VOICE, centers, 0, 1);
			assertTrue(sig.dispMic[4] > sig.dispMic[2]); // 10k > 1k
			assertTrue(sig.dispMic[0] < sig.dispMic[2]); // 100Hz < 1k
		}

		@Test
		void neutralAt1kHzRegardlessOfTilt()
		{
			SignalState s = new SignalState();
			s.ensureBands(12, 1);
			s.update(new float[] { -50 }, new float[] { -50 }, new Voice(0, 6), NO_VOICE,
				new double[] { 1000 }, 0, 1);
			assertEquals(-50, s.dispMic[0], 1e-3);
		}
	}

	@Nested
	class PeakHold
	{
		@Test
		void holdTracksRisingSignal()
		{
			sig.update(fill(n, -20), fill(n, -20), NO_VOICE, NO_VOICE, centers, 0, 1);
			assertEquals(-20, sig.holdMic[0], 1e-4);
		}

		@Test
		void holdDecaysByPoint42PerUpdate()
		{
			sig.update(fill(n, -20), fill(n, -20), NO_VOICE, NO_VOICE, centers, 0, 1);
			float held = sig.holdMic[0];
			sig.update(fill(n, -80), fill(n, -80), NO_VOICE, NO_VOICE, centers, 0, 1);
			assertEquals(held - 0.42, sig.holdMic[0], 1e-3);
		}

		@Test
		void holdNeverBelowDisp()
		{
			sig.update(fill(n, -30), fill(n, -30), NO_VOICE, NO_VOICE, centers, 0, 1);
			assertEquals(sig.dispMic[0], sig.holdMic[0], 1e-3);
		}
	}

	@Nested
	class GetStats
	{
		@Test
		void peakFreqMatchesLoudestBand()
		{
			sig.update(fill(n, -60), fill(n, -60), NO_VOICE, NO_VOICE, centers, 0, 1);
			sig.dispMic[3] = -20;
			Stats st = sig.getStats(centers);
			assertEquals(centers[3], st.peakFreq());
			assertEquals(-20, st.micPeak(), 1e-3);
		}

		@Test
		void correctAverages()
		{
			Arrays.fill(sig.dispMic, -40);
			Arrays.fill(sig.dispSolo, -50);
			Stats st = sig.getStats(centers);
			assertEquals(-40, st.micAvg(), 1e-3);
			assertEquals(-50, st.soloAvg(), 1e-3);
		}

		@Test
		void soloPeakIsMax()
		{
			Arrays.fill(sig.dispSolo, -60);
			sig.dispSolo[1] = -25;
			Stats st = sig.getStats(centers);
			assertEquals(-25, st.soloPeak(), 1e-3);
		}
	}
}
