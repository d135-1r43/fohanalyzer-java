package com.fohanalyzer.ui.controls;

import com.fohanalyzer.audio.AudioDevice;
import com.fohanalyzer.ui.InputPreset;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.List;

/**
 * A source panel: indicator dot, name, on/off toggle, level meter, and a
 * marker-select pill.
 *
 * <p>
 * Which device feeds the source is chosen once per rig, so the picker itself
 * lives in the preferences window. What the card keeps is a read-only line
 * naming the current selection — during a show you still need to see at a
 * glance whether a trace is a live input or a simulation, and on which channel.
 */
public final class SourceCard extends VBox
{
	public final Meter meter;

	public SourceCard(String name, String color,
		BooleanProperty on, StringProperty markerSource, String markerId,
		List<InputPreset> options, StringProperty chan,
		IntegerProperty chanIdx, IntegerProperty chanCount,
		ObservableList<AudioDevice> audioDevices)
	{
		this(name, color, on, markerSource, markerId, options, chan, chanIdx, chanCount,
			audioDevices, null);
	}

	/**
	 * @param stereo
	 *            when non-null and set, the selection line reads the pair the
	 *            source is merging. Null for an always-mono source.
	 */
	public SourceCard(String name, String color,
		BooleanProperty on, StringProperty markerSource, String markerId,
		List<InputPreset> options, StringProperty chan,
		IntegerProperty chanIdx, IntegerProperty chanCount,
		ObservableList<AudioDevice> audioDevices, BooleanProperty stereo)
	{
		getStyleClass().add("src-card");
		setSpacing(9);

		Circle dot = new Circle(4.5, Color.web(color));
		Label nameLbl = new Label(name);
		nameLbl.getStyleClass().add("src-name");
		HBox.setHgrow(nameLbl, Priority.ALWAYS);
		nameLbl.setMaxWidth(Double.MAX_VALUE);
		Toggle toggle = new Toggle(on, color);
		HBox top = new HBox(10, dot, nameLbl, toggle);
		top.setAlignment(Pos.CENTER_LEFT);

		Label tag = new Label();
		tag.getStyleClass().add("chan-tag");
		Label selection = new Label();
		selection.getStyleClass().add("src-sel");
		HBox.setHgrow(selection, Priority.ALWAYS);
		selection.setMaxWidth(Double.MAX_VALUE);
		HBox sel = new HBox(8, tag, selection);
		sel.setAlignment(Pos.CENTER_LEFT);

		Runnable refreshSel = () -> {
			String value = chan.get();
			boolean live = SourceLabel.isLive(value);
			tag.setText(SourceLabel.tag(value));
			String tc = live ? "#a3e635" : color;
			tag.setStyle("-fx-text-fill:" + tc + "; -fx-border-color:" + tc + ";");
			// The channel only means anything on a multi-channel live input.
			String suffix = live && chanCount.get() > 1
				? "  ·  " + SourceLabel.channelShort(chanIdx.get(), stereo != null && stereo.get())
				: "";
			selection.setText(SourceLabel.of(value, options, audioDevices) + suffix);
		};
		chan.addListener((o, a, b) -> refreshSel.run());
		chanIdx.addListener((o, a, b) -> refreshSel.run());
		chanCount.addListener((o, a, b) -> refreshSel.run());
		if (stereo != null) stereo.addListener((o, a, b) -> refreshSel.run());
		audioDevices.addListener((javafx.collections.ListChangeListener<AudioDevice>)c -> refreshSel.run());
		refreshSel.run();

		meter = new Meter(color);
		Button markerPill = new Button();
		markerPill.getStyleClass().add("marker-pill");
		Runnable refreshPill = () -> {
			boolean isMarker = markerId.equals(markerSource.get());
			markerPill.setText(isMarker ? "◎ markers" : "○ markers");
			markerPill.setStyle(isMarker ? "-fx-border-color:" + color + "; -fx-text-fill:" + color
				+ "; -fx-font-weight:bold;" : "");
		};
		markerPill.setOnAction(e -> markerSource.set(markerId));
		markerSource.addListener((o, a, b) -> refreshPill.run());
		refreshPill.run();

		HBox bottom = new HBox(10, meter, markerPill);
		bottom.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(meter, Priority.ALWAYS);

		// dim the whole card when off
		on.addListener((o, a, b) -> setOpacity(b ? 1.0 : 0.5));
		setOpacity(on.get() ? 1.0 : 0.5);

		getChildren().addAll(top, sel, bottom);
	}
}
