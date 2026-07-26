package com.fohanalyzer.ui;

import com.fohanalyzer.audio.AudioDsp;
import com.fohanalyzer.audio.AudioSource;
import com.fohanalyzer.dsp.Stats;
import com.fohanalyzer.engine.Engine;
import com.fohanalyzer.engine.Ring;
import com.fohanalyzer.ui.controls.Meter;
import com.fohanalyzer.ui.controls.Segmented;
import com.fohanalyzer.ui.controls.SourceCard;
import com.fohanalyzer.ui.controls.Toggle;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** The control rail (right panel). Ports the sections and handlers of App.svelte. */
public final class ControlRail extends ScrollPane {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private final AppState s;
    private final Random rng = new Random();

    private SourceCard micCard, soloCard;

    public ControlRail(AppState state) {
        this.s = state;
        getStyleClass().add("scroll-pane");
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);

        VBox rail = new VBox();
        rail.getStyleClass().add("rail");

        rail.getChildren().addAll(
            sourcesSection(),
            splSection(),
            resolutionSection(),
            responseSection(),
            overlaysSection(),
            referenceSection(),
            ringoutSection(),
            railFoot()
        );

        // Push live stats into the meters / SPL readout.
        s.stats.addListener((o, a, st) -> onStats(st));

        setContent(rail);
    }

    // ---- Sources ----------------------------------------------------------
    private VBox sourcesSection() {
        micCard = new SourceCard("Measurement Mic", "Room · capture", "#22d3ee",
            s.micOn, s.markerSource, "mic", AppState.MIC_INPUTS,
            s.micChan, s.micChanIdx, s.micChanCount, s.audioDevices);
        soloCard = new SourceCard("Solo Bus", "Console · PFL/AFL", "#f5a524",
            s.soloOn, s.markerSource, "solo", AppState.SOLO_INPUTS,
            s.soloChan, s.soloChanIdx, s.soloChanCount, s.audioDevices);
        return section("Sources", micCard, soloCard);
    }

    private void onStats(Stats st) {
        micCard.meter.setValue(st.micAvg());
        soloCard.meter.setValue(st.soloAvg());
        if (splValue != null) {
            Double spl = st.micRmsDbfs() != null ? st.micRmsDbfs() + s.splOffset.get() : null;
            splValue.setText(spl != null ? String.format(java.util.Locale.US, "%.1f", spl) : "—");
        }
    }

    // ---- SPL --------------------------------------------------------------
    private Label splValue;
    private Label splBadge;

    private VBox splSection() {
        splValue = new Label("—");
        splValue.getStyleClass().add("spl-val");
        Label unit = new Label("dB SPL");
        unit.getStyleClass().add("spl-unit");
        HBox readout = new HBox(6, splValue, unit);
        readout.setAlignment(Pos.BASELINE_LEFT);
        HBox.setHgrow(readout, Priority.ALWAYS);
        splBadge = new Label("UNCAL");
        splBadge.getStyleClass().add("spl-badge");
        HBox display = new HBox(readout, splBadge);
        display.getStyleClass().add("spl-display");
        display.setAlignment(Pos.CENTER_LEFT);

        TextField ref = new TextField("94");
        ref.getStyleClass().add("spl-input");
        ref.setPrefWidth(68);
        ref.textProperty().addListener((o, a, b) -> {
            try { s.calRefSpl.set(Double.parseDouble(b)); } catch (NumberFormatException ignored) {}
        });
        Label refLbl = new Label("dB SPL ref");
        refLbl.getStyleClass().add("hint");
        HBox.setHgrow(refLbl, Priority.ALWAYS);
        Button calBtn = new Button("Calibrate");
        calBtn.getStyleClass().add("mini-btn");
        calBtn.setOnAction(e -> calibrate());
        HBox calRow = new HBox(8, ref, refLbl, calBtn);
        calRow.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label("Play a known reference level, enter it, then tap Calibrate.");
        hint.getStyleClass().add("hint");

        VBox sec = section("SPL Meter", display, calRow, hint);

        // visible only when the mic source is live
        Runnable vis = () -> {
            boolean live = s.isMicLive();
            sec.setVisible(live);
            sec.setManaged(live);
        };
        s.micChan.addListener((o, a, b) -> vis.run());
        s.splOffset.addListener((o, a, b) -> updateSplBadge());
        vis.run();
        updateSplBadge();
        return sec;
    }

    private void updateSplBadge() {
        boolean cal = s.splOffset.get() != 0;
        splBadge.setText(cal ? "CAL" : "UNCAL");
        splBadge.getStyleClass().remove("spl-badge-cal");
        if (cal) splBadge.getStyleClass().add("spl-badge-cal");
    }

    private void calibrate() {
        Double rms = s.stats.get().micRmsDbfs();
        if (rms != null) s.splOffset.set(s.calRefSpl.get() - rms);
    }

    // ---- Resolution -------------------------------------------------------
    private VBox resolutionSection() {
        Map<Integer, String> opts = new LinkedHashMap<>();
        opts.put(1, "1/1"); opts.put(3, "1/3"); opts.put(6, "1/6");
        opts.put(12, "1/12"); opts.put(24, "1/24");
        Segmented seg = new Segmented(s.frac, opts);
        Label hint = new Label("Octave-band averaging width");
        hint.getStyleClass().add("hint");
        return section("Resolution", seg, hint);
    }

    // ---- Response ---------------------------------------------------------
    private VBox responseSection() {
        Label lbl = new Label("Smoothing");
        lbl.getStyleClass().add("row-lbl");
        Label val = new Label();
        val.getStyleClass().add("row-val");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(lbl, spacer, val);

        Slider slider = new Slider(0, 0.95, s.smoothing.get());
        slider.valueProperty().bindBidirectional(s.smoothing);
        Runnable updateVal = () -> val.setText(Math.round(s.smoothing.get() * 100) + "%");
        s.smoothing.addListener((o, a, b) -> updateVal.run());
        updateVal.run();

        Label avgLbl = new Label("Averaging");
        avgLbl.getStyleClass().add("row-lbl");
        Map<Integer, String> opts = new LinkedHashMap<>();
        opts.put(1, "Off"); opts.put(2, "2"); opts.put(4, "4"); opts.put(8, "8"); opts.put(16, "16");
        Segmented avg = new Segmented(s.avgN, opts);

        return section("Response", row, slider, avgLbl, avg);
    }

    // ---- Overlays ---------------------------------------------------------
    private VBox overlaysSection() {
        // peak hold row with conditional reset button
        Label phLbl = new Label("Peak hold");
        phLbl.getStyleClass().add("ctl-lbl");
        Region s1 = new Region();
        HBox.setHgrow(s1, Priority.ALWAYS);
        Button reset = new Button("Reset");
        reset.getStyleClass().add("mini-btn");
        reset.setOnAction(e -> s.holdReset.set(s.holdReset.get() + 1));
        Toggle phToggle = new Toggle(s.peakHold, "#a3e635");
        HBox phRight = new HBox(9, reset, phToggle);
        phRight.setAlignment(Pos.CENTER_RIGHT);
        HBox phRow = new HBox(phLbl, s1, phRight);
        phRow.setAlignment(Pos.CENTER_LEFT);
        Runnable phVis = () -> { reset.setVisible(s.peakHold.get()); reset.setManaged(s.peakHold.get()); };
        s.peakHold.addListener((o, a, b) -> phVis.run());
        phVis.run();

        HBox markerRow = ctlRow("Peak markers", new Toggle(s.markers, "#a3e635"));
        HBox tfRow = ctlRow("Transfer fn  (Mic−Solo)", new Toggle(s.showTransfer, "#b794f6"));

        return section("Overlays", phRow, markerRow, tfRow);
    }

    private HBox ctlRow(String label, javafx.scene.Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("ctl-lbl");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox row = new HBox(l, sp, control);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ---- Reference capture ------------------------------------------------
    private VBox referenceSection() {
        VBox body = new VBox(9);
        Label hint = new Label("Dashed ghost of both traces — A/B before vs after EQ.");
        hint.getStyleClass().add("hint");
        VBox sec = section("Reference capture", body, hint);

        Runnable rebuild = () -> {
            body.getChildren().clear();
            Draw.Reference ref = s.reference.get();
            if (ref == null) {
                Button capture = new Button("⊕  Capture reference");
                capture.getStyleClass().addAll("btn", "btn-capture");
                capture.setMaxWidth(Double.MAX_VALUE);
                capture.setOnAction(e -> doCapture());
                body.getChildren().add(capture);
            } else {
                Label info = new Label("Snapshot " + ref.time());
                info.getStyleClass().add("row-lbl");
                Button hideShow = miniBtn(s.showReference.get() ? "Hide" : "Show",
                    e -> s.showReference.set(!s.showReference.get()));
                Button recap = miniBtn("Re-cap", e -> doCapture());
                Button clear = miniBtn("Clear", e -> s.reference.set(null));
                HBox actions = new HBox(7, hideShow, recap, clear);
                body.getChildren().addAll(info, actions);
            }
        };
        s.reference.addListener((o, a, b) -> rebuild.run());
        s.showReference.addListener((o, a, b) -> rebuild.run());
        rebuild.run();
        return sec;
    }

    private void doCapture() { s.captureNonce.set(s.captureNonce.get() + 1); }

    // ---- Ring-out assist --------------------------------------------------
    private Label detectHint;

    private VBox ringoutSection() {
        Label copy = new Label("Simulate a monitor feedback ring to practise hunting the offending band.");
        copy.getStyleClass().add("hint");
        detectHint = new Label();
        detectHint.getStyleClass().add("hint");
        detectHint.setWrapText(true);
        VBox controls = new VBox(10);
        VBox log = new VBox(6);
        VBox sec = section("Ring-out assist", copy, controls, log);

        Runnable rebuildControls = () -> {
            controls.getChildren().clear();
            Ring r = s.ring.get();
            if (r.active()) {
                Label ring = new Label("Ringing @ " + Engine.fmtFreq(r.fc()) + " · " + Engine.noteName(r.fc()));
                ring.getStyleClass().add("fb-freq");
                Button clear = new Button("Clear feedback");
                clear.getStyleClass().addAll("btn", "btn-clear");
                clear.setMaxWidth(Double.MAX_VALUE);
                clear.setOnAction(e -> injectRing());
                controls.getChildren().addAll(ring, clear);
            } else {
                Button inject = new Button("⚠  Inject feedback");
                inject.getStyleClass().addAll("btn", "btn-ring");
                inject.setMaxWidth(Double.MAX_VALUE);
                inject.setOnAction(e -> injectRing());
                controls.getChildren().add(inject);
            }
            // Real detection only makes sense against a live mic, not the simulation.
            if (s.isMicLive()) {
                Button detect = new Button("⌖  Detect ring from mic");
                detect.getStyleClass().addAll("btn", "btn-detect");
                detect.setMaxWidth(Double.MAX_VALUE);
                detect.setOnAction(e -> detectRing());
                controls.getChildren().addAll(detect, detectHint);
            } else {
                detectHint.setText("");
            }
        };
        s.micChan.addListener((o, a, b) -> rebuildControls.run());

        Runnable rebuildLog = () -> {
            log.getChildren().clear();
            if (s.feedbackLog.isEmpty()) return;
            Label head = new Label("Feedback log");
            head.getStyleClass().add("sec-title");
            Button clearAll = miniBtn("Clear all", e -> { s.feedbackLog.clear(); s.locateFreq.set(null); });
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            HBox header = new HBox(head, sp, clearAll);
            header.setAlignment(Pos.CENTER_LEFT);
            log.getChildren().add(header);
            for (FeedbackEntry en : s.feedbackLog) log.getChildren().add(fbRow(en));
        };

        s.ring.addListener((o, a, b) -> rebuildControls.run());
        s.feedbackLog.addListener((javafx.collections.ListChangeListener<FeedbackEntry>) c -> rebuildLog.run());
        s.locateFreq.addListener((o, a, b) -> rebuildLog.run());
        rebuildControls.run();
        rebuildLog.run();
        return sec;
    }

    private HBox fbRow(FeedbackEntry en) {
        Label freq = new Label(Engine.fmtFreq(en.freq()));
        freq.getStyleClass().add("fb-freq");
        Label note = new Label(en.note());
        note.getStyleClass().add("fb-note");
        VBox left = new VBox(2, freq, note);
        left.setMinWidth(64);
        Label eq = new Label("GEQ " + Engine.fmtShort(en.band()) + " · −" + en.cut() + " dB");
        eq.getStyleClass().add("fb-eq");
        eq.setMaxWidth(Double.MAX_VALUE);
        eq.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(eq, Priority.ALWAYS);
        Button x = new Button("×");
        x.getStyleClass().add("mini-btn");
        x.setOnAction(e -> removeFb(en));
        HBox row = new HBox(8, left, eq, x);
        row.getStyleClass().add("fb-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-border-width:0 0 0 2; -fx-border-color:#ff5b60;");
        boolean on = s.locateFreq.get() != null && s.locateFreq.get() == en.freq();
        if (on) row.getStyleClass().add("on");
        row.setOnMouseClicked(e -> toggleLocate(en.freq()));
        return row;
    }

    private void injectRing() {
        if (s.ring.get().active()) { s.ring.set(Ring.INACTIVE); return; }
        double fc = 1600 + rng.nextDouble() * 2600;
        s.ring.set(new Ring(true, fc, System.nanoTime() / 1e9));
        logFeedback(fc, 4 + (int) Math.round(rng.nextDouble() * 3));
    }

    /**
     * Ask TarsosDSP's YIN what the live mic is ringing at and log that, instead of the
     * band centre the peak markers would round to.
     */
    private void detectRing() {
        AudioSource mic = s.micSource;
        AudioDsp.Pitch p = mic != null ? mic.readPitch() : null;
        if (p == null) {
            detectHint.setText("No steady tone on the mic right now.");
            return;
        }
        detectHint.setText(String.format(java.util.Locale.US,
            "YIN · %s · %.0f%% confidence", Engine.fmtFreq(p.hz()), p.probability() * 100));
        logFeedback(p.hz(), suggestedCut());
    }

    /** Log a ring at {@code fc}, notched on the nearest ISO 1/3-octave GEQ band. */
    private void logFeedback(double fc, int cut) {
        double[] iso = Engine.bandCenters(3);
        double band = iso[0];
        for (double b : iso) {
            if (Math.abs(log2(b / fc)) < Math.abs(log2(band / fc))) band = b;
        }
        FeedbackEntry entry = new FeedbackEntry(System.nanoTime(), fc, Engine.noteName(fc), band, cut,
            LocalTime.now().format(HHMM));
        s.feedbackLog.add(0, entry);
        while (s.feedbackLog.size() > 6) s.feedbackLog.remove(s.feedbackLog.size() - 1);
        s.locateFreq.set(fc);
    }

    /** How far the loudest band stands above the mic average, clamped to a sane cut. */
    private int suggestedCut() {
        Stats st = s.stats.get();
        double prominence = st != null ? st.micPeak() - st.micAvg() : 6;
        return (int) Math.round(Math.max(3, Math.min(12, prominence)));
    }

    private void removeFb(FeedbackEntry en) {
        if (s.locateFreq.get() != null && s.locateFreq.get() == en.freq()) s.locateFreq.set(null);
        s.feedbackLog.remove(en);
    }

    private void toggleLocate(double freq) {
        Double cur = s.locateFreq.get();
        s.locateFreq.set(cur != null && cur == freq ? null : freq);
    }

    private static double log2(double x) { return Math.log(x) / Math.log(2); }

    // ---- helpers ----------------------------------------------------------
    private VBox section(String title, javafx.scene.Node... children) {
        Label t = new Label(title.toUpperCase());
        t.getStyleClass().add("sec-title");
        VBox sec = new VBox(8, t);
        sec.getChildren().addAll(children);
        sec.getStyleClass().add("section");
        return sec;
    }

    private VBox railFoot() {
        Label f = new Label("FOHanalyzer 2.3.1 · " + s.signalStatus());
        f.getStyleClass().add("rail-foot");
        s.micChan.addListener((o, a, b) -> f.setText("FOHanalyzer 2.3.1 · " + s.signalStatus()));
        s.soloChan.addListener((o, a, b) -> f.setText("FOHanalyzer 2.3.1 · " + s.signalStatus()));
        VBox box = new VBox(f);
        box.setAlignment(Pos.CENTER);
        VBox.setVgrow(box, Priority.ALWAYS);
        box.setStyle("-fx-padding:16 0 14 0;");
        return box;
    }

    private Button miniBtn(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button b = new Button(text);
        b.getStyleClass().add("mini-btn");
        b.setOnAction(action);
        return b;
    }
}
