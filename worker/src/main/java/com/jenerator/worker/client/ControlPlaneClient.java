package com.jenerator.worker.client;

import com.jenerator.common.dto.UpdateJobArtifactRequest;
import com.jenerator.common.dto.UpdateJobReviewRequest;
import com.jenerator.common.dto.UpdateJobStepRequest;
import com.jenerator.common.dto.WorkerClaimResponse;
import com.jenerator.common.dto.WorkerHeartbeatRequest;
import com.jenerator.common.dto.WorkerRegistrationRequest;
import com.jenerator.common.dto.WorkerRegistrationResponse;
import com.jenerator.worker.config.WorkerProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class ControlPlaneClient {
    private static final String WORKER_TOKEN_HEADER = "X-Worker-Token";

    private final WorkerProperties properties;
    private final RestClient restClient;
    private volatile String activeToken;

    public ControlPlaneClient(WorkerProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.controlPlaneUrl()).build();
        this.activeToken = properties.token();
    }

    public Optional<String> ensureToken() {
        if (activeToken != null && !activeToken.isBlank()) {
            return Optional.of(activeToken);
        }
        if (properties.pairingCode() == null || properties.pairingCode().isBlank()) {
            return Optional.empty();
        }
        WorkerRegistrationResponse response = restClient.post()
                .uri("/api/workers/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new WorkerRegistrationRequest(properties.name(), properties.pairingCode()))
                .retrieve()
                .body(WorkerRegistrationResponse.class);
        if (response == null || response.workerToken() == null || response.workerToken().isBlank()) {
            return Optional.empty();
        }
        activeToken = response.workerToken();
        return Optional.of(activeToken);
    }

    public void heartbeat(WorkerHeartbeatRequest request) {
        String token = requireToken();
        restClient.post()
                .uri("/api/workers/heartbeat")
                .header(WORKER_TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public WorkerClaimResponse claimJob() {
        String token = requireToken();
        WorkerClaimResponse response = restClient.post()
                .uri("/api/workers/jobs/claim")
                .header(WORKER_TOKEN_HEADER, token)
                .retrieve()
                .body(WorkerClaimResponse.class);
        return response == null ? WorkerClaimResponse.empty() : response;
    }

    public void updateStep(String jobId, UpdateJobStepRequest request) {
        String token = requireToken();
        restClient.post()
                .uri("/api/workers/jobs/{jobId}/steps", jobId)
                .header(WORKER_TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void updateReview(String jobId, UpdateJobReviewRequest request) {
        String token = requireToken();
        restClient.patch()
                .uri("/api/workers/jobs/{jobId}/review", jobId)
                .header(WORKER_TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void addArtifact(String jobId, UpdateJobArtifactRequest request) {
        String token = requireToken();
        restClient.post()
                .uri("/api/workers/jobs/{jobId}/artifacts", jobId)
                .header(WORKER_TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void addArtifactFile(String jobId, String type, Path path) {
        String token = requireToken();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("type", type);
        body.add("file", new FileSystemResource(path));
        restClient.post()
                .uri("/api/workers/jobs/{jobId}/artifact-file", jobId)
                .header(WORKER_TOKEN_HEADER, token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public Path downloadAsset(String assetId, Path directory) throws IOException {
        String token = requireToken();
        ResponseEntity<byte[]> response = restClient.get()
                .uri("/api/workers/assets/{assetId}/file", assetId)
                .header(WORKER_TOKEN_HEADER, token)
                .retrieve()
                .toEntity(byte[].class);
        byte[] body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("Asset download returned an empty response: " + assetId);
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(safeFilename(assetId, response)).normalize();
        if (!target.startsWith(directory.normalize())) {
            throw new IllegalStateException("Asset download path resolved outside the job directory.");
        }
        Files.write(target, body);
        return target;
    }

    private String requireToken() {
        return ensureToken().orElseThrow(() -> new IllegalStateException("Worker token is not configured and pairing failed."));
    }

    private String safeFilename(String assetId, ResponseEntity<byte[]> response) {
        String filename = response.getHeaders().getContentDisposition().getFilename();
        if (filename == null || filename.isBlank()) {
            filename = assetId + ".bin";
        }
        return assetId + "-" + filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
