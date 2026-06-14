package com.fohanalyzer.ui;

import com.fohanalyzer.audio.AudioDevice;
import com.fohanalyzer.audio.AudioSource;
import com.fohanalyzer.dsp.Stats;
import com.fohanalyzer.engine.Ring;
import com.fohanalyzer.engine.Voice;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

/**
 * All shared, observable application state — the Java equivalent of the {@code $state}
 * fields in App.svelte. The control rail mutates these; the analyzer view reads them.
 */
public final class AppState {

    public static final List<InputPreset> MIC_INPUTS = List.of(
        new InputPreset("ch32", "Ch 32 · Meas Mic", 0, 0),
        new InputPreset("ch31", "Ch 31 · Meas Mic", -1.5, 0.3),
        new InputPreset("rta",  "RTA Input",        1, -0.4),
        new InputPreset("usb",  "USB Rtn 1",        -0.5, 0.6),
        new InputPreset("aes",  "AES50-A 24",       0.5, -0.2)
    );

    public static final List<InputPreset> SOLO_INPUTS = List.of(
        new InputPreset("pfl",  "Solo Bus · PFL",   0, 0),
        new InputPreset("main", "Main L/R",          1.5, -0.4),
        new InputPreset("mono", "Mono / Sub",        1, -1.5),
        new InputPreset("mtx1", "Matrix 1-2",        -1, -0.2),
        new InputPreset("aux4", "Aux 4 · Mon Wedge", -2, 0.5)
    );

    // Visibility & analysis controls
    public final BooleanProperty micOn = new SimpleBooleanProperty(true);
    public final BooleanProperty soloOn = new SimpleBooleanProperty(true);
    public final IntegerProperty frac = new SimpleIntegerProperty(12);
    public final DoubleProperty smoothing = new SimpleDoubleProperty(0.62);
    public final IntegerProperty avgN = new SimpleIntegerProperty(4);
    public final BooleanProperty peakHold = new SimpleBooleanProperty(false);
    public final IntegerProperty holdReset = new SimpleIntegerProperty(0);
    public final BooleanProperty markers = new SimpleBooleanProperty(true);
    public final StringProperty markerSource = new SimpleStringProperty("mic");
    public final BooleanProperty showTransfer = new SimpleBooleanProperty(false);

    // Source / channel selection ("live:<deviceId>" or a preset id)
    public final StringProperty micChan = new SimpleStringProperty("ch32");
    public final StringProperty soloChan = new SimpleStringProperty("pfl");
    public final IntegerProperty micChanIdx = new SimpleIntegerProperty(0);
    public final IntegerProperty soloChanIdx = new SimpleIntegerProperty(0);
    public final IntegerProperty micChanCount = new SimpleIntegerProperty(1);
    public final IntegerProperty soloChanCount = new SimpleIntegerProperty(1);

    // Ring-out / reference / overlays
    public final ObjectProperty<Ring> ring = new SimpleObjectProperty<>(Ring.INACTIVE);
    public final IntegerProperty captureNonce = new SimpleIntegerProperty(0);
    public final ObjectProperty<Draw.Reference> reference = new SimpleObjectProperty<>(null);
    public final BooleanProperty showReference = new SimpleBooleanProperty(true);
    public final ObservableList<FeedbackEntry> feedbackLog = FXCollections.observableArrayList();
    public final ObjectProperty<Double> locateFreq = new SimpleObjectProperty<>(null);

    // Readouts / SPL
    public final ObjectProperty<Stats> stats =
        new SimpleObjectProperty<>(new Stats(0, -90, -90, -90, -90, null));
    public final DoubleProperty splOffset = new SimpleDoubleProperty(0);
    public final DoubleProperty calRefSpl = new SimpleDoubleProperty(94);

    public final ObservableList<AudioDevice> audioDevices = FXCollections.observableArrayList();

    // Live capture sources (plain, non-observable)
    public AudioSource micSource;
    public AudioSource soloSource;

    public boolean isMicLive() { return micChan.get().startsWith("live:"); }
    public boolean isSoloLive() { return soloChan.get().startsWith("live:"); }

    public Voice micVoice() {
        return MIC_INPUTS.stream().filter(p -> p.id().equals(micChan.get()))
            .findFirst().map(InputPreset::voice).orElse(Voice.NEUTRAL);
    }

    public Voice soloVoice() {
        return SOLO_INPUTS.stream().filter(p -> p.id().equals(soloChan.get()))
            .findFirst().map(InputPreset::voice).orElse(Voice.NEUTRAL);
    }

    public String signalStatus() {
        boolean m = isMicLive(), s = isSoloLive();
        if (m && s) return "live signal";
        if (m) return "mic live · solo simulated";
        if (s) return "mic simulated · solo live";
        return "simulated signal";
    }
}
