package com.jenerator.worker.model;

public record RenderPreset(
        int width,
        int height,
        int durationSeconds,
        int framesPerSecond,
        int captionFontSize,
        String pacing,
        String ffmpegScaleExpression
) {
}
