package com.jenerator.controlplane.domain;

public record StoredArtifactSnapshot(
        String artifactId,
        String jobId,
        String filename,
        String contentType,
        String path
) {
    public StoredArtifactRecord toRecord() {
        return new StoredArtifactRecord(
                artifactId,
                jobId,
                filename,
                contentType,
                java.nio.file.Path.of(path)
        );
    }

    public static StoredArtifactSnapshot from(StoredArtifactRecord record) {
        return new StoredArtifactSnapshot(
                record.artifactId(),
                record.jobId(),
                record.filename(),
                record.contentType(),
                record.path().toString()
        );
    }
}
