package com.fohanalyzer.ui;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.io.InputStream;

/**
 * Shared fonts and text measurement for canvas labels.
 *
 * <p>
 * The design calls for IBM Plex Mono numerics against a squarish technical
 * sans, so both families ship with the app (OFL-1.1, see
 * {@code resources/com/fohanalyzer/fonts}) and are registered here rather than
 * left to whatever the host machine happens to have installed — without them
 * the app silently fell back to Helvetica Neue and Menlo.
 */
public final class Fonts
{
	private static final String FONT_DIR = "/com/fohanalyzer/fonts/";

	private static final String[] BUNDLED = {
		"IBMPlexMono-Regular.ttf", "IBMPlexMono-Bold.ttf",
		"Barlow-Regular.ttf", "Barlow-Bold.ttf",
	};

	static
	{
		// Must run before the pick call below, and before any stylesheet is
		// applied. Barlow is picked up by theme.css, which spells out its own
		// fallback chain, so only the mono family needs resolving here.
		loadBundled();
	}

	/** Monospaced family for readouts and canvas labels. */
	public static final String MONO = pick("IBM Plex Mono", "Menlo", "Consolas", "DejaVu Sans Mono");

	private static final Text MEASURE = new Text();

	private Fonts()
	{
	}

	/**
	 * Register the bundled families with the JavaFX font system. Call before
	 * building the scene: {@code -fx-font-family} in the stylesheet can only
	 * resolve what is loaded.
	 */
	public static void install()
	{
		// Touching the class runs the static initialiser; nothing else to do.
	}

	private static void loadBundled()
	{
		for (String file : BUNDLED)
		{
			try (InputStream in = Fonts.class.getResourceAsStream(FONT_DIR + file))
			{
				if (in != null) Font.loadFont(in, 12);
			}
			catch (Exception ignored)
			{
				// A missing or unreadable face just falls back to the next
				// family below.
			}
		}
	}

	private static String pick(String... names)
	{
		for (String name : names)
		{
			if (Font.getFamilies().contains(name) || Font.getFontNames().contains(name)) return name;
		}
		return Font.getDefault().getFamily();
	}

	public static Font mono(double size)
	{
		return Font.font(MONO, size);
	}

	public static Font mono(FontWeight weight, double size)
	{
		return Font.font(MONO, weight, size);
	}

	/** Width of {@code s} rendered in {@code font} (FX-thread only). */
	public static double width(Font font, String s)
	{
		MEASURE.setFont(font);
		MEASURE.setText(s);
		return MEASURE.getLayoutBounds().getWidth();
	}
}
