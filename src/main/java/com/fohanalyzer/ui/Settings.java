package com.fohanalyzer.ui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;

import java.util.List;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Persists the parts of {@link AppState} an engineer would resent re-entering:
 * SPL calibration, the selected inputs, and the analysis/view options.
 *
 * <p>
 * Backed by {@link Preferences}, so there is no dependency and no file format
 * of our own to version. Stored values are validated on the way in — the store
 * outlives the build that wrote it and can be edited by hand, so a stale or
 * nonsensical entry falls back to the built-in default rather than putting the
 * UI in a state its controls cannot represent.
 */
public final class Settings
{

	private static final String SPL_OFFSET = "splOffset";
	private static final String CAL_REF_SPL = "calRefSpl";
	private static final String MIC_CHAN = "micChan";
	private static final String SOLO_CHAN = "soloChan";
	private static final String MIC_CHAN_IDX = "micChanIdx";
	private static final String SOLO_CHAN_IDX = "soloChanIdx";
	private static final String FRAC = "frac";
	private static final String SMOOTHING = "smoothing";
	private static final String AVG_N = "avgN";
	private static final String PEAK_HOLD = "peakHold";
	private static final String MARKERS = "markers";
	private static final String MARKER_SOURCE = "markerSource";
	private static final String SHOW_TRANSFER = "showTransfer";
	private static final String PREFS_X = "prefsWinX";
	private static final String PREFS_Y = "prefsWinY";
	private static final String PREFS_W = "prefsWinW";
	private static final String PREFS_H = "prefsWinH";

	/**
	 * A window smaller than this cannot show its controls; larger is absurd.
	 */
	private static final double MIN_WIN = 320;
	private static final double MAX_WIN = 10000;

	/**
	 * Values the Resolution / Averaging segmented controls can actually
	 * display.
	 */
	private static final Set<Integer> FRACTIONS = Set.of(1, 3, 6, 12, 24);
	private static final Set<Integer> AVERAGES = Set.of(1, 2, 4, 8, 16);
	private static final Set<String> MARKER_SOURCES = Set.of("mic", "solo");

	private final Preferences prefs;

	public Settings()
	{
		this(Preferences.userNodeForPackage(AppState.class));
	}

	/**
	 * Test seam: run against a throwaway node instead of the real user store.
	 */
	Settings(Preferences prefs)
	{
		this.prefs = prefs;
	}

	/**
	 * Restore stored values into {@code state}, then keep the store in step as
	 * they change.
	 *
	 * <p>
	 * Call <em>after</em> the audio devices have been enumerated: a saved live
	 * input is only restored when its interface is actually present.
	 */
	public void bind(AppState state)
	{
		restore(state);
		persist(state);
	}

	/** Force pending writes to disk. Worth calling on window close. */
	public void flush()
	{
		try
		{
			prefs.flush();
		}
		catch (BackingStoreException e)
		{
			System.err.println("[settings] could not flush: " + e.getMessage());
		}
	}

	/**
	 * Forget every stored value and put {@code state} back to the built-in
	 * defaults.
	 */
	public void reset(AppState state)
	{
		// Read the defaults off a fresh AppState rather than repeating them
		// here, so they cannot drift from the field initialisers.
		AppState d = new AppState();
		state.splOffset.set(d.splOffset.get());
		state.calRefSpl.set(d.calRefSpl.get());
		state.frac.set(d.frac.get());
		state.smoothing.set(d.smoothing.get());
		state.avgN.set(d.avgN.get());
		state.peakHold.set(d.peakHold.get());
		state.markers.set(d.markers.get());
		state.markerSource.set(d.markerSource.get());
		state.showTransfer.set(d.showTransfer.get());
		state.micChanIdx.set(d.micChanIdx.get());
		state.soloChanIdx.set(d.soloChanIdx.get());
		state.micChan.set(d.micChan.get());
		state.soloChan.set(d.soloChan.get());

		// Wipe last: if bind() has run, the assignments above have already
		// fired their listeners and written the defaults back, so clearing
		// first would not leave the store empty. An empty store also means a
		// future change to a default is picked up instead of being shadowed by
		// a value we wrote out explicitly.
		try
		{
			prefs.clear();
			prefs.flush();
		}
		catch (BackingStoreException e)
		{
			System.err.println("[settings] could not clear: " + e.getMessage());
		}
	}

	// ---- window geometry --------------------------------------------------

	/** Where a window was last left. All four values or none. */
	public record WindowBounds(double x, double y, double w, double h)
	{
	}

	/**
	 * Bounds of the preferences window, or {@code null} when nothing usable is
	 * stored.
	 *
	 * <p>
	 * A stored size outside {@link #MIN_WIN} … {@link #MAX_WIN} is treated as
	 * nothing stored: the window would open unusably small or absurdly large,
	 * and the default is a better answer than honouring it. Whether the
	 * position is still on a screen is the caller's business — this class has
	 * no view of the displays.
	 */
	public WindowBounds prefsWindow()
	{
		double w = prefs.getDouble(PREFS_W, Double.NaN);
		double h = prefs.getDouble(PREFS_H, Double.NaN);
		double x = prefs.getDouble(PREFS_X, Double.NaN);
		double y = prefs.getDouble(PREFS_Y, Double.NaN);
		if (!Double.isFinite(x) || !Double.isFinite(y)) return null;
		if (!inRange(w) || !inRange(h)) return null;
		return new WindowBounds(x, y, w, h);
	}

	/** Remembers where the preferences window was left. */
	public void putPrefsWindow(double x, double y, double w, double h)
	{
		if (!Double.isFinite(x) || !Double.isFinite(y) || !inRange(w) || !inRange(h)) return;
		prefs.putDouble(PREFS_X, x);
		prefs.putDouble(PREFS_Y, y);
		prefs.putDouble(PREFS_W, w);
		prefs.putDouble(PREFS_H, h);
	}

	private static boolean inRange(double v)
	{
		return Double.isFinite(v) && v >= MIN_WIN && v <= MAX_WIN;
	}

	// ---- restore ----------------------------------------------------------

	private void restore(AppState st)
	{
		// Each property's current value is its own default, so AppState stays
		// the single source of truth for what "unset" means.
		st.splOffset.set(clamp(prefs.getDouble(SPL_OFFSET, st.splOffset.get()), -200, 200,
			st.splOffset.get()));
		st.calRefSpl.set(clamp(prefs.getDouble(CAL_REF_SPL, st.calRefSpl.get()), 0, 200,
			st.calRefSpl.get()));
		st.smoothing.set(clamp(prefs.getDouble(SMOOTHING, st.smoothing.get()), 0, 0.95,
			st.smoothing.get()));

		int frac = prefs.getInt(FRAC, st.frac.get());
		if (FRACTIONS.contains(frac)) st.frac.set(frac);
		int avg = prefs.getInt(AVG_N, st.avgN.get());
		if (AVERAGES.contains(avg)) st.avgN.set(avg);
		String marker = prefs.get(MARKER_SOURCE, st.markerSource.get());
		if (MARKER_SOURCES.contains(marker)) st.markerSource.set(marker);

		st.peakHold.set(prefs.getBoolean(PEAK_HOLD, st.peakHold.get()));
		st.markers.set(prefs.getBoolean(MARKERS, st.markers.get()));
		st.showTransfer.set(prefs.getBoolean(SHOW_TRANSFER, st.showTransfer.get()));

		restoreSource(st, true);
		restoreSource(st, false);
	}

	/**
	 * Restore one source selection. A stored {@code live:<deviceId>} only
	 * survives if that interface is plugged in — moving between rigs is the
	 * normal case, not an error — so a missing device silently leaves the
	 * simulated preset selected rather than pointing the UI at a line that
	 * cannot be opened.
	 */
	private void restoreSource(AppState st, boolean isMic)
	{
		String saved = prefs.get(isMic ? MIC_CHAN : SOLO_CHAN, null);
		if (saved == null || saved.isBlank()) return;

		boolean available = saved.startsWith("live:")
			? st.audioDevices.stream().anyMatch(d -> saved.equals("live:" + d.id()))
			: presets(isMic).stream().anyMatch(p -> p.id().equals(saved));
		if (!available) return;

		StringProperty chan = isMic ? st.micChan : st.soloChan;
		IntegerProperty idx = isMic ? st.micChanIdx : st.soloChanIdx;
		chan.set(saved);
		// Only the lower bound is knowable here; the channel count arrives when
		// the line opens, and MainApp clamps against it then.
		idx.set(Math.max(0, prefs.getInt(isMic ? MIC_CHAN_IDX : SOLO_CHAN_IDX, 0)));
	}

	private static List<InputPreset> presets(boolean isMic)
	{
		return isMic ? AppState.MIC_INPUTS : AppState.SOLO_INPUTS;
	}

	private static double clamp(double v, double lo, double hi, double fallback)
	{
		if (!Double.isFinite(v) || v < lo || v > hi) return fallback;
		return v;
	}

	// ---- persist ----------------------------------------------------------

	private void persist(AppState st)
	{
		// Calibration is user-initiated, rare, and the most expensive to redo,
		// so it is flushed the moment it changes rather than waiting for the
		// periodic sync.
		st.splOffset.addListener((o, a, v) -> {
			prefs.putDouble(SPL_OFFSET, v.doubleValue());
			flush();
		});
		st.calRefSpl.addListener((o, a, v) -> {
			prefs.putDouble(CAL_REF_SPL, v.doubleValue());
			flush();
		});

		// The rest change often enough (slider drags) that flushing each time
		// would be wasteful; Preferences syncs periodically and on a clean
		// exit, and MainApp flushes on window close.
		st.micChan.addListener((o, a, v) -> prefs.put(MIC_CHAN, v));
		st.soloChan.addListener((o, a, v) -> prefs.put(SOLO_CHAN, v));
		st.micChanIdx.addListener((o, a, v) -> prefs.putInt(MIC_CHAN_IDX, v.intValue()));
		st.soloChanIdx.addListener((o, a, v) -> prefs.putInt(SOLO_CHAN_IDX, v.intValue()));
		st.frac.addListener((o, a, v) -> prefs.putInt(FRAC, v.intValue()));
		st.smoothing.addListener((o, a, v) -> prefs.putDouble(SMOOTHING, v.doubleValue()));
		st.avgN.addListener((o, a, v) -> prefs.putInt(AVG_N, v.intValue()));
		st.peakHold.addListener((o, a, v) -> prefs.putBoolean(PEAK_HOLD, v));
		st.markers.addListener((o, a, v) -> prefs.putBoolean(MARKERS, v));
		st.markerSource.addListener((o, a, v) -> prefs.put(MARKER_SOURCE, v));
		st.showTransfer.addListener((o, a, v) -> prefs.putBoolean(SHOW_TRANSFER, v));
	}
}
