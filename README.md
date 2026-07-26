# FOHanalyzer (Java / JavaFX)

A native desktop reimplementation of FOHanalyzer — a real-time
dual-channel RTA (Real-Time Analyzer) for Front-of-House sound engineers. It overlays
a **measurement-mic** trace (cyan) against a console **solo-bus** trace (amber) so you
can compare room response to source signal, hunt feedback, verify EQ, and meter SPL.

This is a faithful port of the original Svelte/Web-Audio app to **Java 21 + JavaFX**.
The frequency math, signal processing, simulation engine, and on-screen rendering all
match the original 1:1.

## Install

Grab the `.dmg` and drag FOHanalyzer to Applications — it ships its own Java runtime, so
**no JDK or Maven is needed** on the machine that runs it.

macOS will ask for microphone permission the first time you select a live input; that is the
measurement mic and console feeds, and without it the analyser has nothing to read.

> The bundle is ad-hoc signed, not notarised, so Gatekeeper on another Mac will refuse the
> first launch: right-click the app and choose **Open** to allow it. A Developer ID
> signature and notarisation would remove that step.

### Building the bundle yourself

```bash
./scripts/package-mac.sh            # target/dist/FOHanalyzer-<version>.dmg
./scripts/package-mac.sh app-image  # just the .app — faster when iterating
```

Needs a JDK with `jpackage` (21+; built on Temurin 25) and Maven. The script collects the
runtime jars, renders the icon from the in-app logo via
`com.fohanalyzer.dev.IconRenderer`, and runs `jpackage`. See
[Packaging notes](#packaging-notes) for why it looks the way it does.

## Requirements (development)

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

## Code style

The style is the Eclipse formatter profile in [`formatter/java.xml`](formatter/java.xml),
shared with the `nuusroom` project so both use one house style: tabs at width 4, braces on
their own line, and comments wrapped at 80 columns.

```bash
mvn formatter:format   # apply it
```

It is enforced, not suggested: `formatter-maven-plugin`'s `validate` goal runs in the
`validate` phase, so **any build fails if a source file is not formatted** — including
`mvn test` and `mvn package`.

One quirk worth knowing when writing comments: the profile sets `join_line_comments=false`,
so the formatter *splits* an over-long `//` line but never rejoins the remainder. A comment
wrapped for a wider margin ends up ragged, and an over-long trailing comment turns into a
deeply indented staircase. Keep `//` lines inside the 80-column budget (counting the tab
indent), and put a long comment on its own line above the statement rather than trailing it.

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

### Packaging notes

The app is deliberately **not** modular. `com.fohanalyzer.Main` is a plain launcher that
calls `Application.launch` on `MainApp`, which lets JavaFX run from the classpath. That
sidesteps the usual `jlink` dead end: TarsosDSP is an automatic module, and `jlink` refuses
to link those, so a modular build would have needed a synthesised `module-info` for a
third-party jar.

Because the app is non-modular, `jpackage` cannot infer what the runtime needs, so the
modules are listed explicitly. Two are easy to miss: **`java.desktop`** carries
`javax.sound.sampled`, without which there is no audio capture at all, and **`java.prefs`**
backs the saved settings. The resulting image is 7 modules, ~79 MB, giving an 88 MB `.app`
that compresses to a ~37 MB `.dmg`.

Two macOS specifics the script handles:

- **Microphone usage description.** macOS 10.14+ denies mic access unless `Info.plist` says
  why the app wants it. `jpackage` on JDK 25 writes a generic sentence and offers no flag for
  arbitrary keys, so the script rewrites the key with the real reason — that text is the
  prompt the user has to agree to.
- **Re-signing.** Editing `Info.plist` invalidates the ad-hoc signature `jpackage` applies,
  and arm64 macOS will not launch an unsigned bundle, so the script re-signs ad-hoc
  afterwards.

The icon is rendered from the same `Logo` vector used in the header, at every size
`iconutil` wants, rather than downscaled from one bitmap — the bars are thin and resampling
turns the 16px variant to mush.

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
