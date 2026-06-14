package com.fohanalyzer.engine;

/**
 * Voicing applied to a (simulated) source: a uniform gain plus a tilt in dB/octave
 * around the 1 kHz pivot. Mirrors the {@code { g, tilt }} objects in App.svelte.
 */
public record Voice(double g, double tilt) {
    public static final Voice NEUTRAL = new Voice(0, 0);
}
