package com.jenerator.common.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateJobReviewRequest(
        @Size(max = 100) String title,
        @Size(max = 5_000) String description,
        @Size(max = 30) List<@Size(max = 60) String> tags,
        @Size(max = 50) List<@Size(max = 500) String> citations,
        Boolean madeForKids,
        Boolean containsSyntheticMedia
) {
    public UpdateJobReviewRequest {
        tags = tags == null ? List.of() : tags.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        citations = citations == null ? List.of() : citations.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}
