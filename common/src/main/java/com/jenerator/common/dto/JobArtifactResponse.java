package com.jenerator.common.dto;

import java.time.Instant;

public record JobArtifactResponse(
        String id,
        String type,
        String filename,
        String url,
        long sizeBytes,
        Instant createdAt
) {
}
