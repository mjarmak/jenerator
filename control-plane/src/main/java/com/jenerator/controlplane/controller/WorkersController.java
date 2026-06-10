package com.jenerator.controlplane.controller;

import com.jenerator.common.dto.UpdateJobArtifactRequest;
import com.jenerator.common.dto.UpdateJobReviewRequest;
import com.jenerator.common.dto.UpdateJobStepRequest;
import com.jenerator.common.dto.WorkerClaimResponse;
import com.jenerator.common.dto.WorkerHeartbeatRequest;
import com.jenerator.common.dto.WorkerRegistrationRequest;
import com.jenerator.common.dto.WorkerRegistrationResponse;
import com.jenerator.controlplane.service.ArtifactStorageService;
import com.jenerator.controlplane.service.AssetService;
import com.jenerator.controlplane.service.JobService;
import com.jenerator.controlplane.service.WorkerRegistry;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/workers")
public class WorkersController {
    private static final String WORKER_TOKEN_HEADER = "X-Worker-Token";

    private final WorkerRegistry workerRegistry;
    private final JobService jobService;
    private final ArtifactStorageService artifactStorageService;
    private final AssetService assetService;

    public WorkersController(
            WorkerRegistry workerRegistry,
            JobService jobService,
            ArtifactStorageService artifactStorageService,
            AssetService assetService
    ) {
        this.workerRegistry = workerRegistry;
        this.jobService = jobService;
        this.artifactStorageService = artifactStorageService;
        this.assetService = assetService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkerRegistrationResponse register(@Valid @RequestBody WorkerRegistrationRequest request) {
        return workerRegistry.register(request);
    }

    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @Valid @RequestBody WorkerHeartbeatRequest request
    ) {
        String workerId = workerRegistry.requireWorkerId(workerToken);
        workerRegistry.heartbeat(workerId, request);
    }

    @PostMapping("/jobs/claim")
    public WorkerClaimResponse claim(@RequestHeader(WORKER_TOKEN_HEADER) String workerToken) {
        String workerId = workerRegistry.requireWorkerId(workerToken);
        return jobService.claim(workerId);
    }

    @PostMapping("/jobs/{jobId}/steps")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStep(
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @org.springframework.web.bind.annotation.PathVariable String jobId,
            @Valid @RequestBody UpdateJobStepRequest request
    ) {
        workerRegistry.requireWorkerId(workerToken);
        jobService.updateStep(jobId, request);
    }

    @PatchMapping("/jobs/{jobId}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateReview(
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @org.springframework.web.bind.annotation.PathVariable String jobId,
            @Valid @RequestBody UpdateJobReviewRequest request
    ) {
        workerRegistry.requireWorkerId(workerToken);
        jobService.updateReview(jobId, request);
    }

    @PostMapping("/jobs/{jobId}/artifacts")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addArtifact(
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @org.springframework.web.bind.annotation.PathVariable String jobId,
            @Valid @RequestBody UpdateJobArtifactRequest request
    ) {
        workerRegistry.requireWorkerId(workerToken);
        jobService.addArtifact(jobId, request);
    }

    @PostMapping(value = "/jobs/{jobId}/artifact-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addArtifactFile(
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @org.springframework.web.bind.annotation.PathVariable String jobId,
            @org.springframework.web.bind.annotation.RequestParam("type") String type,
            @org.springframework.web.bind.annotation.RequestParam("file") MultipartFile file
    ) throws java.io.IOException {
        workerRegistry.requireWorkerId(workerToken);
        artifactStorageService.store(jobId, type, file);
    }

    @GetMapping("/assets/{assetId}/file")
    public ResponseEntity<Resource> downloadAsset(
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @org.springframework.web.bind.annotation.PathVariable String assetId
    ) {
        workerRegistry.requireWorkerId(workerToken);
        return assetService.load(assetId);
    }
}
