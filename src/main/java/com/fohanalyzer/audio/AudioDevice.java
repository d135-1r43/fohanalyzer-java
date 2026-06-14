package com.fohanalyzer.audio;

import javax.sound.sampled.Mixer;

/**
 * An available audio input, identified by its {@link Mixer.Info}. The {@code id}
 * is a stable string (the mixer name) used the way the web app uses {@code deviceId}.
 */
public record AudioDevice(String id, String label, Mixer.Info mixerInfo) {
    @Override
    public String toString() { return label; }
}
