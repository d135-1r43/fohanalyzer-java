package com.fohanalyzer.ui.controls;

import javafx.beans.property.BooleanProperty;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * A 38×22 on/off switch bound to a {@link BooleanProperty}. Port of
 * Toggle.svelte.
 */
public final class Toggle extends Region
{

	private final Circle knob = new Circle(8);
	private final BooleanProperty value;
	private final String color;

	public Toggle(BooleanProperty value, String color)
	{
		this.value = value;
		this.color = color;
		setPrefSize(38, 22);
		setMinSize(38, 22);
		setMaxSize(38, 22);
		knob.setManaged(false);
		getChildren().add(knob);
		setOnMouseClicked(e -> value.set(!value.get()));
		value.addListener((o, a, b) -> render());
		setCursor(javafx.scene.Cursor.HAND);
		render();
	}

	private void render()
	{
		boolean on = value.get();
		String bg = on ? color : "#1a232e";
		String border = on ? color : "#1c2733";
		setStyle("-fx-background-radius:12; -fx-background-color:" + bg
			+ "; -fx-border-radius:12; -fx-border-color:" + border + ";");
		knob.setFill(on ? Color.web("#07120a") : Color.web("#6b7686"));
		requestLayout();
	}

	@Override
	protected void layoutChildren()
	{
		boolean on = value.get();
		knob.setCenterX(on ? 28 : 12);
		knob.setCenterY(11);
	}
}
