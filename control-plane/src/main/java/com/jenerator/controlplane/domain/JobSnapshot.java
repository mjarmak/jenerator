package com.jenerator.controlplane.domain;

import com.jenerator.common.dto.CreateJobRequest;
import com.jenerator.common.model.JobStatus;

import java.time.Instant;
import java.util.List;

public record JobSnapshot(
        String id,
        CreateJobRequest request,
        Instant createdAt,
        JobStatus status,
        Instant updatedAt,
        String title,
        String description,
        List<String> tags,
        List<String> citations,
        boolean madeForKids,
        boolean containsSyntheticMedia,
        String error,
        String claimedByWorkerId,
        List<JobStepRecord> steps,
        List<JobArtifactRecord> artifacts
) {
}
