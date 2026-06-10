package com.jenerator.worker.model;

public record CaptionCue(
        double startSeconds,
        double endSeconds,
        String text
) {
}
