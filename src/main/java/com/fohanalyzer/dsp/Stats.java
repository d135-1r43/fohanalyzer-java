package com.fohanalyzer.dsp;

/**
 * Snapshot of analyzer readouts. {@code micRmsDbfs} is {@code null} when no live
 * mic is connected (SPL is only meaningful for a live source).
 */
public record Stats(
    double peakFreq,
    double micPeak,
    double micAvg,
    double soloAvg,
    double soloPeak,
    Double micRmsDbfs
) {
    public Stats withMicRms(Double rms) {
        return new Stats(peakFreq, micPeak, micAvg, soloAvg, soloPeak, rms);
    }
}
