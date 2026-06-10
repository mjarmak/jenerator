package com.jenerator.worker.model;

import java.nio.file.Path;

public record AudioTrack(
        Path path,
        String type,
        String message
) {
}
