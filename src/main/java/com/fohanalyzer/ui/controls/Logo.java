package com.fohanalyzer.ui.controls;

import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;

/** The little spectrum-bars logo. Port of Logo.svelte (40×40 artwork scaled to 34px). */
public final class Logo extends Pane {

    public Logo() {
        setPrefSize(34, 34);
        setMinSize(34, 34);

        Rectangle frame = new Rectangle(1, 1, 38, 38);
        frame.setArcWidth(18);
        frame.setArcHeight(18);
        frame.setFill(Color.web("#0e141d"));
        frame.setStroke(Color.web("#1f2a37"));

        Group art = new Group(
            frame,
            roundBar(8, 22, 4.5, 10, "#22d3ee"),
            roundBar(14.5, 14, 4.5, 18, "#22d3ee"),
            roundBar(21, 9, 4.5, 23, "#a3e635"),
            roundBar(27.5, 18, 4.5, 14, "#f5a524")
        );
        art.getTransforms().add(new Scale(34.0 / 40.0, 34.0 / 40.0));
        getChildren().add(art);
    }

    private static Rectangle roundBar(double x, double y, double w, double h, String fill) {
        Rectangle r = new Rectangle(x, y, w, h);
        r.setArcWidth(3);
        r.setArcHeight(3);
        r.setFill(Color.web(fill));
        return r;
    }
}
