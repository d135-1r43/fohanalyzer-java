package com.fohanalyzer.ui.controls;

import com.fohanalyzer.audio.AudioDevice;
import com.fohanalyzer.ui.InputPreset;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Source selector: a button + popup of live inputs and simulated presets, plus
 * a channel stepper for multi-channel live devices. Port of
 * ChannelSelect.svelte.
 */
public final class ChannelSelect extends VBox
{

	private final StringProperty value;
	private final List<InputPreset> options;
	private final ObservableList<AudioDevice> audioDevices;
	private final String color;
	private final IntegerProperty chanIdx;
	private final IntegerProperty chanCount;

	private final Label tag = new Label();
	private final Label valLabel = new Label();
	private final HBox stepper;
	private final Label idxLabel = new Label();

	public ChannelSelect(StringProperty value, List<InputPreset> options,
		ObservableList<AudioDevice> audioDevices, String color,
		IntegerProperty chanIdx, IntegerProperty chanCount)
	{
		this.value = value;
		this.options = options;
		this.audioDevices = audioDevices;
		this.color = color;
		this.chanIdx = chanIdx;
		this.chanCount = chanCount;
		setSpacing(6);

		Button btn = new Button();
		btn.getStyleClass().add("chan-btn");
		btn.setMaxWidth(Double.MAX_VALUE);
		tag.getStyleClass().add("chan-tag");
		valLabel.getStyleClass().add("chan-val");
		HBox.setHgrow(valLabel, Priority.ALWAYS);
		valLabel.setMaxWidth(Double.MAX_VALUE);
		Label caret = new Label("▾");
		caret.setStyle("-fx-text-fill:#5b6a7a; -fx-font-size:10px;");
		HBox box = new HBox(8, tag, valLabel, caret);
		box.setAlignment(Pos.CENTER_LEFT);
		btn.setGraphic(box);
		btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		btn.setOnAction(e -> showMenu(btn));

		// channel stepper
		Button prev = new Button("‹");
		Button next = new Button("›");
		prev.getStyleClass().add("chan-idx-btn");
		next.getStyleClass().add("chan-idx-btn");
		idxLabel.getStyleClass().add("chan-idx-label");
		idxLabel.setMaxWidth(Double.MAX_VALUE);
		idxLabel.setAlignment(Pos.CENTER);
		HBox.setHgrow(idxLabel, Priority.ALWAYS);
		prev.setOnAction(e -> {
			if (chanIdx.get() > 0) chanIdx.set(chanIdx.get() - 1);
		});
		next.setOnAction(e -> {
			if (chanIdx.get() < chanCount.get() - 1) chanIdx.set(chanIdx.get() + 1);
		});
		stepper = new HBox(6, prev, idxLabel, next);
		stepper.setAlignment(Pos.CENTER);
		stepper.setStyle("-fx-background-color:#0a0e14; -fx-border-color:#1c2733;"
			+ " -fx-border-radius:8; -fx-background-radius:8; -fx-padding:4 6 4 6;");

		getChildren().addAll(btn, stepper);

		value.addListener((o, a, b) -> refresh());
		chanIdx.addListener((o, a, b) -> refresh());
		chanCount.addListener((o, a, b) -> refresh());
		audioDevices.addListener((javafx.collections.ListChangeListener<AudioDevice>)c -> refresh());
		refresh();
	}

	private boolean isLive()
	{
		return value.get().startsWith("live:");
	}

	private void refresh()
	{
		boolean live = isLive();
		String label;
		if (live)
		{
			String id = value.get().substring(5);
			label = audioDevices.stream().filter(d -> d.id().equals(id))
				.findFirst().map(AudioDevice::label).orElse("Unknown device");
		}
		else
		{
			label = options.stream().filter(p -> p.id().equals(value.get()))
				.findFirst().map(InputPreset::label).orElse(options.isEmpty() ? "" : options.get(0).label());
		}
		valLabel.setText(label);
		tag.setText(live ? "LIVE" : "SIM");
		String tc = live ? "#a3e635" : color;
		tag.setStyle("-fx-text-fill:" + tc + "; -fx-border-color:" + tc + ";");

		boolean showStepper = live && chanCount.get() > 1;
		stepper.setVisible(showStepper);
		stepper.setManaged(showStepper);
		idxLabel.setText("Ch " + (chanIdx.get() + 1) + " / " + chanCount.get());
	}

	private void showMenu(Button anchor)
	{
		ContextMenu menu = new ContextMenu();
		if (!audioDevices.isEmpty())
		{
			menu.getItems().add(header("Live inputs"));
			for (AudioDevice d : audioDevices)
			{
				String devVal = "live:" + d.id();
				MenuItem mi = new MenuItem((value.get().equals(devVal) ? "● " : "   ") + d.label());
				mi.setOnAction(e -> {
					value.set(devVal);
					chanIdx.set(0);
				});
				menu.getItems().add(mi);
			}
			menu.getItems().add(new SeparatorMenuItem());
			menu.getItems().add(header("Simulated"));
		}
		for (InputPreset p : options)
		{
			MenuItem mi = new MenuItem((value.get().equals(p.id()) ? "● " : "   ") + p.label());
			mi.setOnAction(e -> {
				value.set(p.id());
				chanIdx.set(0);
			});
			menu.getItems().add(mi);
		}
		menu.show(anchor, Side.BOTTOM, 0, 4);
	}

	private static MenuItem header(String text)
	{
		MenuItem h = new MenuItem(text);
		h.setDisable(true);
		return h;
	}
}
