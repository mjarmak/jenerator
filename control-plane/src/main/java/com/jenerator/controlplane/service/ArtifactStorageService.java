package com.jenerator.controlplane.service;

import com.jenerator.common.model.JobStatus;
import com.jenerator.controlplane.domain.JobArtifactRecord;
import com.jenerator.controlplane.domain.StoredArtifactRecord;
import com.jenerator.controlplane.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ArtifactStorageService {
    private final Path artifactStoragePath;
    private final JobService jobService;
    private final ControlPlaneStateStore stateStore;
    private final ConcurrentMap<String, StoredArtifactRecord> artifacts = new ConcurrentHashMap<>();

    public ArtifactStorageService(
            @Value("${jenerator.artifact-storage-path:data/artifacts}") Path artifactStoragePath,
            JobService jobService,
            ControlPlaneStateStore stateStore
    ) {
        this.artifactStoragePath = artifactStoragePath.toAbsolutePath().normalize();
        this.jobService = jobService;
        this.stateStore = stateStore;
        loadPersistedArtifacts();
    }

    public void store(String jobId, String type, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalStateException("Artifact file is empty.");
        }
        if (jobService.get(jobId).status() == JobStatus.CANCELLED) {
            return;
        }
        String artifactId = UUID.randomUUID().toString();
        String originalName = file.getOriginalFilename() == null ? "artifact.bin" : file.getOriginalFilename();
        String safeName = originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        Path jobDirectory = artifactStoragePath.resolve(jobId).normalize();
        if (!jobDirectory.startsWith(artifactStoragePath)) {
            throw new IllegalStateException("Artifact path resolved outside the storage directory.");
        }
        Files.createDirectories(jobDirectory);
        Path target = jobDirectory.resolve(artifactId + "-" + safeName).normalize();
        if (!target.startsWith(jobDirectory)) {
            throw new IllegalStateException("Artifact file path resolved outside the job directory.");
        }
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }

        StoredArtifactRecord stored = new StoredArtifactRecord(
                artifactId,
                jobId,
                safeName,
                file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType(),
                target
        );
        artifacts.put(artifactId, stored);
        persist();
        jobService.addArtifactRecord(jobId, new JobArtifactRecord(
                artifactId,
                type,
                safeName,
                "/api/jobs/" + jobId + "/artifacts/" + artifactId + "/file",
                file.getSize(),
                Instant.now()
        ));
    }

    public ResponseEntity<Resource> load(String jobId, String artifactId) {
        StoredArtifactRecord stored = artifacts.get(artifactId);
        if (stored == null || !stored.jobId().equals(jobId)) {
            throw new NotFoundException("Artifact not found: " + artifactId);
        }
        try {
            Resource resource = new UrlResource(stored.path().toUri());
            if (!resource.exists()) {
                throw new NotFoundException("Artifact file not found: " + artifactId);
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(stored.contentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + stored.filename() + "\"")
                    .body(resource);
        } catch (MalformedURLException exception) {
            throw new IllegalStateException("Artifact file URL is invalid.", exception);
        }
    }

    private void loadPersistedArtifacts() {
        for (StoredArtifactRecord record : stateStore.readArtifacts()) {
            artifacts.put(record.artifactId(), record);
        }
    }

    private void persist() {
        stateStore.writeArtifacts(artifacts.values());
    }
}
