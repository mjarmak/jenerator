package com.jenerator.worker.model;

public record YoutubeUploadResult(
        String videoId,
        String url,
        boolean dryRun
) {
}
