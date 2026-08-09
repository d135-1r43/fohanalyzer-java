package com.fohanalyzer.audio;

import com.fohanalyzer.engine.Engine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract of the read methods on a source that is not capturing. No audio
 * device is touched, so this runs anywhere.
 */
class AudioSourceTest
{
	private static final double[] CENTERS = Engine.bandCenters(12);

	@Test
	void freshSourceIsNotConnected()
	{
		AudioSource src = new AudioSource();
		assertFalse(src.isConnected());
		assertNull(src.getError());
	}

	/**
	 * Null, never an empty array. AnalyzerView tells live data from "fall back
	 * to the simulation" by a null check, and an empty array passes that check
	 * while carrying no bands — which cost a frame per pulse until it was
	 * fixed.
	 */
	@Test
	void readBandsIsNullWhileDisconnected()
	{
		assertNull(new AudioSource().readBands(CENTERS, 12));
	}

	@Test
	void readRmsAndPitchAreNullWhileDisconnected()
	{
		AudioSource src = new AudioSource();
		assertNull(src.readRMS());
		assertNull(src.readPitch());
	}

	@Test
	void disconnectIsIdempotentOnAFreshSource()
	{
		AudioSource src = new AudioSource();
		assertDoesNotThrow(src::disconnect);
		assertDoesNotThrow(src::disconnect);
		assertFalse(src.isConnected());
		assertNull(src.readBands(CENTERS, 12));
	}

	@Test
	void connectRejectsANullDevice()
	{
		assertThrows(IllegalArgumentException.class, () -> new AudioSource().connect(null, 0));
	}

	@Test
	void defaultsToMonoAndTracksTheStereoFlag()
	{
		AudioSource src = new AudioSource();
		assertFalse(src.isStereo());
		src.setStereo(true);
		assertTrue(src.isStereo());
		src.setStereo(false);
		assertFalse(src.isStereo());
	}

	/**
	 * A disconnected source reports one channel, so there is no room for a pair
	 * and the index has to stay at 0 rather than going negative.
	 */
	@Test
	void stereoOnASingleChannelSourceStaysAtIndexZero()
	{
		AudioSource src = new AudioSource();
		src.setStereo(true);
		src.setChannel(7);
		assertEquals(0, src.getChannelIndex());
	}
}
