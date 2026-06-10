package com.jenerator.controlplane.domain;

import com.jenerator.common.dto.CreateJobRequest;
import com.jenerator.common.model.JobStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JobRecord {
    private final String id;
    private final CreateJobRequest request;
    private final Instant createdAt;
    private final List<JobStepRecord> steps = new ArrayList<>();
    private final List<JobArtifactRecord> artifacts = new ArrayList<>();
    private JobStatus status;
    private Instant updatedAt;
    private String title;
    private String description;
    private List<String> tags = new ArrayList<>();
    private List<String> citations = new ArrayList<>();
    private boolean madeForKids;
    private boolean containsSyntheticMedia = true;
    private String error;
    private String claimedByWorkerId;

    private JobRecord(String id, CreateJobRequest request, Instant now) {
        this.id = id;
        this.request = request;
        this.createdAt = now;
        this.updatedAt = now;
        this.status = JobStatus.QUEUED;
    }

    public static JobRecord create(CreateJobRequest request) {
        Instant now = Instant.now();
        JobRecord job = new JobRecord(UUID.randomUUID().toString(), request, now);
        job.updateStep("QUEUED", "DONE", "Job queued for the local worker.");
        return job;
    }

    public synchronized void claimFor(String workerId, JobStatus newStatus) {
        this.claimedByWorkerId = workerId;
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public synchronized void updateStep(String name, String status, String message) {
        steps.removeIf(step -> step.name().equals(name));
        steps.add(new JobStepRecord(name, status, message, Instant.now()));
        this.updatedAt = Instant.now();
    }

    public synchronized void addArtifact(JobArtifactRecord artifact) {
        artifacts.add(artifact);
        this.updatedAt = Instant.now();
    }

    public synchronized void setStatus(JobStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public synchronized void fail(String error) {
        this.status = JobStatus.FAILED;
        this.error = error;
        this.updatedAt = Instant.now();
    }

    public synchronized String id() {
        return id;
    }

    public synchronized CreateJobRequest request() {
        return request;
    }

    public synchronized JobStatus status() {
        return status;
    }

    public synchronized Instant createdAt() {
        return createdAt;
    }

    public synchronized Instant updatedAt() {
        return updatedAt;
    }

    public synchronized String title() {
        return title;
    }

    public synchronized void setTitle(String title) {
        this.title = title;
        this.updatedAt = Instant.now();
    }

    public synchronized String description() {
        return description;
    }

    public synchronized void setDescription(String description) {
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public synchronized List<String> tags() {
        return List.copyOf(tags);
    }

    public synchronized List<String> citations() {
        return List.copyOf(citations);
    }

    public synchronized void updateReview(
            String title,
            String description,
            List<String> tags,
            List<String> citations,
            Boolean madeForKids,
            Boolean containsSyntheticMedia
    ) {
        this.title = title;
        this.description = description;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.citations = citations == null ? List.of() : List.copyOf(citations);
        if (madeForKids != null) {
            this.madeForKids = madeForKids;
        }
        if (containsSyntheticMedia != null) {
            this.containsSyntheticMedia = containsSyntheticMedia;
        }
        this.updatedAt = Instant.now();
    }

    public synchronized boolean madeForKids() {
        return madeForKids;
    }

    public synchronized boolean containsSyntheticMedia() {
        return containsSyntheticMedia;
    }

    public synchronized String error() {
        return error;
    }

    public synchronized List<JobStepRecord> steps() {
        return List.copyOf(steps);
    }

    public synchronized List<JobArtifactRecord> artifacts() {
        return List.copyOf(artifacts);
    }

    public synchronized String claimedByWorkerId() {
        return claimedByWorkerId;
    }
}
