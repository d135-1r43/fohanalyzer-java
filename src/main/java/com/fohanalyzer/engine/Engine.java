package com.fohanalyzer.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure frequency math and the signal simulator. Direct port of
 * {@code src/lib/engine.js}. All methods are static and side-effect free.
 */
public final class Engine
{

	public static final double FMIN = 20, FMAX = 20000;
	private static final double LOGMIN = log2(FMIN);
	private static final double LOGMAX = log2(FMAX);
	private static final double LOGSPAN = LOGMAX - LOGMIN;

	private static final String[] NOTES = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };

	/** Frequency zones drawn in the colour strip, low to high. */
	public static final List<Zone> ZONES = List.of(
		new Zone(20, 60, "SUB", "#3b5bff"),
		new Zone(60, 200, "BASS", "#2f9bff"),
		new Zone(200, 500, "LOW-MID", "#19c6c6"),
		new Zone(500, 2000, "MID", "#2fd07a"),
		new Zone(2000, 4000, "UPPER-MID", "#a3e635"),
		new Zone(4000, 8000, "PRESENCE", "#f5a524"),
		new Zone(8000, 20000, "AIR", "#f5575d"));

	private Engine()
	{
	}

	private static double log2(double x)
	{
		return Math.log(x) / Math.log(2);
	}

	/** Normalised log position of a frequency: FMIN -> 0, FMAX -> 1. */
	public static double freqNorm(double f)
	{
		return (log2(f) - LOGMIN) / LOGSPAN;
	}

	/** Inverse of {@link #freqNorm}. */
	public static double normFreq(double n)
	{
		return Math.pow(2, LOGMIN + n * LOGSPAN);
	}

	/**
	 * Log-spaced 1/{@code frac}-octave band centres anchored at 1 kHz, within
	 * FMIN..FMAX.
	 */
	public static double[] bandCenters(int frac)
	{
		double r = Math.pow(2, 1.0 / frac);
		double lnr = Math.log(r);
		int kmin = (int)Math.ceil(Math.log(FMIN / 1000) / lnr);
		int kmax = (int)Math.floor(Math.log(FMAX / 1000) / lnr);
		List<Double> out = new ArrayList<>();
		for (int k = kmin; k <= kmax; k++)
			out.add(1000 * Math.pow(r, k));
		double[] arr = new double[out.size()];
		for (int i = 0; i < arr.length; i++)
			arr[i] = out.get(i);
		return arr;
	}

	private static double bump(double f, double fc, double widthOct, double gainDb)
	{
		double x = log2(f / fc) / widthOct;
		return gainDb * Math.exp(-x * x);
	}

	/** Nearest musical note name, e.g. 440 -> "A4". Empty string for f <= 0. */
	public static String noteName(double f)
	{
		if (f <= 0) return "";
		int n = (int)Math.round(12 * log2(f / 440)) + 69;
		String name = NOTES[((n % 12) + 12) % 12];
		int oct = (int)Math.floor(n / 12.0) - 1;
		return name + oct;
	}

	/** Result of one simulation frame: two dBFS-per-band traces. */
	public record SimFrame(float[] mic, float[] solo)
	{
	}

	/**
	 * Generate one frame of the two simulated traces at time {@code t}
	 * (seconds). {@code ring} may be {@code null} (treated as inactive). Direct
	 * port of {@code sample()}.
	 */
	public static SimFrame sample(double[] centers, double t, Ring ring)
	{
		int n = centers.length;
		float[] mic = new float[n];
		float[] solo = new float[n];

		double bps = 2.0;
		double beat = t * bps;
		double beatFrac = beat - Math.floor(beat);
		int beatIdx = (int)(Math.floor(beat) % 4);
		double kickEnv = Math.exp(-beatFrac * 16);
		double snareEnv = (beatIdx == 1 || beatIdx == 3) ? Math.exp(-beatFrac * 20) : 0;
		double hatFrac = (t * bps * 2) % 1;
		double hatEnv = Math.exp(-hatFrac * 26) * 0.8;
		double bassSust = 0.45 + 0.55 * (0.5 + 0.5 * Math.sin(t * Math.PI));
		double lead = 0.5 + 0.5 * Math.sin(t * 1.27) + 0.3 * Math.sin(t * 2.73 + 1.1);
		lead = Math.max(0, Math.min(1, lead));

		double ringLvl = 0, ringOsc = 1, ringFc = 1000;
		if (ring != null && ring.active())
		{
			double age = t - ring.t0();
			ringLvl = Math.min(42, age * 50);
			ringOsc = 1 + 0.12 * Math.sin(t * 38);
			ringFc = ring.fc();
		}

		for (int i = 0; i < n; i++)
		{
			double f = centers[i];
			double g = -3.0 * log2(f / 1000) - 37;
			g += kickEnv * bump(f, 58, 0.5, 15);
			g += bassSust * bump(f, 95, 0.7, 9);
			g += bump(f, 150, 0.6, 4);
			g += bump(f, 400, 0.6, -2.5);
			g += snareEnv * bump(f, 210, 0.5, 12);
			g += snareEnv * bump(f, 3200, 0.85, 8);
			g += (0.4 + 0.6 * lead) * bump(f, 750, 1.0, 6);
			g += (0.3 + 0.7 * lead) * bump(f, 2500, 0.8, 7);
			g += hatEnv * bump(f, 11000, 0.9, 10);
			g += (0.5 + 0.5 * hatEnv) * bump(f, 16000, 1.2, 2.5);

			double h = f * 0.0017;
			double jScale = 0.55 + 0.07 * log2(f / 100);
			double jBase = 2.0 * Math.sin(t * 8.0 + h * 97) +
				1.3 * Math.sin(t * 15.3 + h * 53) +
				0.8 * Math.sin(t * 23.1 + h * 131);

			double md = 3.0 * Math.cos((2 * Math.PI * f) / 120) * Math.exp(-f / 2600);
			md += bump(f, 48, 0.05, 5) + bump(f, 92, 0.06, 4) + bump(f, 146, 0.06, 3);
			md += bump(f, 62, 0.85, 3);
			md += -3.6 * Math.max(0, log2(f / 7000));
			md += -1.5;
			double micJit = jBase * jScale;
			double soloJit = (jBase * 0.85 + 1.1 * Math.sin(t * 19.0 + h * 71)) * jScale;

			double mv = g + md + micJit;
			double sv = g + 1.2 + soloJit;

			if (ringLvl > 0)
			{
				mv += bump(f, ringFc, 0.16, ringLvl) * ringOsc;
				sv += bump(f, ringFc, 0.17, ringLvl * 0.7) * ringOsc;
			}

			mic[i] = (float)Math.max(-95, Math.min(-2, mv));
			solo[i] = (float)Math.max(-95, Math.min(-2, sv));
		}
		return new SimFrame(mic, solo);
	}

	/** Long form, e.g. "440 Hz", "1.00 kHz", "10.0 kHz", or "—" for 0/NaN. */
	public static String fmtFreq(double f)
	{
		if (f == 0 || Double.isNaN(f)) return "—";
		if (f >= 1000)
		{
			int dp = f >= 10000 ? 1 : 2;
			return String.format(Locale.US, "%." + dp + "f kHz", f / 1000);
		}
		return Math.round(f) + " Hz";
	}

	/** Short form, e.g. "440", "1.0k", "10k". */
	public static String fmtShort(double f)
	{
		if (f >= 1000)
		{
			int dp = f >= 10000 ? 0 : 1;
			return String.format(Locale.US, "%." + dp + "f", f / 1000) + "k";
		}
		return Long.toString(Math.round(f));
	}
}
