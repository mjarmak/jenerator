package com.jenerator.controlplane.domain;

import java.time.Instant;

public record JobStepRecord(
        String name,
        String status,
        String message,
        Instant updatedAt
) {
}
