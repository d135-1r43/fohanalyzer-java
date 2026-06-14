# FOHanalyzer (Java / JavaFX)

A native desktop reimplementation of [FOHanalyzer](../fohanalyzer) — a real-time
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
| `audio` | Java Sound capture + JTransforms FFT (Web-Audio-equivalent), device enumeration | `lib/audioInput.js` |
| `ui` | JavaFX canvas rendering + render loop, app shell, control rail | `lib/draw.js`, `AnalyzerCanvas.svelte`, `App.svelte` |
| `ui.controls` | Toggle, Segmented, Meter, SourceCard, ChannelSelect, Logo | the corresponding `.svelte` components |

### Audio capture

Live input is captured from a `javax.sound.sampled.TargetDataLine` on a daemon thread
into a 16384-sample ring buffer. Each frame, the latest window is Blackman-windowed and
transformed with JTransforms; per-bin magnitude is normalised by the FFT size and
converted to dBFS — matching the browser's `AnalyserNode.getFloatFrequencyData`. Choose a
live device per source from its dropdown; multi-channel interfaces expose a channel
stepper. SPL metering (with calibration) appears when the measurement mic is a live input.

### Notes / differences from the web original

- Desktop window instead of a browser tab; no HTTPS/permission prompt needed.
- Audio devices are enumerated once at launch (no browser `devicechange` event).
- UI-component unit tests (Toggle/Segmented/Meter) are not ported — those controls are
  trivial and JavaFX UI testing would add disproportionate setup. Core logic is fully tested.

### Dev render probe

`PROBE=true mvn javafx:run` boots the app, snapshots the window to
`target/probe-full.png` after ~2 s, and exits — handy for headless visual checks.
