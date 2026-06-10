package com.jenerator.controlplane.service;

import com.jenerator.controlplane.api.WorkerStatusResponse;
import com.jenerator.common.dto.WorkerHeartbeatRequest;
import com.jenerator.common.dto.WorkerRegistrationRequest;
import com.jenerator.common.dto.WorkerRegistrationResponse;
import com.jenerator.controlplane.domain.WorkerRecord;
import com.jenerator.controlplane.exception.UnauthorizedWorkerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class WorkerRegistry {
    private final String pairingCode;
    private final ConcurrentMap<String, WorkerRecord> workersByToken = new ConcurrentHashMap<>();

    public WorkerRegistry(@Value("${jenerator.worker-pairing-code:change-me}") String pairingCode) {
        this.pairingCode = pairingCode;
    }

    public WorkerRegistrationResponse register(WorkerRegistrationRequest request) {
        if (!pairingCode.equals(request.pairingCode())) {
            throw new UnauthorizedWorkerException("Worker pairing code is invalid.");
        }
        String workerId = UUID.randomUUID().toString();
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        WorkerRecord record = new WorkerRecord(workerId, token, request.workerName(), Instant.now());
        workersByToken.put(token, record);
        return new WorkerRegistrationResponse(workerId, token);
    }

    public String requireWorkerId(String workerToken) {
        return requireWorker(workerToken).id();
    }

    public WorkerRecord requireWorker(String workerToken) {
        WorkerRecord record = workersByToken.get(workerToken);
        if (record == null) {
            throw new UnauthorizedWorkerException("Worker token is missing or invalid.");
        }
        return record;
    }

    public void heartbeat(String workerId, WorkerHeartbeatRequest request) {
        workersByToken.values()
                .stream()
                .filter(worker -> worker.id().equals(workerId))
                .findFirst()
                .ifPresent(worker -> worker.heartbeat(
                        request.ffmpegAvailable(),
                        request.qwenAvailable(),
                        request.youtubeConfigured(),
                        request.details()
                ));
    }

    public List<WorkerStatusResponse> listStatuses() {
        Instant now = Instant.now();
        return workersByToken.values()
                .stream()
                .sorted(Comparator.comparing(WorkerRecord::lastSeenAt).reversed())
                .map(worker -> new WorkerStatusResponse(
                        worker.id(),
                        worker.name(),
                        worker.registeredAt(),
                        worker.lastSeenAt(),
                        worker.online(now),
                        worker.ffmpegAvailable(),
                        worker.qwenAvailable(),
                        worker.youtubeConfigured(),
                        worker.details()
                ))
                .toList();
    }
}
