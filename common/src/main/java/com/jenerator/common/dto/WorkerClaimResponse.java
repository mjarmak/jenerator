package com.jenerator.common.dto;

public record WorkerClaimResponse(
        boolean claimed,
        JobResponse job
) {
    public static WorkerClaimResponse empty() {
        return new WorkerClaimResponse(false, null);
    }
}
