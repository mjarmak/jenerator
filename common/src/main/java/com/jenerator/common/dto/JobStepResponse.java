package com.jenerator.common.dto;

import java.time.Instant;

public record JobStepResponse(
        String name,
        String status,
        String message,
        Instant updatedAt
) {
}
