package com.jenerator.controlplane.api;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        List<String> messages
) {
    public static ApiError of(int status, String error, List<String> messages) {
        return new ApiError(Instant.now(), status, error, List.copyOf(messages));
    }
}
