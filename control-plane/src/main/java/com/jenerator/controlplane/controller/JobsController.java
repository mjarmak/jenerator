package com.jenerator.controlplane.controller;

import com.jenerator.common.dto.CreateJobRequest;
import com.jenerator.common.dto.JobResponse;
import com.jenerator.common.dto.UpdateJobReviewRequest;
import com.jenerator.controlplane.service.ArtifactStorageService;
import com.jenerator.controlplane.service.JobService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobsController {
    private final JobService jobService;
    private final ArtifactStorageService artifactStorageService;

    public JobsController(JobService jobService, ArtifactStorageService artifactStorageService) {
        this.jobService = jobService;
        this.artifactStorageService = artifactStorageService;
    }

    @GetMapping
    public List<JobResponse> list() {
        return jobService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse create(@Valid @RequestBody CreateJobRequest request) {
        return jobService.create(request);
    }

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable String id) {
        return jobService.get(id);
    }

    @PostMapping("/{id}/cancel")
    public JobResponse cancel(@PathVariable String id) {
        return jobService.cancel(id);
    }

    @PostMapping("/{id}/approve-upload")
    public JobResponse approveUpload(@PathVariable String id) {
        return jobService.approveUpload(id);
    }

    @PatchMapping("/{id}/review")
    public JobResponse updateReview(@PathVariable String id, @Valid @RequestBody UpdateJobReviewRequest request) {
        return jobService.updateReview(id, request);
    }

    @GetMapping("/{jobId}/artifacts/{artifactId}/file")
    public ResponseEntity<Resource> getArtifactFile(@PathVariable String jobId, @PathVariable String artifactId) {
        return artifactStorageService.load(jobId, artifactId);
    }
}
