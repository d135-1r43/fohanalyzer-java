package com.fohanalyzer.engine;

/**
 * State of the ring-out (simulated feedback) generator.
 * {@code t0} is a timestamp in seconds (origin matching the analyzer clock).
 */
public record Ring(boolean active, double fc, double t0) {
    public static final Ring INACTIVE = new Ring(false, 2500, 0);
}
