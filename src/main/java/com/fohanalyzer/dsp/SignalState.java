package com.fohanalyzer.dsp;

import com.fohanalyzer.engine.Voice;

/**
 * Per-band signal processing state: frame averaging, asymmetric (fast-attack /
 * slow-release) smoothing, and peak hold.
 */
public final class SignalState
{
	private int frac = 0;

	public float[] avgMic;
	public float[] avgSolo;

	public float[] dispMic;
	public float[] dispSolo;

	public float[] holdMic;
	public float[] holdSolo;

	private static double log2(double x)
	{
		return Math.log(x) / Math.log(2);
	}

	/** (Re)allocate buffers when the resolution or band count changes. */
	public void ensureBands(int frac, int n)
	{
		if (this.frac == frac && dispMic != null && dispMic.length == n) return;
		this.frac = frac;
		avgMic = filled(n, -65);
		avgSolo = filled(n, -65);
		dispMic = filled(n, -65);
		dispSolo = filled(n, -65);
		holdMic = filled(n, -95);
		holdSolo = filled(n, -95);
	}

	private static float[] filled(int n, float v)
	{
		float[] a = new float[n];
		java.util.Arrays.fill(a, v);
		return a;
	}

	public void resetHold()
	{
		java.util.Arrays.fill(holdMic, -95);
		java.util.Arrays.fill(holdSolo, -95);
	}

	public void update(float[] mic, float[] solo, Voice micVoice, Voice soloVoice,
		double[] centers, double smoothing, int avgN)
	{
		int n = centers.length;
		double ca = avgN > 1 ? 1 - 1.0 / avgN : 0;
		double cs = smoothing;
		for (int i = 0; i < n; i++)
		{
			double lf = log2(centers[i] / 1000);
			double mt = mic[i] + micVoice.g() + micVoice.tilt() * lf;
			double sv = solo[i] + soloVoice.g() + soloVoice.tilt() * lf;
			avgMic[i] = (float)(ca > 0 ? avgMic[i] * ca + mt * (1 - ca) : mt);
			avgSolo[i] = (float)(ca > 0 ? avgSolo[i] * ca + sv * (1 - ca) : sv);
			double csm = avgMic[i] > dispMic[i] ? 0 : cs;
			double css = avgSolo[i] > dispSolo[i] ? 0 : cs;
			dispMic[i] = (float)(dispMic[i] * csm + avgMic[i] * (1 - csm));
			dispSolo[i] = (float)(dispSolo[i] * css + avgSolo[i] * (1 - css));
			holdMic[i] = (float)Math.max(holdMic[i] - 0.42, dispMic[i]);
			holdSolo[i] = (float)Math.max(holdSolo[i] - 0.42, dispSolo[i]);
		}
	}

	public Stats getStats(double[] centers)
	{
		int n = centers.length;
		double pMic = -200;
		int pIdx = 0;

		double sMic = 0;
		double sSolo = 0;
		double soloPeak = -200;

		for (int i = 0; i < n; i++)
		{
			if (dispMic[i] > pMic)
			{
				pMic = dispMic[i];
				pIdx = i;
			}
			sMic += dispMic[i];
			sSolo += dispSolo[i];
			if (dispSolo[i] > soloPeak) soloPeak = dispSolo[i];
		}

		return new Stats(centers[pIdx], pMic, sMic / n, sSolo / n, soloPeak, null);
	}
}
