package com.fohanalyzer.ui;

import com.fohanalyzer.dsp.SignalState;
import com.fohanalyzer.dsp.Stats;
import com.fohanalyzer.engine.Engine;
import com.fohanalyzer.engine.Ring;
import com.fohanalyzer.engine.Voice;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * The analyzer plot: a resizable {@link Canvas} driven by an
 * {@link AnimationTimer}. Port of {@code src/lib/AnalyzerCanvas.svelte}; reads
 * everything it needs from {@link AppState}.
 */
public final class AnalyzerView extends Region
{

	// ~45 Hz, matching the original setInterval(22)
	private static final double FRAME_SEC = 0.022;

	private final AppState state;
	private final Canvas canvas = new Canvas();
	private final SignalState sig = new SignalState();

	private long t0Nanos = 0;
	private long lastFrameNanos = 0;
	private double lastReport = 0;
	private int lastCapture = 0;
	private int holdResetStamp = 0;

	private double mouseX, mouseY;
	private boolean mouseInside;

	public AnalyzerView(AppState state)
	{
		this.state = state;
		getChildren().add(canvas);

		canvas.setOnMouseMoved(e -> {
			mouseX = e.getX();
			mouseY = e.getY();
			mouseInside = true;
		});
		canvas.setOnMousePressed(e -> {
			mouseX = e.getX();
			mouseY = e.getY();
			mouseInside = true;
		});
		canvas.setOnMouseExited(e -> mouseInside = false);

		AnimationTimer timer = new AnimationTimer()
		{
			@Override
			public void handle(long now)
			{
				if (t0Nanos == 0) t0Nanos = now;
				if (now - lastFrameNanos < FRAME_SEC * 1e9) return;
				lastFrameNanos = now;
				frame((now - t0Nanos) / 1e9);
			}
		};
		timer.start();
	}

	@Override
	protected void layoutChildren()
	{
		double w = getWidth(), h = getHeight();
		canvas.setWidth(w);
		canvas.setHeight(h);
	}

	private void frame(double t)
	{
		double width = canvas.getWidth(), height = canvas.getHeight();
		if (width <= 0 || height <= 0) return;
		GraphicsContext g = canvas.getGraphicsContext2D();
		double plotW = width - Draw.PAD_L - Draw.PAD_R;
		double plotH = height - Draw.PAD_T - Draw.PAD_B;
		if (plotW <= 0 || plotH <= 0) return;

		g.clearRect(0, 0, width, height);

		int frac = state.frac.get();
		double[] centers = Engine.bandCenters(frac);
		sig.ensureBands(frac, centers.length);

		int holdReset = state.holdReset.get();
		if (holdReset != holdResetStamp)
		{
			holdResetStamp = holdReset;
			sig.resetHold();
		}

		Ring ring = state.ring.get();
		Engine.SimFrame sim = Engine.sample(centers, t, ring);

		float[] liveMic = state.micSource != null ? state.micSource.readBands(centers, frac) : null;
		float[] liveSolo = state.soloSource != null ? state.soloSource.readBands(centers, frac) : null;

		Voice mvc = liveMic != null ? Voice.NEUTRAL : state.micVoice();
		Voice svc = liveSolo != null ? Voice.NEUTRAL : state.soloVoice();

		sig.update(liveMic != null ? liveMic : sim.mic(),
			liveSolo != null ? liveSolo : sim.solo(),
			mvc, svc, centers, state.smoothing.get(), state.avgN.get());

		if (t - lastReport > 0.12)
		{
			lastReport = t;
			Stats s = sig.getStats(centers);
			if (state.micSource != null) s = s.withMicRms(state.micSource.readRMS());
			state.stats.set(s);
		}

		int captureNonce = state.captureNonce.get();
		if (captureNonce != lastCapture)
		{
			lastCapture = captureNonce;
			if (captureNonce > 0)
			{
				state.reference.set(new Draw.Reference(
					centers.clone(), sig.dispMic.clone(), sig.dispSolo.clone(),
					java.time.LocalTime.now().toString().substring(0, 5)));
				state.showReference.set(true);
			}
		}

		boolean micOn = state.micOn.get(), soloOn = state.soloOn.get();
		boolean showTransfer = state.showTransfer.get();

		Draw.drawBackground(g, width, height);
		Draw.drawGrid(g, width, plotW, plotH);

		Draw.BarLayout layout = Draw.computeBarLayout(centers, plotW);
		double[] xs = layout.xs();
		double[] barW = layout.barW();
		double baseY = Draw.PAD_T + plotH;
		double aT = showTransfer ? 0.3 : 0.5;
		double aB = showTransfer ? 0.05 : 0.07;
		boolean tfShown = showTransfer && micOn && soloOn;

		g.save();
		g.beginPath();
		g.rect(Draw.PAD_L, Draw.PAD_T, plotW, plotH);
		g.clip();

		if (soloOn) Draw.drawBars(g, sig.dispSolo, xs, barW, plotH, baseY, 245, 165, 36, aT, aB);
		if (micOn) Draw.drawBars(g, sig.dispMic, xs, barW, plotH, baseY, 34, 211, 238, aT, aB);
		if (state.peakHold.get())
		{
			if (soloOn) Draw.drawHoldTicks(g, sig.holdSolo, xs, barW, plotH, Color.rgb(245, 165, 36, 0.85));
			if (micOn) Draw.drawHoldTicks(g, sig.holdMic, xs, barW, plotH, Color.rgb(34, 211, 238, 0.9));
		}
		Draw.Reference ref = state.reference.get();
		if (ref != null && state.showReference.get()) Draw.drawReference(g, ref, plotW, plotH, micOn, soloOn);
		if (tfShown) Draw.drawTransferCurve(g, centers, sig.dispMic, sig.dispSolo, width, plotW, plotH);

		g.restore();

		if (tfShown) Draw.drawTransferLabels(g, width, plotW, plotH);
		if (state.markers.get())
		{
			boolean solo = "solo".equals(state.markerSource.get());
			float[] src = solo ? sig.dispSolo : sig.dispMic;
			boolean on = solo ? soloOn : micOn;
			Color col = solo ? Color.web("#f5a524") : Color.web("#22d3ee");
			if (on) Draw.drawPeakMarkers(g, src, centers, plotW, plotH, width, col);
		}
		Double locate = state.locateFreq.get();
		if (locate != null) Draw.drawLocateLine(g, locate, width, plotW, plotH);
		if (mouseInside)
		{
			Draw.drawHoverTooltip(g, new Draw.Mouse(mouseX, mouseY, true), centers, xs,
				sig.dispMic, sig.dispSolo, width, plotW, plotH, micOn, soloOn);
		}
		Draw.drawZoneStrip(g, plotW, plotH, width, height);
	}
}
