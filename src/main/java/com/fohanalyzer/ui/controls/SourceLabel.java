package com.fohanalyzer.ui.controls;

import com.fohanalyzer.audio.AudioDevice;
import com.fohanalyzer.ui.InputPreset;

import java.util.List;

/**
 * Resolves a stored source value — {@code "live:<device id>"} or a simulated
 * preset id — to what the user should read.
 *
 * <p>
 * The picker in {@link ChannelSelect} and the read-only line on
 * {@link SourceCard} have to agree on that wording, and they live in different
 * places now that the picker sits in the preferences window, so the rule lives
 * here rather than in either of them.
 */
final class SourceLabel
{
	static final String LIVE_PREFIX = "live:";

	private SourceLabel()
	{
	}

	static boolean isLive(String value)
	{
		return value.startsWith(LIVE_PREFIX);
	}

	/** {@code "LIVE"} or {@code "SIM"} — the tag next to the name. */
	static String tag(String value)
	{
		return isLive(value) ? "LIVE" : "SIM";
	}

	/**
	 * The device or preset name. A live value whose device is not currently
	 * present reads as unknown rather than falling back to a preset, since the
	 * two mean different things to whoever is looking at the rail.
	 */
	static String of(String value, List<InputPreset> options, List<AudioDevice> devices)
	{
		if (isLive(value))
		{
			String id = value.substring(LIVE_PREFIX.length());
			return devices.stream().filter(d -> d.id().equals(id))
				.findFirst().map(AudioDevice::label).orElse("Unknown device");
		}
		return options.stream().filter(p -> p.id().equals(value))
			.findFirst().map(InputPreset::label)
			.orElse(options.isEmpty() ? "" : options.get(0).label());
	}
}
