package com.fohanalyzer;

import com.fohanalyzer.ui.MainApp;
import javafx.application.Application;

/**
 * Entry point. Kept separate from the {@link Application} subclass so a plain
 * (non-modular) launch works without the JavaFX runtime on the initial
 * classpath.
 */
public final class Main
{
	static void main(String[] args)
	{
		Application.launch(MainApp.class, args);
	}
}
