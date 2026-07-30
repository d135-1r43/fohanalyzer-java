package com.fohanalyzer.ui;

import com.fohanalyzer.ui.controls.ChannelSelect;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Locale;

/**
 * The set-once settings: which device feeds each source, and the SPL
 * calibration.
 *
 * <p>
 * Both are decided when the rig is patched and then left alone, so they sit
 * here instead of taking up the rail that has to stay readable during a show.
 * Everything binds straight to {@link AppState}, which {@link Settings} already
 * persists — closing the window is not a commit step, and there is nothing to
 * cancel.
 */
public final class PreferencesWindow
{

	private final AppState state;
	private Stage stage;

	public PreferencesWindow(AppState state)
	{
		this.state = state;
	}

	/** Opens the window, or brings it forward if it is already up. */
	public void show(Window owner)
	{
		if (stage != null)
		{
			stage.toFront();
			stage.requestFocus();
			return;
		}

		Scene scene = new Scene(buildRoot(), 420, 520, Color.web("#070a0f"));
		scene.getStylesheets().add(
			MainApp.class.getResource("/com/fohanalyzer/theme.css").toExternalForm());

		stage = new Stage();
		stage.setTitle("FOHanalyzer · Preferences");
		stage.initOwner(owner);
		stage.initModality(Modality.NONE);
		stage.setScene(scene);
		stage.setOnHidden(e -> stage = null);
		stage.show();
	}

	/**
	 * The window's contents, separate from staging them, so the layout can be
	 * built and measured without a {@link Stage}.
	 */
	VBox buildRoot()
	{
		VBox body = new VBox(18, sourcesSection(), splSection());
		body.getStyleClass().add("prefs-body");
		VBox.setVgrow(body, Priority.ALWAYS);

		Button close = new Button("Done");
		close.getStyleClass().addAll("btn", "mini-btn");
		close.setOnAction(e -> {
			if (stage != null) stage.close();
		});
		HBox foot = new HBox(close);
		foot.setAlignment(Pos.CENTER_RIGHT);
		foot.getStyleClass().add("prefs-foot");

		VBox root = new VBox(body, foot);
		root.getStyleClass().add("prefs-root");
		return root;
	}

	// ---- Sources ----------------------------------------------------------
	private VBox sourcesSection()
	{
		return section("Sources",
			sourceRow("Measurement Mic", "#22d3ee", AppState.MIC_INPUTS,
				state.micChan, state.micChanIdx, state.micChanCount),
			sourceRow("Solo Bus", "#f5a524", AppState.SOLO_INPUTS,
				state.soloChan, state.soloChanIdx, state.soloChanCount),
			hint("Live inputs are listed first, simulated presets below. A"
				+ " multi-channel interface adds a channel stepper."));
	}

	private VBox sourceRow(String name, String color, java.util.List<InputPreset> options,
		javafx.beans.property.StringProperty chan,
		javafx.beans.property.IntegerProperty chanIdx,
		javafx.beans.property.IntegerProperty chanCount)
	{
		Label lbl = new Label(name);
		lbl.getStyleClass().add("ctl-lbl");
		lbl.setStyle("-fx-text-fill:" + color + ";");
		ChannelSelect select = new ChannelSelect(chan, options, state.audioDevices, color,
			chanIdx, chanCount);
		return new VBox(7, lbl, select);
	}

	// ---- SPL calibration --------------------------------------------------
	private VBox splSection()
	{
		TextField ref = new TextField(fmtRef(state.calRefSpl.get()));
		ref.getStyleClass().add("spl-input");
		ref.setPrefWidth(68);
		ref.textProperty().addListener((o, a, b) -> {
			try
			{
				state.calRefSpl.set(Double.parseDouble(b));
			}
			catch (NumberFormatException ignored)
			{
			}
		});
		// Keep the field in step when the value arrives from somewhere else — a
		// restored setting or a reset — without fighting the user's own typing.
		state.calRefSpl.addListener((o, a, b) -> {
			String text = fmtRef(b.doubleValue());
			if (!text.equals(ref.getText().trim())) ref.setText(text);
		});

		Label refLbl = new Label("dB SPL ref");
		refLbl.getStyleClass().add("hint");
		HBox.setHgrow(refLbl, Priority.ALWAYS);
		Button calBtn = new Button("Calibrate");
		calBtn.getStyleClass().add("mini-btn");
		calBtn.setOnAction(e -> calibrate());
		HBox calRow = new HBox(8, ref, refLbl, calBtn);
		calRow.setAlignment(Pos.CENTER_LEFT);

		Label status = new Label();
		status.getStyleClass().add("hint");
		Runnable refreshStatus = () -> {
			double off = state.splOffset.get();
			status.setText(off == 0
				? "Not calibrated — the readout shows dBFS + 0."
				: String.format(Locale.US, "Calibrated · offset %+.1f dB", off));
		};
		state.splOffset.addListener((o, a, b) -> refreshStatus.run());
		refreshStatus.run();

		VBox sec = section("SPL meter", calRow,
			hint("Play a known reference level, enter it, then tap Calibrate."), status);

		// Only a live mic produces the RMS the calibration is computed from.
		Runnable vis = () -> {
			boolean live = state.isMicLive();
			sec.setDisable(!live);
			sec.setOpacity(live ? 1.0 : 0.45);
		};
		state.micChan.addListener((o, a, b) -> vis.run());
		vis.run();
		return sec;
	}

	private void calibrate()
	{
		Double rms = state.stats.get().micRmsDbfs();
		if (rms != null) state.splOffset.set(state.calRefSpl.get() - rms);
	}

	/**
	 * {@code 94.0 -> "94"}, so a restored whole number does not read as a
	 * measurement.
	 */
	private static String fmtRef(double v)
	{
		return v == Math.rint(v)
			? Long.toString(Math.round(v))
			: String.format(Locale.US, "%.1f", v);
	}

	// ---- shell ------------------------------------------------------------
	private static VBox section(String title, javafx.scene.Node... children)
	{
		Label t = new Label(title.toUpperCase());
		t.getStyleClass().add("sec-title");
		VBox sec = new VBox(9, t);
		sec.getChildren().addAll(children);
		sec.getStyleClass().add("section");
		return sec;
	}

	private static Label hint(String text)
	{
		Label l = new Label(text);
		l.getStyleClass().add("hint");
		l.setWrapText(true);
		l.setMaxWidth(Double.MAX_VALUE);
		return l;
	}
}
