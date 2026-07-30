package com.fohanalyzer.audio;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;

/**
 * Enumerates the audio capture devices the host machine offers.
 */
public final class AudioInputs
{
	private AudioInputs()
	{
	}

	/**
	 * All mixers that expose at least one capture ({@link TargetDataLine})
	 * line.
	 */
	public static List<AudioDevice> enumerate()
	{
		List<AudioDevice> out = new ArrayList<>();
		for (Mixer.Info info : AudioSystem.getMixerInfo())
		{
			Mixer mixer = AudioSystem.getMixer(info);
			boolean hasCapture = false;
			for (Line.Info li : mixer.getTargetLineInfo())
			{
				if (li instanceof DataLine.Info dli
					&& TargetDataLine.class.isAssignableFrom(dli.getLineClass()))
				{
					hasCapture = true;
					break;
				}
			}
			if (hasCapture)
			{
				String name = info.getName();
				out.add(new AudioDevice(name, name, info));
			}
		}
		return out;
	}
}
