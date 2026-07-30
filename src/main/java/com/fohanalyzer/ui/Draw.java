package com.fohanalyzer.ui;

import com.fohanalyzer.engine.Engine;
import com.fohanalyzer.engine.Zone;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;

/**
 * Canvas drawing routines against a JavaFX {@link GraphicsContext}. The plot
 * padding and the dB/frequency mappings are defined here.
 */
public final class Draw
{
	public static final double DB_TOP = -6, DB_BOT = -90;
	public static final double PAD_L = 50, PAD_R = 16, PAD_T = 18, PAD_B = 50;

	private static final double[] FREQ_MAJ_F = { 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000 };
	private static final String[] FREQ_MAJ_L = { "20", "50", "100", "200", "500", "1k", "2k", "5k", "10k", "20k" };
	private static final double[] FREQ_MIN = { 30, 40, 70, 300, 400, 700, 3000, 4000, 7000, 15000 };

	private Draw()
	{
	}

	public static double dbY(double v, double plotH)
	{
		return PAD_T + (DB_TOP - v) / (DB_TOP - DB_BOT) * plotH;
	}

	public static double fX(double f, double plotW)
	{
		return PAD_L + Engine.freqNorm(f) * plotW;
	}

	private static void dot(GraphicsContext g, double x, double y, Color c)
	{
		g.setFill(c);
		g.setEffect(new DropShadow(6, c));
		g.fillOval(x - 3, y - 3, 6, 6);
		g.setEffect(null);
	}

	private static void roundRect(GraphicsContext g, double x, double y, double w, double h, double r)
	{
		// GraphicsContext fillRoundRect/strokeRoundRect take arc width/height
		// (= 2r). Callers invoke fill/stroke directly; this helper just
		// centralises the radius.
		g.fillRoundRect(x, y, w, h, r * 2, r * 2);
	}

	public static void drawBackground(GraphicsContext g, double width, double height)
	{
		g.setFill(new LinearGradient(0, 0, 0, height, false, CycleMethod.NO_CYCLE,
			new Stop(0, Color.web("#0b1019")), new Stop(1, Color.web("#080b11"))));
		g.fillRect(0, 0, width, height);
	}

	public static void drawGrid(GraphicsContext g, double width, double plotW, double plotH)
	{
		g.setLineWidth(1);
		Font f = Fonts.mono(11);
		g.setFont(f);
		for (double db = DB_TOP; db >= DB_BOT; db -= 12)
		{
			double y = Math.round(dbY(db, plotH)) + 0.5;
			g.setStroke(Color.rgb(255, 255, 255, 0.05));
			g.strokeLine(PAD_L, y, width - PAD_R, y);
			g.setFill(Color.web("#4d5a6a"));
			g.setTextAlign(TextAlignment.RIGHT);
			g.setTextBaseline(VPos.CENTER);
			g.fillText(fmtInt(db), PAD_L - 8, y);
		}
		g.setStroke(Color.rgb(255, 255, 255, 0.035));
		for (double fr : FREQ_MIN)
		{
			double x = Math.round(fX(fr, plotW)) + 0.5;
			g.strokeLine(x, PAD_T, x, PAD_T + plotH);
		}
		g.setTextAlign(TextAlignment.CENTER);
		g.setTextBaseline(VPos.TOP);
		for (int i = 0; i < FREQ_MAJ_F.length; i++)
		{
			double x = Math.round(fX(FREQ_MAJ_F[i], plotW)) + 0.5;
			g.setStroke(Color.rgb(255, 255, 255, 0.08));
			g.strokeLine(x, PAD_T, x, PAD_T + plotH);
			g.setFill(Color.web("#6d7d8e"));
			g.fillText(FREQ_MAJ_L[i], x, PAD_T + plotH + 8);
		}
	}

	private static String fmtInt(double v)
	{
		return Long.toString(Math.round(v));
	}

	/** Per-band x positions and bar widths. */
	public record BarLayout(double[] xs, double[] barW)
	{
	}

	public static BarLayout computeBarLayout(double[] centers, double plotW)
	{
		int n = centers.length;
		double[] xs = new double[n];
		for (int i = 0; i < n; i++)
			xs[i] = fX(centers[i], plotW);
		double[] barW = new double[n];
		for (int i = 0; i < n; i++)
		{
			double left = i > 0 ? xs[i] - xs[i - 1] : xs[1] - xs[0];
			double right = i < n - 1 ? xs[i + 1] - xs[i] : xs[n - 1] - xs[n - 2];
			barW[i] = Math.min(left, right);
		}
		return new BarLayout(xs, barW);
	}

	public static void drawBars(GraphicsContext g, float[] arr, double[] xs, double[] barW,
		double plotH, double baseY, int r, int gc, int b,
		double aTop, double aBot)
	{
		int n = arr.length;
		g.setGlobalBlendMode(BlendMode.ADD);
		for (int i = 0; i < n; i++)
		{
			double w = Math.max(1.5, barW[i] * 0.82);
			double y = dbY(arr[i], plotH);
			g.setFill(new LinearGradient(0, y, 0, baseY, false, CycleMethod.NO_CYCLE,
				new Stop(0, Color.rgb(r, gc, b, aTop)),
				new Stop(1, Color.rgb(r, gc, b, aBot))));
			g.fillRect(xs[i] - w / 2, y, w, baseY - y);
		}
		g.setGlobalBlendMode(BlendMode.SRC_OVER);
		g.setFill(Color.rgb(r, gc, b));
		for (int i = 0; i < n; i++)
		{
			double w = Math.max(1.5, barW[i] * 0.82);
			double y = dbY(arr[i], plotH);
			g.fillRect(xs[i] - w / 2, y - 1, w, 2.5);
		}
	}

	public static void drawHoldTicks(GraphicsContext g, float[] arr, double[] xs, double[] barW,
		double plotH, Color color)
	{
		g.setFill(color);
		for (int i = 0; i < arr.length; i++)
		{
			double w = Math.max(1.5, barW[i] * 0.82);
			g.fillRect(xs[i] - w / 2, dbY(arr[i], plotH) - 1, w, 2);
		}
	}

	/** Captured reference snapshot for the A/B ghost overlay. */
	public record Reference(double[] centers, float[] mic, float[] solo, String time)
	{
	}

	public static void drawReference(GraphicsContext g, Reference R, double plotW, double plotH,
		boolean micOn, boolean soloOn)
	{
		if (micOn) ghost(g, R.centers(), R.mic(), plotW, plotH, Color.rgb(120, 222, 242, 0.6));
		if (soloOn) ghost(g, R.centers(), R.solo(), plotW, plotH, Color.rgb(247, 200, 120, 0.6));
	}

	private static void ghost(GraphicsContext g, double[] centers, float[] vals,
		double plotW, double plotH, Color color)
	{
		g.setLineWidth(1.5);
		g.setStroke(color);
		g.setLineDashes(5, 4);
		g.beginPath();
		for (int i = 0; i < centers.length; i++)
		{
			double x = fX(centers[i], plotW), y = dbY(vals[i], plotH);
			if (i == 0) g.moveTo(x, y);
			else
				g.lineTo(x, y);
		}
		g.stroke();
		g.setLineDashes(null);
	}

	public static void drawTransferCurve(GraphicsContext g, double[] centers, float[] dispMic,
		float[] dispSolo, double width, double plotW, double plotH)
	{
		double tfRange = 24, midY = PAD_T + plotH / 2, half = (plotH / 2) * 0.8;
		g.setStroke(Color.rgb(183, 148, 246, 0.28));
		g.setLineDashes(6, 6);
		g.strokeLine(PAD_L, midY, width - PAD_R, midY);
		g.setLineDashes(null);
		g.beginPath();
		for (int i = 0; i < centers.length; i++)
		{
			double d = dispMic[i] - dispSolo[i];
			double x = fX(centers[i], plotW);
			double y = midY - Math.max(-tfRange, Math.min(tfRange, d)) / tfRange * half;
			if (i == 0) g.moveTo(x, y);
			else
				g.lineTo(x, y);
		}
		g.setLineWidth(2.5);
		g.setStroke(Color.web("#b794f6"));
		g.setEffect(new DropShadow(7, Color.web("#b794f6")));
		g.stroke();
		g.setEffect(null);
	}

	public static void drawTransferLabels(GraphicsContext g, double width, double plotW, double plotH)
	{
		double tfRange = 24, midY = PAD_T + plotH / 2, half = (plotH / 2) * 0.8;
		g.setFont(Fonts.mono(10));
		g.setTextAlign(TextAlignment.RIGHT);
		g.setTextBaseline(VPos.CENTER);
		int[] ds = { 24, 12, 0, -12, -24 };
		String[] ls = { "+24", "+12", "0", "−12", "−24" };
		for (int i = 0; i < ds.length; i++)
		{
			double y = midY - ds[i] / tfRange * half;
			g.setFill(Color.rgb(183, 148, 246, 0.7));
			g.fillText(ls[i], width - PAD_R - 6, y);
		}
		g.setTextAlign(TextAlignment.LEFT);
		g.setFill(Color.rgb(183, 148, 246, 0.9));
		g.setFont(Fonts.mono(FontWeight.SEMI_BOLD, 9));
		g.fillText("TF · MIC − SOLO  (dB)", PAD_L + 8, PAD_T + 11);
	}

	private record Peak(int i, double v, double prom, double f)
	{
	}

	public static void drawPeakMarkers(GraphicsContext g, float[] src, double[] centers,
		double plotW, double plotH, double width, Color col)
	{
		int n = src.length;
		List<Peak> peaks = new ArrayList<>();
		for (int i = 2; i < n - 2; i++)
		{
			if (src[i] > src[i - 1] && src[i] >= src[i + 1]
				&& src[i] > src[i - 2] && src[i] > src[i + 2])
			{
				double local = (src[i - 2] + src[i + 2]) / 2;
				double prom = src[i] - local;
				if (prom > 3.5) peaks.add(new Peak(i, src[i], prom, centers[i]));
			}
		}
		peaks.sort((a, b) -> Double.compare(b.v, a.v));
		Font labelFont = Fonts.mono(FontWeight.SEMI_BOLD, 11);
		for (int k = 0; k < Math.min(3, peaks.size()); k++)
		{
			Peak pk = peaks.get(k);
			double x = fX(pk.f, plotW), y = dbY(pk.v, plotH);
			g.setFill(Color.web("#e9fb9b"));
			g.fillOval(x - 3.5, y - 3.5, 7, 7);
			g.setStroke(col);
			g.setLineWidth(1.5);
			g.strokeOval(x - 3.5, y - 3.5, 7, 7);
			String fl = pk.f >= 1000
				? String.format(java.util.Locale.US, "%." + (pk.f >= 10000 ? 0 : 1) + "f", pk.f / 1000) + "k"
				: Long.toString(Math.round(pk.f));
			String label = fl + " · " + Engine.noteName(pk.f);
			g.setFont(labelFont);
			double tw = Fonts.width(labelFont, label);
			double bx = x - tw / 2 - 6;
			bx = Math.max(PAD_L + 2, Math.min(bx, width - PAD_R - tw - 12));
			double by = Math.max(PAD_T + 2, y - 26);
			g.setFill(Color.rgb(8, 11, 17, 0.9));
			roundRect(g, bx, by, tw + 12, 17, 4);
			g.setStroke(Color.rgb(233, 251, 155, 0.5));
			g.setLineWidth(1);
			g.strokeRoundRect(bx, by, tw + 12, 17, 8, 8);
			g.setFill(Color.web("#e9fb9b"));
			g.setTextAlign(TextAlignment.LEFT);
			g.setTextBaseline(VPos.CENTER);
			g.fillText(label, bx + 6, by + 9);
		}
	}

	public static void drawLocateLine(GraphicsContext g, double locateFreq, double width,
		double plotW, double plotH)
	{
		double x = fX(locateFreq, plotW);
		if (x < PAD_L || x > width - PAD_R) return;
		g.setStroke(Color.rgb(255, 91, 96, 0.85));
		g.setLineWidth(1.5);
		g.setLineDashes(4, 3);
		g.strokeLine(x, PAD_T, x, PAD_T + plotH);
		g.setLineDashes(null);
		String lab = locateFreq >= 1000
			? String.format(java.util.Locale.US, "%." + (locateFreq >= 10000 ? 1 : 2) + "f", locateFreq / 1000) + "k"
			: Long.toString(Math.round(locateFreq));
		Font f = Fonts.mono(FontWeight.SEMI_BOLD, 11);
		g.setFont(f);
		double tw = Fonts.width(f, lab);
		double bx = x - tw / 2 - 6;
		bx = Math.max(PAD_L, Math.min(bx, width - PAD_R - tw - 12));
		g.setFill(Color.rgb(255, 91, 96, 0.94));
		roundRect(g, bx, PAD_T + 2, tw + 12, 17, 4);
		g.setFill(Color.web("#180405"));
		g.setTextAlign(TextAlignment.LEFT);
		g.setTextBaseline(VPos.CENTER);
		g.fillText(lab, bx + 6, PAD_T + 11);
	}

	/** Mouse position relative to the canvas, with an inside flag. */
	public record Mouse(double x, double y, boolean inside)
	{
	}

	private record Line(String t, Color c)
	{
	}

	public static void drawHoverTooltip(GraphicsContext g, Mouse mouse, double[] centers, double[] xs,
		float[] dispMic, float[] dispSolo, double width, double plotW,
		double plotH, boolean micOn, boolean soloOn)
	{
		double mx = mouse.x(), my = mouse.y();
		if (mx <= PAD_L || mx >= width - PAD_R || my <= PAD_T || my >= PAD_T + plotH) return;
		int n = centers.length;
		double f = Engine.normFreq(Math.max(0, Math.min(1, (mx - PAD_L) / plotW)));
		int idx = 0;
		double best = 1e9;
		for (int i = 0; i < n; i++)
		{
			double d = Math.abs(centers[i] - f);
			if (d < best)
			{
				best = d;
				idx = i;
			}
		}
		double cx = xs[idx];
		g.setStroke(Color.rgb(255, 255, 255, 0.22));
		g.setLineWidth(1);
		g.strokeLine(cx, PAD_T, cx, PAD_T + plotH);
		double micV = dispMic[idx], soloV = dispSolo[idx];
		if (micOn) dot(g, cx, dbY(micV, plotH), Color.web("#22d3ee"));
		if (soloOn) dot(g, cx, dbY(soloV, plotH), Color.web("#f5a524"));
		String fl = centers[idx] >= 1000
			? String.format(java.util.Locale.US, "%." + (centers[idx] >= 10000 ? 1 : 2) + "f", centers[idx] / 1000) + " kHz"
			: Math.round(centers[idx]) + " Hz";
		List<Line> lines = new ArrayList<>();
		lines.add(new Line(fl + "  " + Engine.noteName(centers[idx]), Color.web("#e7eef6")));
		if (micOn) lines.add(new Line("MIC  " + fmt1(micV) + " dB", Color.web("#22d3ee")));
		if (soloOn) lines.add(new Line("SOLO " + fmt1(soloV) + " dB", Color.web("#f5a524")));
		if (micOn && soloOn)
		{
			double d = micV - soloV;
			lines.add(new Line("Δ    " + (d >= 0 ? "+" : "") + fmt1(d) + " dB", Color.web("#a3e635")));
		}
		Font font = Fonts.mono(11);
		g.setFont(font);
		double bw = 0;
		for (Line l : lines)
			bw = Math.max(bw, Fonts.width(font, l.t()));
		bw += 18;
		double bh = lines.size() * 16 + 10;
		double bx = cx + 12;
		if (bx + bw > width - PAD_R) bx = cx - bw - 12;
		double by = my - bh - 10;
		if (by < PAD_T) by = my + 14;
		g.setFill(Color.rgb(10, 14, 21, 0.94));
		roundRect(g, bx, by, bw, bh, 6);
		g.setStroke(Color.rgb(255, 255, 255, 0.14));
		g.setLineWidth(1);
		g.strokeRoundRect(bx, by, bw, bh, 12, 12);
		g.setTextAlign(TextAlignment.LEFT);
		g.setTextBaseline(VPos.TOP);
		for (int i = 0; i < lines.size(); i++)
		{
			g.setFill(lines.get(i).c());
			g.fillText(lines.get(i).t(), bx + 9, by + 7 + i * 16);
		}
	}

	public static void drawZoneStrip(GraphicsContext g, double plotW, double plotH, double width, double height)
	{
		double zy = PAD_T + plotH + 24, zh = 14;
		g.setFont(Fonts.mono(FontWeight.SEMI_BOLD, 8.5));
		for (Zone z : Engine.ZONES)
		{
			double x0 = fX(Math.max(Engine.FMIN, z.f0()), plotW);
			double x1 = fX(Math.min(Engine.FMAX, z.f1()), plotW);
			g.setGlobalAlpha(0.85);
			g.setFill(Color.web(z.colorHex()));
			g.fillRect(x0, zy, x1 - x0 - 1.5, zh);
			g.setGlobalAlpha(1);
			if (x1 - x0 > 36)
			{
				g.setFill(Color.rgb(0, 0, 0, 0.55));
				g.setTextAlign(TextAlignment.CENTER);
				g.setTextBaseline(VPos.CENTER);
				g.fillText(z.name(), (x0 + x1) / 2, zy + zh / 2 + 0.5);
			}
		}
	}

	private static String fmt1(double v)
	{
		return String.format(java.util.Locale.US, "%.1f", v);
	}
}
