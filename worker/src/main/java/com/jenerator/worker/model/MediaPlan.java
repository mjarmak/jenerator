package com.jenerator.worker.model;

import java.nio.file.Path;
import java.util.List;

public record MediaPlan(
        Path visualPath,
        List<Path> downloadedVisuals,
        Path musicPath,
        Path manifestPath
) {
}
