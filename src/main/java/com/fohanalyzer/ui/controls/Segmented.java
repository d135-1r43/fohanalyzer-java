package com.fohanalyzer.ui.controls;

import javafx.beans.property.IntegerProperty;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A segmented (radio-button row) control bound to an {@link IntegerProperty}.
 */
public final class Segmented extends HBox
{
	private final IntegerProperty value;
	private final Map<Integer, Button> buttons = new LinkedHashMap<>();

	/**
	 * {@code options} maps the integer value to its label, in display order.
	 */
	public Segmented(IntegerProperty value, Map<Integer, String> options)
	{
		this.value = value;
		getStyleClass().add("segmented");
		setSpacing(4);
		for (var e : options.entrySet())
		{
			Button b = new Button(e.getValue());
			b.getStyleClass().add("seg-btn");
			b.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(b, Priority.ALWAYS);
			int v = e.getKey();
			b.setOnAction(a -> value.set(v));
			buttons.put(v, b);
			getChildren().add(b);
		}
		value.addListener((o, a, b) -> refresh());
		refresh();
	}

	private void refresh()
	{
		int v = value.get();
		buttons.forEach((k, btn) -> {
			btn.getStyleClass().remove("on");
			if (k == v) btn.getStyleClass().add("on");
		});
	}
}
