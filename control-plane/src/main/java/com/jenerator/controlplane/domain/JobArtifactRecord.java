package com.jenerator.controlplane.domain;

import java.time.Instant;

public record JobArtifactRecord(
        String id,
        String type,
        String filename,
        String url,
        long sizeBytes,
        Instant createdAt
) {
}
