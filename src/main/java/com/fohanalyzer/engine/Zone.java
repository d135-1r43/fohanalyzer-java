package com.fohanalyzer.engine;

/** A labelled frequency region drawn in the colour strip below the plot. */
public record Zone(double f0, double f1, String name, String colorHex)
{
}
