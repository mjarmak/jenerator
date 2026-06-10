package com.jenerator.controlplane.domain;

import java.nio.file.Path;

public record StoredArtifactRecord(
        String artifactId,
        String jobId,
        String filename,
        String contentType,
        Path path
) {
}
