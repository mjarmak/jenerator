package com.jenerator.common.dto;

import com.jenerator.common.model.JobStatus;
import jakarta.validation.constraints.NotBlank;

public record UpdateJobStepRequest(
        @NotBlank String stepName,
        @NotBlank String stepStatus,
        String message,
        JobStatus jobStatus
) {
}
