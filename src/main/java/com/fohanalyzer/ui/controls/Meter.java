package com.fohanalyzer.ui.controls;

import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Thin horizontal level meter. Maps dBFS in [-90, -6] to a 0–100% fill.
 */
public final class Meter extends StackPane
{

	private final Region fill = new Region();
	private final String color;

	public Meter(String color)
	{
		this.color = color;
		setPrefHeight(6);
		setMinHeight(6);
		setMaxHeight(6);
		setStyle("-fx-background-color:#0a0e14; -fx-background-radius:3; -fx-border-radius:3;"
			+ " -fx-border-color:#161e28;");
		setMaxWidth(Double.MAX_VALUE);
		HBoxGrow();
		fill.setStyle("-fx-background-color:" + color + "; -fx-background-radius:3; -fx-opacity:0.85;");
		fill.setManaged(false);
		getChildren().add(fill);
		setValue(-90);
	}

	private void HBoxGrow()
	{
		javafx.scene.layout.HBox.setHgrow(this, Priority.ALWAYS);
	}

	public void setValue(double dbfs)
	{
		double pct = Math.max(0, Math.min(1, (dbfs + 90) / 84));
		fill.resizeRelocate(0, 0, getWidth() * pct, 6);
		// store for re-layout
		this.lastPct = pct;
	}

	private double lastPct = 0;

	@Override
	protected void layoutChildren()
	{
		fill.resizeRelocate(0, 0, getWidth() * lastPct, 6);
	}
}
