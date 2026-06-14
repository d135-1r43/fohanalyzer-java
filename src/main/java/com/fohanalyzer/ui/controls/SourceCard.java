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

/** A source panel: indicator dot, name, on/off toggle, channel selector, level meter,
 *  and a marker-select pill. Port of SourceCard.svelte. */
public final class SourceCard extends VBox {

    public final Meter meter;

    public SourceCard(String name, String sub, String color,
                      BooleanProperty on, StringProperty markerSource, String markerId,
                      List<InputPreset> options, StringProperty chan,
                      IntegerProperty chanIdx, IntegerProperty chanCount,
                      ObservableList<AudioDevice> audioDevices) {
        getStyleClass().add("src-card");
        setSpacing(11);

        Circle dot = new Circle(4.5, Color.web(color));
        Label nameLbl = new Label(name);
        nameLbl.getStyleClass().add("src-name");
        Label subLbl = new Label(sub);
        subLbl.getStyleClass().add("src-sub");
        VBox names = new VBox(2, nameLbl, subLbl);
        HBox.setHgrow(names, Priority.ALWAYS);
        Toggle toggle = new Toggle(on, color);
        HBox top = new HBox(10, dot, names, toggle);
        top.setAlignment(Pos.CENTER_LEFT);

        ChannelSelect chanSel = new ChannelSelect(chan, options, audioDevices, color, chanIdx, chanCount);

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

        getChildren().addAll(top, chanSel, bottom);
    }
}
