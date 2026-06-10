package com.jenerator.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateJobArtifactRequest(
        @NotBlank String type,
        @NotBlank String filename,
        @NotBlank String url,
        @PositiveOrZero long sizeBytes
) {
}
