package com.jenerator.controlplane.service;

import com.jenerator.common.dto.WorkerHeartbeatRequest;
import com.jenerator.common.dto.WorkerRegistrationRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerRegistryTest {
    @Test
    void reportsWorkerReadinessWithoutToken() {
        WorkerRegistry registry = new WorkerRegistry("pair");
        var registration = registry.register(new WorkerRegistrationRequest("pc-worker", "pair"));
        String workerId = registry.requireWorkerId(registration.workerToken());
        registry.heartbeat(workerId, new WorkerHeartbeatRequest(
                "pc-worker",
                true,
                false,
                true,
                Map.of("workspace", "data/worker")
        ));

        var statuses = registry.listStatuses();

        assertThat(statuses).hasSize(1);
        var status = statuses.get(0);
        assertThat(status.name()).isEqualTo("pc-worker");
        assertThat(status.online()).isTrue();
        assertThat(status.ffmpegAvailable()).isTrue();
        assertThat(status.qwenAvailable()).isFalse();
        assertThat(status.youtubeConfigured()).isTrue();
        assertThat(status.details()).containsEntry("workspace", "data/worker");
    }
}
