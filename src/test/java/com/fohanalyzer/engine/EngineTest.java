package com.fohanalyzer.engine;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.fohanalyzer.engine.Engine.*;
import static org.junit.jupiter.api.Assertions.*;

/** Frequency math, note naming, formatting, and the signal simulator. */
class EngineTest
{

	private static final double EPS = 1e-9;

	@Nested
	class FreqNormAndNormFreq
	{
		@Test
		void mapsFminTo0AndFmaxTo1()
		{
			assertEquals(0, freqNorm(FMIN), 1e-6);
			assertEquals(1, freqNorm(FMAX), 1e-6);
		}

		@Test
		void roundtripsCorrectly()
		{
			for (double f : new double[] { 50, 440, 1000, 4000, 10000 })
			{
				assertEquals(f, normFreq(freqNorm(f)), 1e-5 * f + 1e-5);
			}
		}
	}

	@Nested
	class BandCenters
	{
		@Test
		void allBandsWithinFminFmax()
		{
			for (int frac : new int[] { 1, 3, 6, 12, 24 })
			{
				for (double f : bandCenters(frac))
				{
					assertTrue(f >= FMIN && f <= FMAX, "band " + f + " out of range");
				}
			}
		}

		@Test
		void higherResolutionYieldsMoreBands()
		{
			assertTrue(bandCenters(3).length > bandCenters(1).length);
			assertTrue(bandCenters(12).length > bandCenters(3).length);
			assertTrue(bandCenters(24).length > bandCenters(12).length);
		}

		@Test
		void oneOctaveProducesAboutTenBands()
		{
			int len = bandCenters(1).length;
			assertTrue(len >= 9 && len <= 11, "got " + len);
		}

		@Test
		void consecutiveBandsHaveEqualLogRatio()
		{
			double[] bands = bandCenters(3);
			double expected = bands[1] / bands[0];
			for (int i = 1; i < bands.length; i++)
			{
				assertEquals(expected, bands[i] / bands[i - 1], 1e-6);
			}
		}
	}

	@Nested
	class NoteName
	{
		@Test
		void emptyForNonPositive()
		{
			assertEquals("", noteName(0));
			assertEquals("", noteName(-1));
		}

		@Test
		void identifiesNotes()
		{
			assertEquals("A4", noteName(440));
			assertEquals("A3", noteName(220));
			assertEquals("C4", noteName(261.63));
			assertEquals("A5", noteName(880));
		}
	}

	@Nested
	class FmtFreq
	{
		@Test
		void dashForZeroOrNaN()
		{
			assertEquals("—", fmtFreq(0));
			assertEquals("—", fmtFreq(Double.NaN));
		}

		@Test
		void subKiloAsIntegerHz()
		{
			assertEquals("100 Hz", fmtFreq(100));
			assertEquals("440 Hz", fmtFreq(440));
		}

		@Test
		void oneToTenKiloTwoDecimals()
		{
			assertEquals("1.00 kHz", fmtFreq(1000));
			assertEquals("4.50 kHz", fmtFreq(4500));
		}

		@Test
		void tenKiloPlusOneDecimal()
		{
			assertEquals("10.0 kHz", fmtFreq(10000));
			assertEquals("16.0 kHz", fmtFreq(16000));
		}
	}

	@Nested
	class FmtShort
	{
		@Test
		void subKiloInteger()
		{
			assertEquals("100", fmtShort(100));
			assertEquals("440", fmtShort(440));
		}

		@Test
		void oneToTenKiloOneDecimal()
		{
			assertEquals("1.0k", fmtShort(1000));
			assertEquals("4.5k", fmtShort(4500));
		}

		@Test
		void tenKiloPlusInteger()
		{
			assertEquals("10k", fmtShort(10000));
			assertEquals("16k", fmtShort(16000));
		}
	}

	@Nested
	class Sample
	{
		final double[] centers = bandCenters(12);

		@Test
		void returnsArraysMatchingCentersLength()
		{
			var f = Engine.sample(centers, 0, null);
			assertEquals(centers.length, f.mic().length);
			assertEquals(centers.length, f.solo().length);
		}

		@Test
		void clampsValuesToRange()
		{
			for (double t : new double[] { 0, 0.5, 1.0, 2.0 })
			{
				var f = Engine.sample(centers, t, null);
				for (int i = 0; i < centers.length; i++)
				{
					assertTrue(f.mic()[i] >= -95 && f.mic()[i] <= -2);
					assertTrue(f.solo()[i] >= -95 && f.solo()[i] <= -2);
				}
			}
		}

		@Test
		void activeRingRaisesLevelsNearRingFreq()
		{
			double fc = 1000;
			var ring = new Ring(true, fc, 0);
			var withRing = Engine.sample(centers, 2, ring);
			var quiet = Engine.sample(centers, 2, null);
			int near = 0;
			for (int i = 1; i < centers.length; i++)
			{
				if (Math.abs(centers[i] - fc) < Math.abs(centers[near] - fc)) near = i;
			}
			assertTrue(withRing.mic()[near] > quiet.mic()[near]);
		}

		@Test
		void inactiveRingSameAsNull()
		{
			var ring = new Ring(false, 1000, 0);
			var withRing = Engine.sample(centers, 1, ring);
			var nullRing = Engine.sample(centers, 1, null);
			for (int i = 0; i < centers.length; i++)
			{
				assertEquals(nullRing.mic()[i], withRing.mic()[i], EPS);
				assertEquals(nullRing.solo()[i], withRing.solo()[i], EPS);
			}
		}

		@Test
		void outputVariesOverTime()
		{
			var a = Engine.sample(centers, 0, null);
			var b = Engine.sample(centers, 1, null);
			boolean differs = false;
			for (int i = 0; i < centers.length; i++)
			{
				if (a.mic()[i] != b.mic()[i])
				{
					differs = true;
					break;
				}
			}
			assertTrue(differs);
		}
	}
}
