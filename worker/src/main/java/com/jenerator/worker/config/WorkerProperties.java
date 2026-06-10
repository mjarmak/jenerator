package com.jenerator.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "jenerator.worker")
public record WorkerProperties(
        boolean enabled,
        String name,
        String controlPlaneUrl,
        String token,
        String pairingCode,
        long pollDelayMs,
        Path workspacePath,
        String ffmpegPath,
        String qwenHealthUrl
) {
    public WorkerProperties {
        name = name == null || name.isBlank() ? "local-worker" : name;
        controlPlaneUrl = controlPlaneUrl == null || controlPlaneUrl.isBlank() ? "http://localhost:8080" : controlPlaneUrl;
        pollDelayMs = pollDelayMs <= 0 ? 15_000 : pollDelayMs;
        workspacePath = workspacePath == null ? Path.of("data/worker") : workspacePath;
        ffmpegPath = ffmpegPath == null || ffmpegPath.isBlank() ? "ffmpeg" : ffmpegPath;
    }
}
