package com.jenerator.worker.model;

import java.nio.file.Path;
import java.util.List;

public record CaptionTrack(
        Path path,
        List<CaptionCue> cues
) {
}
