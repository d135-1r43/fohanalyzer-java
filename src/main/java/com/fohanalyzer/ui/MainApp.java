package com.fohanalyzer.ui;

import com.fohanalyzer.audio.AudioDevice;
import com.fohanalyzer.audio.AudioInputs;
import com.fohanalyzer.audio.AudioSource;
import com.fohanalyzer.dsp.Stats;
import com.fohanalyzer.ui.controls.Logo;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** Application root: header + analyzer + control rail, with live-audio wiring. */
public class MainApp extends Application {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final AppState state = new AppState();

    private Label peakVal, micVal, soloVal, splVal, clockLabel;
    private HBox splChip;

    @Override
    public void start(Stage stage) {
        AnalyzerView analyzer = new AnalyzerView(state);
        BorderPane.setMargin(analyzer, new Insets(12));

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setTop(buildHeader());
        root.setCenter(analyzer);
        root.setRight(new ControlRail(state));

        Scene scene = new Scene(root, 1366, 820, Color.web("#070a0f"));
        scene.getStylesheets().add(getClass().getResource("/com/fohanalyzer/theme.css").toExternalForm());
        stage.setTitle("FOHanalyzer");
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
        // Launched from a CLI/Maven process on macOS the window can open behind the
        // terminal; pull it to the front and give it focus.
        stage.setAlwaysOnTop(true);
        stage.toFront();
        stage.requestFocus();
        stage.setAlwaysOnTop(false);

        state.stats.addListener((o, a, st) -> updateReadouts(st));
        updateReadouts(state.stats.get());

        wireLiveAudio();
        startClock();
        refreshDevices();

        stage.setOnCloseRequest(e -> {
            if (state.micSource != null) state.micSource.disconnect();
            if (state.soloSource != null) state.soloSource.disconnect();
        });

        if ("true".equals(System.getenv("PROBE"))) maybeSnapshot(root);
    }

    /** Dev aid: with -Dprobe=true, snapshot the whole window to target/probe-full.png and exit. */
    private void maybeSnapshot(BorderPane root) {
        var wait = new javafx.animation.PauseTransition(Duration.seconds(2));
        wait.setOnFinished(e -> {
            try {
                var img = root.snapshot(new javafx.scene.SnapshotParameters(), null);
                javax.imageio.ImageIO.write(
                    javafx.embed.swing.SwingFXUtils.fromFXImage(img, null), "png",
                    new java.io.File("target/probe-full.png"));
                System.out.println("PROBE_WROTE target/probe-full.png");
            } catch (Exception ex) { ex.printStackTrace(); }
            finally { Platform.exit(); }
        });
        wait.play();
    }

    // ---- header -----------------------------------------------------------
    private HBox buildHeader() {
        Label top = new Label("FOHanalyzer");
        top.getStyleClass().add("wm-top");
        Label sub = new Label("DUAL SPECTRUM · RTA");
        sub.getStyleClass().add("wm-sub");
        VBox wordmark = new VBox(4, top, sub);
        Label ver = new Label("2.3.1");
        ver.getStyleClass().add("ver-chip");
        HBox brand = new HBox(12, new Logo(), wordmark, ver);
        brand.setAlignment(Pos.CENTER_LEFT);

        peakVal = new Label("—");
        micVal = new Label("-90");
        soloVal = new Label("-90");
        splVal = new Label("—");
        HBox readouts = new HBox(10,
            readoutChip("PEAK", peakVal, "#e9fb9b"),
            readoutChip("MIC", micVal, "#22d3ee"),
            readoutChip("SOLO", soloVal, "#f5a524"));
        splChip = readoutChip("SPL", splVal, "#a3e635");
        readouts.getChildren().add(splChip);
        readouts.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(readouts, Priority.ALWAYS);
        readouts.setAlignment(Pos.CENTER_RIGHT);

        Circle led = new Circle(4.5, Color.web("#ff4d4d"));
        Label liveTxt = new Label("LIVE");
        liveTxt.getStyleClass().add("st-txt");
        Label rate = new Label("48 kHz / 24-bit");
        rate.getStyleClass().add("st-mono");
        clockLabel = new Label("");
        clockLabel.getStyleClass().add("st-mono");
        HBox status = new HBox(10, led, liveTxt, sep(), rate, sep(), clockLabel);
        status.setAlignment(Pos.CENTER_LEFT);

        // SPL chip visibility follows the mic-live state
        Runnable splVis = () -> { splChip.setVisible(state.isMicLive()); splChip.setManaged(state.isMicLive()); };
        state.micChan.addListener((o, a, b) -> splVis.run());
        splVis.run();

        HBox header = new HBox(24, brand, readouts, status);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private HBox readoutChip(String label, Label valueLabel, String color) {
        Label l = new Label(label);
        l.getStyleClass().add("ro-lbl");
        valueLabel.getStyleClass().add("ro-val");
        valueLabel.setStyle("-fx-text-fill:" + color + ";");
        VBox box = new VBox(3, l, valueLabel);
        HBox chip = new HBox(box);
        chip.getStyleClass().add("readout");
        chip.setMinWidth(96);
        return chip;
    }

    private Region sep() {
        Region r = new Region();
        r.setStyle("-fx-background-color:#1c2733;");
        r.setMinWidth(1);
        r.setPrefSize(1, 16);
        r.setMaxHeight(16);
        return r;
    }

    private void updateReadouts(Stats st) {
        peakVal.setText(com.fohanalyzer.engine.Engine.fmtFreq(st.peakFreq()));
        micVal.setText(String.format(java.util.Locale.US, "%.1f", st.micPeak()));
        soloVal.setText(String.format(java.util.Locale.US, "%.1f", st.soloPeak()));
        Double spl = st.micRmsDbfs() != null ? st.micRmsDbfs() + state.splOffset.get() : null;
        splVal.setText(spl != null ? String.format(java.util.Locale.US, "%.1f", spl) : "—");
    }

    // ---- live audio wiring (ports the two $effect blocks) -----------------
    private void wireLiveAudio() {
        Runnable mic = () -> updateSource(true);
        Runnable solo = () -> updateSource(false);
        state.micChan.addListener((o, a, b) -> mic.run());
        state.micChanIdx.addListener((o, a, b) -> mic.run());
        state.soloChan.addListener((o, a, b) -> solo.run());
        state.soloChanIdx.addListener((o, a, b) -> solo.run());
        mic.run();
        solo.run();
    }

    private void updateSource(boolean isMic) {
        String chan = (isMic ? state.micChan : state.soloChan).get();
        int idx = (isMic ? state.micChanIdx : state.soloChanIdx).get();
        if (chan.startsWith("live:")) {
            String id = chan.substring(5);
            Optional<AudioDevice> dev = state.audioDevices.stream()
                .filter(d -> d.id().equals(id)).findFirst();
            if (dev.isEmpty()) return;
            AudioSource src = isMic ? state.micSource : state.soloSource;
            if (src == null) {
                src = new AudioSource();
                if (isMic) state.micSource = src; else state.soloSource = src;
            }
            AudioSource source = src;
            // Open the line off the FX thread; report the channel count back.
            new Thread(() -> {
                int count = source.connect(dev.get(), idx);
                Platform.runLater(() ->
                    (isMic ? state.micChanCount : state.soloChanCount).set(count));
            }, "audio-connect").start();
        } else {
            AudioSource src = isMic ? state.micSource : state.soloSource;
            if (src != null) src.disconnect();
            if (isMic) state.micSource = null; else state.soloSource = null;
            (isMic ? state.micChanCount : state.soloChanCount).set(1);
        }
    }

    private void refreshDevices() {
        state.audioDevices.setAll(AudioInputs.enumerate());
    }

    private void startClock() {
        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(1),
            e -> clockLabel.setText(LocalTime.now().format(CLOCK))));
        tl.setCycleCount(Animation.INDEFINITE);
        tl.play();
        clockLabel.setText(LocalTime.now().format(CLOCK));
    }
}
