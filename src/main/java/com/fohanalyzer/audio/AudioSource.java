package com.fohanalyzer.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * Live capture from a {@link TargetDataLine}, exposing per-band dBFS and
 * broadband RMS.
 *
 * <p>
 * A daemon thread continuously reads PCM frames and maintains the most recent
 * {@link #FFT_SIZE} samples of the selected channel in a ring buffer.
 * {@link #readBands} / {@link #readRMS} snapshot that buffer and run the
 * transform off the capture thread.
 */
public final class AudioSource
{
	private static final Logger log = LoggerFactory.getLogger(AudioSource.class);

	public static final int FFT_SIZE = 16384;
	private static final float TARGET_RATE = 48000f;

	private TargetDataLine line;
	private Thread captureThread;
	private volatile boolean running;

	private volatile int channelCount = 1;
	private volatile int channelIndex = 0;
	private volatile boolean stereo;
	private float sampleRate = TARGET_RATE;
	private String deviceId;
	private volatile String error;

	// Ring buffers of the most recent FFT_SIZE samples. In mono only `ring` is
	// meaningful; in stereo it holds the selected channel and `ringR` the one
	// above it. Both are filled from the same frame so the two stay aligned.
	private final float[] ring = new float[FFT_SIZE];
	private final float[] ringR = new float[FFT_SIZE];
	private int ringPos = 0;
	private final Object ringLock = new Object();

	public boolean isConnected()
	{
		return line != null && running;
	}

	public int getChannelCount()
	{
		return channelCount;
	}

	public int getChannelIndex()
	{
		return channelIndex;
	}

	public boolean isStereo()
	{
		return stereo;
	}

	/**
	 * Read the selected channel and the one above it as a pair, or just the
	 * selected one.
	 *
	 * <p>
	 * The line is always opened at the device's full channel count, so this
	 * only changes how the captured frames are read — no reopen, and it is safe
	 * to flip while capturing. The channel index is re-clamped because a pair
	 * needs one more channel above it than a single does.
	 */
	public synchronized void setStereo(boolean stereo)
	{
		this.stereo = stereo;
		setChannel(channelIndex);
	}

	/**
	 * Highest channel index that can be selected: one below the last channel in
	 * stereo, since the pair extends upwards.
	 */
	private int maxChannelIndex()
	{
		return Math.max(0, channelCount - (stereo ? 2 : 1));
	}

	public String getError()
	{
		return error;
	}

	public float getSampleRate()
	{
		return sampleRate;
	}

	/**
	 * Open {@code device} and begin capturing. If already connected to the same
	 * device, only the channel selection changes. Returns the channel count (1
	 * on failure).
	 */
	public synchronized int connect(AudioDevice device, int channelIndex)
	{
		return connect(device, channelIndex, false);
	}

	/**
	 * As {@link #connect(AudioDevice, int)}, reading {@code channelIndex} and
	 * the channel above it as a stereo pair when {@code stereo} is set.
	 */
	public synchronized int connect(AudioDevice device, int channelIndex, boolean stereo)
	{
		this.stereo = stereo;
		if (isConnected() && device != null && device.id().equals(deviceId))
		{
			setChannel(channelIndex);
			return channelCount;
		}

		disconnect();
		error = null;

		if (device == null)
		{
			throw new IllegalArgumentException("device");
		}

		try
		{
			Mixer mixer = AudioSystem.getMixer(device.mixerInfo());
			AudioFormat format = chooseFormat(mixer);
			DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
			line = (TargetDataLine)mixer.getLine(info);
			line.open(format, (int)(format.getFrameSize() * format.getSampleRate() * 0.1));
			line.start();

			sampleRate = format.getSampleRate();
			channelCount = format.getChannels();
			this.channelIndex = Math.clamp(channelIndex, 0, maxChannelIndex());
			deviceId = device.id();

			running = true;
			captureThread = new Thread(() -> captureLoop(format), "audio-capture");
			captureThread.setDaemon(true);
			captureThread.start();

			// What was opened, once per connect. Which channel of how many, at
			// what rate, is the first thing worth knowing when a trace looks
			// wrong, and it is not visible anywhere else.
			log.info("capturing {} ch {}{} of {} at {} Hz", device.label(),
				this.channelIndex + 1, stereo ? "-" + (this.channelIndex + 2) : "",
				channelCount, (int)sampleRate);
		}
		catch (Exception e)
		{
			// Kept in `error` for a caller that wants to show it, but logged
			// here too: a device that refuses to open otherwise fails in total
			// silence and the plot just stays simulated.
			error = e.getMessage();
			log.warn("cannot capture {}", device.label(), e);
			disconnect();
		}
		return channelCount;
	}

	/**
	 * Open 16-bit PCM at the device's full channel count (so every input of a
	 * multi-channel interface like a Scarlett 18i20 or Behringer Wing is
	 * reachable), preferring 48 kHz then 44.1 kHz. Channel counts are read from
	 * the line's own supported formats rather than guessed, then tried
	 * largest-first.
	 */
	private AudioFormat chooseFormat(Mixer mixer)
	{
		int maxCh = maxSupportedChannels(mixer);
		// Distinct channel counts to try, largest first: full count, then
		// stereo, then mono.
		java.util.List<Integer> tries = new java.util.ArrayList<>();
		if (maxCh >= 1) tries.add(maxCh);
		if (maxCh > 2 && !tries.contains(2)) tries.add(2);
		if (!tries.contains(1)) tries.add(1);
		for (float rate : new float[] { TARGET_RATE, 44100f })
		{
			for (int ch : tries)
			{
				AudioFormat f = new AudioFormat(rate, 16, ch, true, false);
				if (mixer.isLineSupported(new DataLine.Info(TargetDataLine.class, f))) return f;
			}
		}
		// Last resort: full count at the target rate, let the system negotiate.
		return new AudioFormat(TARGET_RATE, 16, Math.max(1, maxCh), true, false);
	}

	/**
	 * Largest channel count advertised by any of the mixer's capture lines
	 * (default 2).
	 */
	private static int maxSupportedChannels(Mixer mixer)
	{
		int max = 0;
		for (javax.sound.sampled.Line.Info li : mixer.getTargetLineInfo())
		{
			if (li instanceof DataLine.Info dli
				&& TargetDataLine.class.isAssignableFrom(dli.getLineClass()))
			{
				for (AudioFormat f : dli.getFormats())
				{
					if (f.getChannels() != javax.sound.sampled.AudioSystem.NOT_SPECIFIED)
					{
						max = Math.max(max, f.getChannels());
					}
				}
			}
		}
		return max > 0 ? max : 2;
	}

	public synchronized void setChannel(int channelIndex)
	{
		if (channelCount <= 1)
		{
			this.channelIndex = 0;
			return;
		}
		this.channelIndex = Math.clamp(channelIndex, 0, maxChannelIndex());
	}

	public synchronized void disconnect()
	{
		running = false;
		if (captureThread != null)
		{
			captureThread.interrupt();
			captureThread = null;
		}
		if (line != null)
		{
			line.stop();
			line.close();
			line = null;
		}
		channelCount = 1;
		channelIndex = 0;
		deviceId = null;
		synchronized (ringLock)
		{
			java.util.Arrays.fill(ring, 0f);
			java.util.Arrays.fill(ringR, 0f);
			ringPos = 0;
		}
	}

	private void captureLoop(AudioFormat format)
	{
		int frameSize = format.getFrameSize();
		int ch = format.getChannels();
		int bytesPerSample = frameSize / ch;
		byte[] buf = new byte[frameSize * 2048];
		try
		{
			while (running && line != null)
			{
				int read = line.read(buf, 0, buf.length);
				if (read <= 0) continue;
				int frames = read / frameSize;
				int idx = channelIndex;
				// Read both sides from the same frame, so a later snapshot gets
				// two time-aligned windows. Guarded in case the pair would run
				// off the end of a device that shrank under us.
				boolean pair = stereo && idx + 1 < ch;
				synchronized (ringLock)
				{
					for (int fr = 0; fr < frames; fr++)
					{
						int base = fr * frameSize;
						ring[ringPos] = sampleToFloat(buf, base + idx * bytesPerSample, bytesPerSample);
						ringR[ringPos] = pair
							? sampleToFloat(buf, base + (idx + 1) * bytesPerSample, bytesPerSample)
							: 0f;
						ringPos = (ringPos + 1) % FFT_SIZE;
					}
				}
			}
		}
		catch (Exception e)
		{
			// disconnect() closes the line under this thread on purpose, and
			// the read then throws — expected, so it is only worth a line at
			// debug. Still running means the device went away on its own,
			// which the capture silently stopping would otherwise hide.
			if (running) log.warn("capture stopped unexpectedly", e);
			else
				log.debug("capture thread ended after close", e);
		}
	}

	/**
	 * Little-endian signed PCM sample -> [-1, 1] float. Handles 8/16/24/32-bit.
	 */
	private static float sampleToFloat(byte[] b, int off, int bytesPerSample)
	{
		return switch (bytesPerSample)
		{
			case 1 -> b[off] / 128f;
			case 2 -> (short)((b[off] & 0xff) | (b[off + 1] << 8)) / 32768f;
			case 3 -> ((b[off] & 0xff) | ((b[off + 1] & 0xff) << 8) | (b[off + 2] << 16)) / 8388608f;
			default -> ((b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)
				| ((b[off + 2] & 0xff) << 16) | (b[off + 3] << 24)) / 2147483648f;
		};
	}

	/** Latest FFT window in chronological order. */
	private float[] snapshotWindow()
	{
		float[] w = new float[FFT_SIZE];
		synchronized (ringLock)
		{
			int start = ringPos;
			for (int i = 0; i < FFT_SIZE; i++)
			{
				w[i] = ring[(start + i) % FFT_SIZE];
			}
		}
		return w;
	}

	/**
	 * Latest windows for both sides of the pair, taken under one lock so they
	 * describe the same span of time. Index 0 is the selected channel, 1 the
	 * one above it.
	 */
	private float[][] snapshotPair()
	{
		float[] l = new float[FFT_SIZE];
		float[] r = new float[FFT_SIZE];
		synchronized (ringLock)
		{
			int start = ringPos;
			for (int i = 0; i < FFT_SIZE; i++)
			{
				int j = (start + i) % FFT_SIZE;
				l[i] = ring[j];
				r[i] = ringR[j];
			}
		}
		return new float[][] { l, r };
	}

	/**
	 * Per-band dBFS levels, or {@code null} if not connected.
	 *
	 * <p>
	 * Null rather than an empty array: callers distinguish "no live signal,
	 * fall back to the simulation" from real data by a null check, and a
	 * zero-length array passes that check while carrying no bands. The window
	 * between creating a source and its background connect() completing is
	 * enough to hit it.
	 */
	public float[] readBands(double[] centers, int frac)
	{
		if (!isConnected()) return null;
		double binHz = sampleRate / FFT_SIZE;
		if (!stereo)
		{
			return AudioDsp.bands(AudioDsp.spectrumDb(snapshotWindow()), binHz, centers, frac);
		}
		// Each side is transformed on its own and only the magnitudes are
		// merged — see AudioDsp.mergePower for why this rather than summing the
		// two windows before the transform.
		float[][] w = snapshotPair();
		double[] merged = AudioDsp.mergePower(
			AudioDsp.spectrumDb(w[0]), AudioDsp.spectrumDb(w[1]));
		return AudioDsp.bands(merged, binHz, centers, frac);
	}

	/**
	 * Broadband RMS in dBFS, or {@code null} if not connected. A stereo pair is
	 * averaged in the power domain, matching {@link #readBands}.
	 */
	public Double readRMS()
	{
		if (!isConnected()) return null;
		if (!stereo) return AudioDsp.rmsDb(snapshotWindow());
		float[][] w = snapshotPair();
		return AudioDsp.powerMeanDb(AudioDsp.rmsDb(w[0]), AudioDsp.rmsDb(w[1]));
	}

	/**
	 * Fundamental of whatever is currently ringing, or {@code null} if not
	 * connected or nothing steady is present. Drives the ring-out assist's
	 * "detect from mic" action.
	 */
	public AudioDsp.Pitch readPitch()
	{
		if (!isConnected()) return null;
		if (!stereo) return AudioDsp.detectPitch(snapshotWindow(), sampleRate);
		// YIN needs a waveform, and a power-merged spectrum cannot be turned
		// back into one, so the pair is summed in the time domain here. A
		// ringing feedback tone is the same on both sides in practice, which is
		// the case this serves.
		float[][] w = snapshotPair();
		float[] mid = new float[FFT_SIZE];
		for (int i = 0; i < FFT_SIZE; i++)
		{
			mid[i] = (w[0][i] + w[1][i]) * 0.5f;
		}
		return AudioDsp.detectPitch(mid, sampleRate);
	}
}
