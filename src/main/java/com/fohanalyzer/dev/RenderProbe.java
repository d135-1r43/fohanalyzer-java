package com.fohanalyzer.dev;

import com.fohanalyzer.engine.Ring;
import com.fohanalyzer.ui.AnalyzerView;
import com.fohanalyzer.ui.AppState;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;

/**
 * Headless-ish render check: builds the analyzer in simulation mode, lets it run a
 * couple of seconds, snapshots the canvas to {@code target/probe.png}, then exits.
 * Run with: {@code mvn javafx:run -Dapp.mainClass=com.fohanalyzer.dev.RenderProbe}
 */
public class RenderProbe extends Application {
    @Override
    public void start(Stage stage) {
        com.fohanalyzer.ui.Fonts.install();
        AppState state = new AppState();
        state.peakHold.set(true);
        state.showTransfer.set(true);
        state.ring.set(new Ring(true, 2500, 0)); // exercise the ring-out path

        AnalyzerView analyzer = new AnalyzerView(state);
        BorderPane root = new BorderPane(analyzer);
        root.setStyle("-fx-background-color:#070a0f;");
        Scene scene = new Scene(root, 1100, 640, Color.web("#070a0f"));
        stage.setScene(scene);
        stage.show();

        PauseTransition wait = new PauseTransition(Duration.seconds(2.0));
        wait.setOnFinished(e -> {
            try {
                WritableImage img = analyzer.snapshot(new SnapshotParameters(), null);
                File out = new File("target/probe.png");
                ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
                System.out.println("PROBE_WROTE " + out.getAbsolutePath());
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                Platform.exit();
            }
        });
        wait.play();
    }
}
