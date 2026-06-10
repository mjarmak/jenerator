package com.jenerator.common.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record WorkerHeartbeatRequest(
        @NotBlank String workerName,
        boolean ffmpegAvailable,
        boolean qwenAvailable,
        boolean youtubeConfigured,
        Map<String, String> details
) {
    public WorkerHeartbeatRequest {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
