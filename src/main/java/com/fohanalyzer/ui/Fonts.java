package com.fohanalyzer.ui;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/** Shared monospace fonts and text measurement for canvas labels. */
public final class Fonts {

    /** A monospaced family available on the platform (Plex Mono if installed, else system). */
    public static final String MONO = pickMono();

    private static final Text MEASURE = new Text();

    private Fonts() {}

    private static String pickMono() {
        for (String name : new String[]{"IBM Plex Mono", "Menlo", "Consolas", "DejaVu Sans Mono"}) {
            if (Font.getFamilies().contains(name) || Font.getFontNames().contains(name)) return name;
        }
        return "monospace";
    }

    public static Font mono(double size) {
        return Font.font(MONO, size);
    }

    public static Font mono(FontWeight weight, double size) {
        return Font.font(MONO, weight, size);
    }

    /** Width of {@code s} rendered in {@code font} (FX-thread only). */
    public static double width(Font font, String s) {
        MEASURE.setFont(font);
        MEASURE.setText(s);
        return MEASURE.getLayoutBounds().getWidth();
    }
}
