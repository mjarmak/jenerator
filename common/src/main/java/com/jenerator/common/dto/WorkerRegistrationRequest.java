package com.jenerator.common.dto;

import jakarta.validation.constraints.NotBlank;

public record WorkerRegistrationRequest(
        @NotBlank String workerName,
        @NotBlank String pairingCode
) {
}
