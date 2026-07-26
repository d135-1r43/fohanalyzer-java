# FOHanalyzer (Java / JavaFX)

A native desktop reimplementation of FOHanalyzer — a real-time
dual-channel RTA (Real-Time Analyzer) for Front-of-House sound engineers. It overlays
a **measurement-mic** trace (cyan) against a console **solo-bus** trace (amber) so you
can compare room response to source signal, hunt feedback, verify EQ, and meter SPL.

This is a faithful port of the original Svelte/Web-Audio app to **Java 21 + JavaFX**.
The frequency math, signal processing, simulation engine, and on-screen rendering all
match the original 1:1.

## Requirements

- JDK 21+ (built/tested on Temurin 25)
- Maven 3.9+

## Run

```bash
mvn javafx:run
```

## Test

```bash
mvn test
```

The JUnit suites (`EngineTest`, `SignalStateTest`, `AudioDspTest`) port the original
Vitest tests and verify parity of the band math, note naming, formatting, simulation,
averaging/smoothing/peak-hold/voicing, and the spectral (FFT/band/RMS) logic.

## Architecture

| Package | Responsibility | Ported from |
|---|---|---|
| `engine` | Frequency math, note names, formatting, signal simulation | `lib/engine.js` |
| `dsp` | Per-band averaging, smoothing, peak hold, stats | `lib/signalState.js` |
| `audio` | Java Sound capture + TarsosDSP FFT (Web-Audio-equivalent) and pitch detection, device enumeration | `lib/audioInput.js` |
| `ui` | JavaFX canvas rendering + render loop, app shell, control rail | `lib/draw.js`, `AnalyzerCanvas.svelte`, `App.svelte` |
| `ui.controls` | Toggle, Segmented, Meter, SourceCard, ChannelSelect, Logo | the corresponding `.svelte` components |

### Audio capture

Live input is captured from a `javax.sound.sampled.TargetDataLine` on a daemon thread
into a 16384-sample ring buffer. Each frame, the latest window is Blackman-windowed and
transformed with [TarsosDSP](https://github.com/JorenSix/TarsosDSP)
(`be.tarsos.dsp.util.fft.FFT` + `BlackmanWindow`); per-bin magnitude is normalised by the
FFT size and converted to dBFS — matching the browser's
`AnalyserNode.getFloatFrequencyData`. Choose a live device per source from its dropdown;
multi-channel interfaces expose a channel stepper. SPL metering (with calibration) appears
when the measurement mic is a live input.

TarsosDSP is not on Maven Central; the POM adds the author's repository
(`https://mvn.0110.be/releases`) and pulls `be.tarsos.dsp:core`, which has no transitive
dependencies. **It is GPL-3.0**, which is stricter than the rest of this project's
dependency set — relevant if the app is ever distributed.

Only the `core` module is used. The `jvm` module's `AudioDispatcher` is deliberately not
used for capture: it consumes a mono stream, which would give up the per-channel selection
this app needs on multi-channel interfaces (Scarlett 18i20, Behringer Wing, …).

### Saved settings

SPL calibration, the selected inputs and channels, and the analysis/view options persist
between launches via `java.util.prefs.Preferences` — no config file of our own, no
dependency. Calibration is flushed to disk the moment it changes, since it is the expensive
thing to redo; the rest ride the periodic sync plus a flush on window close.

Stored values are validated on the way in: a resolution or averaging setting the segmented
controls cannot display, or a smoothing value outside the slider's range, falls back to the
default rather than putting the UI in an unrepresentable state.

A saved `live:<device>` input is only restored if that interface is actually present —
moving between rigs is the normal case, so a missing device quietly leaves the simulated
preset selected instead of pointing at a line that cannot be opened. A channel index saved
against an 18-in interface is clamped when it reopens on a 2-in one. *Reset saved settings*
at the foot of the rail clears everything back to defaults.

### Ring-out assist

With a **live** measurement mic, *Detect ring from mic* runs TarsosDSP's `FastYin` over
the newest audio and logs the actual ringing fundamental, rather than the band centre the
peak markers round to. Four consecutive 2048-sample blocks are estimated independently and
the median is logged, so one glitched block cannot shift the frequency you go on to notch.
The suggested cut comes from how far the loudest band stands above the mic average. With a
simulated mic, only the original *Inject feedback* practice mode is offered.

### Typography

The web original styles everything with **IBM Plex Mono** (readouts, axis labels, badges)
against **Saira** for UI text. A browser fetches those from Google Fonts; a desktop app
cannot, so both families ship in `resources/com/fohanalyzer/fonts` (OFL-1.1, licences
included) and `Fonts.install()` registers them with JavaFX before the stylesheet is applied.
Without that step the app silently fell back to Helvetica Neue and Menlo.

Saira is only published as a variable font, and JavaFX 21 cannot select an axis instance, so
the UI sans is **Barlow** — the nearest static-face equivalent of Saira's squarish technical
grotesque. Saira is kept next in the CSS stack for anyone who has it installed locally.
Regular and Bold are bundled per family; the SemiBold (600) faces the web version uses are
available upstream if the headings want a lighter weight.

### Notes / differences from the web original

- Desktop window instead of a browser tab; no HTTPS/permission prompt needed.
- Audio devices are enumerated once at launch (no browser `devicechange` event).
- UI text is Barlow rather than Saira, for the variable-font reason above.
- The Blackman window comes from TarsosDSP, which uses the symmetric definition
  (`cos(2πi/(N−1))`) where Web Audio uses the periodic one (`cos(2πi/N)`). At the 16384-point
  window in use the two differ by ~1 part in 16k — far below the display resolution — and the
  spectral tests hold. The FFT itself is single- rather than double-precision for the same reason.
- Broadband RMS stays hand-rolled: TarsosDSP's `SilenceDetector.soundPressureLevel` divides
  energy by the buffer length instead of its square root, so it would not agree with the
  browser RMS the SPL readout is calibrated against.
- UI-component unit tests (Toggle/Segmented/Meter) are not ported — those controls are
  trivial and JavaFX UI testing would add disproportionate setup. Core logic is fully tested.

### Dev aids

**CSS hot reload.** `FOH_DEV=true mvn javafx:run` watches
`src/main/resources/com/fohanalyzer/theme.css` and re-applies it to the running window on
save, so padding/colour/font work does not need a restart. Off by default — no watcher
thread exists in a normal run.

It reloads by copying the stylesheet to a fresh temp file and pointing the scene at that.
JavaFX keys its parsed-stylesheet cache on the URL string, so re-adding the same path — even
after `clear()` — can hand back the stale parse; a filename it has never seen sidesteps the
cache instead of relying on undocumented invalidation.

**Render probe.** `PROBE=true mvn javafx:run` boots the app, snapshots the window to
`target/probe-full.png` after ~2 s, and exits — handy for headless visual checks. Pass a
number for a longer delay (`PROBE=20`), e.g. to snapshot *after* a hot reload.

## Licence

**GPL-3.0-or-later** — see [LICENSE](LICENSE). The choice is dictated by
[TarsosDSP](https://github.com/JorenSix/TarsosDSP), which is GPL-3.0: linking against it
means anything distributed here has to be GPL-compatible too.

The bundled fonts are **not** covered by that licence and keep their own:
IBM Plex Mono (© IBM Corp.) and Barlow (© The Barlow Project Authors) are both
[SIL Open Font License 1.1](https://openfontlicense.org), with the licence texts included
in `src/main/resources/com/fohanalyzer/fonts`. The OFL permits bundling them with an
application regardless of that application's own licence.
