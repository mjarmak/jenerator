package com.jenerator.controlplane.domain;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;

public class WorkerRecord {
    private static final Duration ONLINE_WINDOW = Duration.ofSeconds(45);

    private final String id;
    private final String token;
    private final String name;
    private final Instant registeredAt;
    private Instant lastSeenAt;
    private boolean ffmpegAvailable;
    private boolean qwenAvailable;
    private boolean youtubeConfigured;
    private Map<String, String> details = Map.of();

    public WorkerRecord(String id, String token, String name, Instant lastSeenAt) {
        this.id = id;
        this.token = token;
        this.name = name;
        this.registeredAt = lastSeenAt;
        this.lastSeenAt = lastSeenAt;
    }

    public synchronized void heartbeat(boolean ffmpegAvailable, boolean qwenAvailable, boolean youtubeConfigured, Map<String, String> details) {
        this.lastSeenAt = Instant.now();
        this.ffmpegAvailable = ffmpegAvailable;
        this.qwenAvailable = qwenAvailable;
        this.youtubeConfigured = youtubeConfigured;
        this.details = Map.copyOf(details);
    }

    public String id() {
        return id;
    }

    public String token() {
        return token;
    }

    public String name() {
        return name;
    }

    public synchronized Instant lastSeenAt() {
        return lastSeenAt;
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    public synchronized boolean online(Instant now) {
        return lastSeenAt.plus(ONLINE_WINDOW).isAfter(now);
    }

    public synchronized boolean ffmpegAvailable() {
        return ffmpegAvailable;
    }

    public synchronized boolean qwenAvailable() {
        return qwenAvailable;
    }

    public synchronized boolean youtubeConfigured() {
        return youtubeConfigured;
    }

    public synchronized Map<String, String> details() {
        return Map.copyOf(details);
    }
}
