package com.jenerator.worker.model;

import java.nio.file.Path;

public record RenderArtifact(
        Path path,
        String type,
        String publicUrl,
        long sizeBytes
) {
}
