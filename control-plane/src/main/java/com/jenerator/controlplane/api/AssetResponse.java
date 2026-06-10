package com.jenerator.controlplane.api;

import java.time.Instant;

public record AssetResponse(
        String id,
        String filename,
        String contentType,
        long sizeBytes,
        String url,
        Instant createdAt
) {
}
