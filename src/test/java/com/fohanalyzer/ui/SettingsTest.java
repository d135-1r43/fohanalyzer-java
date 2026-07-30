package com.fohanalyzer.ui;

import com.fohanalyzer.audio.AudioDevice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs {@link Settings} against a throwaway {@link Preferences} node rather
 * than the real user store, so the suite neither reads nor leaves behind
 * anyone's actual calibration. Only {@code javafx.base} properties are involved
 * — no toolkit, so this stays a plain unit test.
 */
class SettingsTest
{
	private Preferences node;
	private Settings settings;

	@BeforeEach
	void setUp()
	{
		node = Preferences.userRoot().node("fohanalyzer-test/" + UUID.randomUUID());
		settings = new Settings(node);
	}

	@AfterEach
	void tearDown() throws Exception
	{
		Preferences parent = node.parent();
		node.removeNode();
		// the removal is only persistent once an ancestor is flushed
		parent.flush();
	}

	@Test
	void storedValuesComeBackOnTheNextLaunch()
	{
		AppState first = new AppState();
		settings.bind(first);
		first.splOffset.set(112.5);
		first.calRefSpl.set(100);
		first.frac.set(24);
		first.smoothing.set(0.4);
		first.avgN.set(8);
		first.peakHold.set(true);
		first.markers.set(false);
		first.markerSource.set("solo");
		first.showTransfer.set(true);

		AppState next = new AppState();
		new Settings(node).bind(next);

		assertEquals(112.5, next.splOffset.get(), 1e-9);
		assertEquals(100, next.calRefSpl.get(), 1e-9);
		assertEquals(24, next.frac.get());
		assertEquals(0.4, next.smoothing.get(), 1e-9);
		assertEquals(8, next.avgN.get());
		assertTrue(next.peakHold.get());
		assertFalse(next.markers.get());
		assertEquals("solo", next.markerSource.get());
		assertTrue(next.showTransfer.get());
	}

	@Test
	void anUntouchedStoreLeavesTheDefaultsAlone()
	{
		AppState st = new AppState();
		AppState defaults = new AppState();
		settings.bind(st);

		assertEquals(defaults.frac.get(), st.frac.get());
		assertEquals(defaults.smoothing.get(), st.smoothing.get(), 1e-9);
		assertEquals(defaults.micChan.get(), st.micChan.get());
		assertEquals(defaults.splOffset.get(), st.splOffset.get(), 1e-9);
	}

	@Test
	void valuesTheControlsCannotDisplayFallBackToDefaults()
	{
		node.putInt("frac", 7); // no such resolution button
		node.putInt("avgN", 3); // no such averaging button
		node.putDouble("smoothing", 4.2); // outside the slider's [0, 0.95]
		node.put("markerSource", "banana");

		AppState st = new AppState();
		AppState defaults = new AppState();
		settings.bind(st);

		assertEquals(defaults.frac.get(), st.frac.get());
		assertEquals(defaults.avgN.get(), st.avgN.get());
		assertEquals(defaults.smoothing.get(), st.smoothing.get(), 1e-9);
		assertEquals(defaults.markerSource.get(), st.markerSource.get());
	}

	@Nested
	class PrefsWindowBounds
	{
		@Test
		void roundTripsWhatWasStored()
		{
			settings.putPrefsWindow(120, 80, 500, 640);

			Settings.WindowBounds b = new Settings(node).prefsWindow();
			assertNotNull(b);
			assertEquals(120, b.x(), 1e-9);
			assertEquals(80, b.y(), 1e-9);
			assertEquals(500, b.w(), 1e-9);
			assertEquals(640, b.h(), 1e-9);
		}

		@Test
		void nothingStoredMeansNoBounds()
		{
			assertNull(settings.prefsWindow(), "a first run should fall back to the default size");
		}

		@Test
		void anUnusableStoredSizeIsIgnored()
		{
			// Hand-edited or written by a build that allowed it: too small to
			// show the controls, so the default is the better answer.
			node.putDouble("prefsWinX", 100);
			node.putDouble("prefsWinY", 100);
			node.putDouble("prefsWinW", 40);
			node.putDouble("prefsWinH", 30);

			assertNull(settings.prefsWindow());
		}

		@Test
		void aPartialRecordIsIgnored()
		{
			// Size but no position — all four or nothing.
			node.putDouble("prefsWinW", 500);
			node.putDouble("prefsWinH", 640);

			assertNull(settings.prefsWindow());
		}

		@Test
		void nonsenseIsNeverWritten()
		{
			settings.putPrefsWindow(Double.NaN, 0, 500, 640);
			settings.putPrefsWindow(0, 0, Double.POSITIVE_INFINITY, 640);

			assertNull(settings.prefsWindow());
		}

		@Test
		void resetForgetsTheWindowToo()
		{
			settings.putPrefsWindow(120, 80, 500, 640);
			settings.reset(new AppState());

			assertNull(settings.prefsWindow());
		}
	}

	@Test
	void savedLiveInputIsDroppedWhenTheInterfaceIsGone()
	{
		node.put("micChan", "live:Scarlett 18i20");
		node.putInt("micChanIdx", 7);

		// nothing enumerated: different venue, device absent
		AppState st = new AppState();
		settings.bind(st);

		assertEquals(new AppState().micChan.get(), st.micChan.get(),
			"a missing device should leave the simulated preset selected");
		assertFalse(st.isMicLive());
		assertEquals(0, st.micChanIdx.get(), "the channel index goes with the device");
	}

	@Test
	void savedLiveInputIsRestoredWhenTheInterfaceIsPresent()
	{
		node.put("micChan", "live:Scarlett 18i20");
		node.putInt("micChanIdx", 5);

		AppState st = new AppState();
		st.audioDevices.add(new AudioDevice("Scarlett 18i20", "Focusrite Scarlett 18i20", null));
		settings.bind(st);

		assertEquals("live:Scarlett 18i20", st.micChan.get());
		assertTrue(st.isMicLive());
		assertEquals(5, st.micChanIdx.get());
	}

	@Test
	void savedPresetThatNoLongerExistsIsIgnored()
	{
		node.put("soloChan", "preset-from-an-older-build");

		AppState st = new AppState();
		settings.bind(st);

		assertEquals(new AppState().soloChan.get(), st.soloChan.get());
	}

	@Test
	void resetRestoresDefaultsAndEmptiesTheStore() throws Exception
	{
		AppState st = new AppState();
		settings.bind(st);
		st.splOffset.set(99);
		st.frac.set(1);
		st.markerSource.set("solo");

		settings.reset(st);

		AppState defaults = new AppState();
		assertEquals(defaults.splOffset.get(), st.splOffset.get(), 1e-9);
		assertEquals(defaults.frac.get(), st.frac.get());
		assertEquals(defaults.markerSource.get(), st.markerSource.get());
		// Applying the defaults fires the persist listeners, so this also pins
		// down that the store is wiped afterwards rather than left holding
		// explicit copies of the defaults.
		assertEquals(0, node.keys().length, "reset should leave nothing stored");
	}
}
