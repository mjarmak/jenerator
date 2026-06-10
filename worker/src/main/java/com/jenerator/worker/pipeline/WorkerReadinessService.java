package com.jenerator.worker.pipeline;

import com.jenerator.worker.config.WorkerProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class WorkerReadinessService {
    private final WorkerProperties properties;
    private final RestClient restClient;

    public WorkerReadinessService(WorkerProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public boolean ffmpegAvailable() {
        try {
            Process process = new ProcessBuilder(properties.ffmpegPath(), "-version").start();
            return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    public boolean qwenAvailable() {
        if (properties.qwenHealthUrl() == null || properties.qwenHealthUrl().isBlank()) {
            return false;
        }
        try {
            restClient.get()
                    .uri(properties.qwenHealthUrl())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public Duration pollDelay() {
        return Duration.ofMillis(properties.pollDelayMs());
    }
}
