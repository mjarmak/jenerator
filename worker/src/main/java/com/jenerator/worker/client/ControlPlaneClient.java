package com.jenerator.worker.client;

import com.jenerator.common.dto.UpdateJobArtifactRequest;
import com.jenerator.common.dto.UpdateJobReviewRequest;
import com.jenerator.common.dto.UpdateJobStepRequest;
import com.jenerator.common.dto.JobArtifactResponse;
import com.jenerator.common.dto.JobResponse;
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
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.URI;
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
        withWorkerTokenRetry(token -> {
            restClient.post()
                .uri("/api/workers/heartbeat")
                .header(WORKER_TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
            return null;
        });
    }

    public WorkerClaimResponse claimJob() {
        WorkerClaimResponse response = withWorkerTokenRetry(token -> restClient.post()
                .uri("/api/workers/jobs/claim")
                .header(WORKER_TOKEN_HEADER, token)
                .retrieve()
                .body(WorkerClaimResponse.class));
        return response == null ? WorkerClaimResponse.empty() : response;
    }

    public JobResponse getJob(String jobId) {
        JobResponse response = withWorkerTokenRetry(token -> restClient.get()
                .uri("/api/workers/jobs/{jobId}", jobId)
                .header(WORKER_TOKEN_HEADER, token)
                .retrieve()
                .body(JobResponse.class));
        if (response == null) {
            throw new IllegalStateException("Job status lookup returned an empty response: " + jobId);
        }
        return response;
    }

    public void updateStep(String jobId, UpdateJobStepRequest request) {
        withWorkerTokenRetry(token -> {
            restClient.post()
                .uri("/api/workers/jobs/{jobId}/steps", jobId)
                .header(WORKER_TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
            return null;
        });
    }

    public void updateReview(String jobId, UpdateJobReviewRequest request) {
        withWorkerTokenRetry(token -> {
            restClient.patch()
                .uri("/api/workers/jobs/{jobId}/review", jobId)
                .header(WORKER_TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
            return null;
        });
    }

    public void addArtifact(String jobId, UpdateJobArtifactRequest request) {
        withWorkerTokenRetry(token -> {
            restClient.post()
                .uri("/api/workers/jobs/{jobId}/artifacts", jobId)
                .header(WORKER_TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
            return null;
        });
    }

    public void addArtifactFile(String jobId, String type, Path path) {
        withWorkerTokenRetry(token -> {
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
            return null;
        });
    }

    public Path downloadAsset(String assetId, Path directory) throws IOException {
        ResponseEntity<byte[]> response = withWorkerTokenRetry(token -> restClient.get()
                .uri("/api/workers/assets/{assetId}/file", assetId)
                .header(WORKER_TOKEN_HEADER, token)
                .retrieve()
                .toEntity(byte[].class));
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

    public Path downloadArtifact(JobArtifactResponse artifact, Path directory) throws IOException {
        ResponseEntity<byte[]> response = withWorkerTokenRetry(token -> {
            var request = restClient.get();
            var spec = artifact.url().startsWith("http://") || artifact.url().startsWith("https://")
                    ? request.uri(URI.create(artifact.url()))
                    : request.uri(artifact.url());
            return spec.header(WORKER_TOKEN_HEADER, token)
                    .retrieve()
                    .toEntity(byte[].class);
        });
        byte[] body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("Artifact download returned an empty response: " + artifact.id());
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(safeArtifactFilename(artifact, response)).normalize();
        if (!target.startsWith(directory.normalize())) {
            throw new IllegalStateException("Artifact download path resolved outside the job directory.");
        }
        Files.write(target, body);
        return target;
    }

    private String requireToken() {
        return ensureToken().orElseThrow(() -> new IllegalStateException("Worker token is not configured and pairing failed."));
    }

    private <T> T withWorkerTokenRetry(WorkerRequest<T> request) {
        String token = requireToken();
        try {
            return request.execute(token);
        } catch (RestClientResponseException exception) {
            if (!isUnauthorized(exception) || !hasPairingCode()) {
                throw exception;
            }
            activeToken = null;
            return request.execute(requireToken());
        }
    }

    private boolean isUnauthorized(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return status == 401 || status == 403;
    }

    private boolean hasPairingCode() {
        return properties.pairingCode() != null && !properties.pairingCode().isBlank();
    }

    private String safeFilename(String assetId, ResponseEntity<byte[]> response) {
        String filename = response.getHeaders().getContentDisposition().getFilename();
        if (filename == null || filename.isBlank()) {
            filename = assetId + ".bin";
        }
        return assetId + "-" + filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String safeArtifactFilename(JobArtifactResponse artifact, ResponseEntity<byte[]> response) {
        String filename = response.getHeaders().getContentDisposition().getFilename();
        if (filename == null || filename.isBlank()) {
            filename = artifact.filename() == null || artifact.filename().isBlank() ? artifact.id() + ".bin" : artifact.filename();
        }
        return artifact.id() + "-" + filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private interface WorkerRequest<T> {
        T execute(String token);
    }
}
