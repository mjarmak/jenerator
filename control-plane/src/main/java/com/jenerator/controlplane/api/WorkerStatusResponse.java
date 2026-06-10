package com.jenerator.controlplane.api;

import java.time.Instant;
import java.util.Map;

public record WorkerStatusResponse(
        String id,
        String name,
        Instant registeredAt,
        Instant lastSeenAt,
        boolean online,
        boolean ffmpegAvailable,
        boolean qwenAvailable,
        boolean youtubeConfigured,
        Map<String, String> details
) {
}
