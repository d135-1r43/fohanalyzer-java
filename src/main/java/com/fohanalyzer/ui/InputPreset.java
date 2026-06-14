package com.fohanalyzer.ui;

import com.fohanalyzer.engine.Voice;

/** A simulated source preset: id, display label, and its voicing. */
public record InputPreset(String id, String label, double g, double tilt) {
    public Voice voice() { return new Voice(g, tilt); }
}
