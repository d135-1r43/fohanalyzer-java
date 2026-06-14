package com.fohanalyzer.ui;

/** One logged ring-out event shown in the feedback log. */
public record FeedbackEntry(long id, double freq, String note, double band, int cut, String time) {}
