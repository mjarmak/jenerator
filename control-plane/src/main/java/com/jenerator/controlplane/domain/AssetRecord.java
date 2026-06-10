package com.jenerator.controlplane.domain;

import java.time.Instant;

public record AssetRecord(
        String id,
        String filename,
        String contentType,
        long sizeBytes,
        String storagePath,
        Instant createdAt
) {
}
