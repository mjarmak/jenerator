package com.jenerator.worker.model;

import java.nio.file.Path;

public record YoutubeUploadResult(
        String videoId,
        String url,
        boolean dryRun,
        Path artifactPath
) {
}
