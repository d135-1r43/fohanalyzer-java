package com.fohanalyzer.ui.controls;

import com.fohanalyzer.audio.AudioDevice;
import com.fohanalyzer.ui.InputPreset;
import javafx.beans.property.BooleanProperty;
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
 * a channel stepper for multi-channel live devices.
 */
public final class ChannelSelect extends VBox
{
	private final StringProperty value;
	private final List<InputPreset> options;
	private final ObservableList<AudioDevice> audioDevices;
	private final String color;
	private final IntegerProperty chanIdx;
	private final IntegerProperty chanCount;
	private final BooleanProperty stereo;

	private final Label tag = new Label();
	private final Label valLabel = new Label();
	private final HBox stepper;
	private final Label idxLabel = new Label();
	private final HBox modeRow;

	public ChannelSelect(StringProperty value, List<InputPreset> options,
		ObservableList<AudioDevice> audioDevices, String color,
		IntegerProperty chanIdx, IntegerProperty chanCount)
	{
		this(value, options, audioDevices, color, chanIdx, chanCount, null);
	}

	/**
	 * @param stereo
	 *            when non-null, adds a mono/stereo choice and the stepper
	 *            selects the first channel of a pair. Null for a source that is
	 *            always mono.
	 */
	public ChannelSelect(StringProperty value, List<InputPreset> options,
		ObservableList<AudioDevice> audioDevices, String color,
		IntegerProperty chanIdx, IntegerProperty chanCount, BooleanProperty stereo)
	{
		this.value = value;
		this.options = options;
		this.audioDevices = audioDevices;
		this.color = color;
		this.chanIdx = chanIdx;
		this.chanCount = chanCount;
		this.stereo = stereo;
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
			if (chanIdx.get() < lastIdx()) chanIdx.set(chanIdx.get() + 1);
		});
		stepper = new HBox(6, prev, idxLabel, next);
		stepper.setAlignment(Pos.CENTER);
		stepper.setStyle("-fx-background-color:#0a0e14; -fx-border-color:#1c2733;"
			+ " -fx-border-radius:8; -fx-background-radius:8; -fx-padding:4 6 4 6;");

		modeRow = buildModeRow();

		getChildren().addAll(btn, stepper);
		if (modeRow != null) getChildren().add(modeRow);

		value.addListener((o, a, b) -> refresh());
		chanIdx.addListener((o, a, b) -> refresh());
		chanCount.addListener((o, a, b) -> refresh());
		if (stereo != null) stereo.addListener((o, a, b) -> refresh());
		audioDevices.addListener((javafx.collections.ListChangeListener<AudioDevice>)c -> refresh());
		refresh();
	}

	private boolean isLive()
	{
		return SourceLabel.isLive(value.get());
	}

	private boolean isStereo()
	{
		return stereo != null && stereo.get();
	}

	/**
	 * Last selectable index. A stereo pair extends upwards into the next
	 * channel, so it cannot start on the final one.
	 */
	private int lastIdx()
	{
		return Math.max(0, chanCount.get() - (isStereo() ? 2 : 1));
	}

	/** Mono/stereo pair of buttons, or null for an always-mono source. */
	private HBox buildModeRow()
	{
		if (stereo == null) return null;
		Button mono = new Button("Mono");
		Button pair = new Button("Stereo");
		mono.getStyleClass().add("chan-mode-btn");
		pair.getStyleClass().add("chan-mode-btn");
		mono.setMaxWidth(Double.MAX_VALUE);
		pair.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(mono, Priority.ALWAYS);
		HBox.setHgrow(pair, Priority.ALWAYS);
		mono.setOnAction(e -> stereo.set(false));
		pair.setOnAction(e -> stereo.set(true));
		Runnable style = () -> {
			boolean s = isStereo();
			mono.setStyle(s ? ""
				: "-fx-text-fill:" + color + "; -fx-border-color:" + color
					+ "; -fx-font-weight:bold;");
			pair.setStyle(s ? "-fx-text-fill:" + color + "; -fx-border-color:" + color
				+ "; -fx-font-weight:bold;" : "");
		};
		stereo.addListener((o, a, b) -> style.run());
		style.run();
		HBox row = new HBox(6, mono, pair);
		row.setAlignment(Pos.CENTER);
		return row;
	}

	private void refresh()
	{
		boolean live = isLive();
		valLabel.setText(SourceLabel.of(value.get(), options, audioDevices));
		tag.setText(SourceLabel.tag(value.get()));
		String tc = live ? "#a3e635" : color;
		tag.setStyle("-fx-text-fill:" + tc + "; -fx-border-color:" + tc + ";");

		// A pair needs two channels to exist before the choice means anything.
		if (modeRow != null)
		{
			boolean showMode = live && chanCount.get() > 1;
			modeRow.setVisible(showMode);
			modeRow.setManaged(showMode);
		}
		if (isStereo() && chanIdx.get() > lastIdx()) chanIdx.set(lastIdx());

		boolean showStepper = live && chanCount.get() > 1;
		stepper.setVisible(showStepper);
		stepper.setManaged(showStepper);
		idxLabel.setText(SourceLabel.channel(chanIdx.get(), chanCount.get(), isStereo()));
	}

	private void showMenu(Button anchor)
	{
		ContextMenu menu = new ContextMenu();
		if (!audioDevices.isEmpty())
		{
			menu.getItems().add(header("Live inputs"));
			for (AudioDevice d : audioDevices)
			{
				String devVal = SourceLabel.LIVE_PREFIX + d.id();
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
