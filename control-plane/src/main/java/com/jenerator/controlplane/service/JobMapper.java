package com.jenerator.controlplane.service;

import com.jenerator.common.dto.JobArtifactResponse;
import com.jenerator.common.dto.JobResponse;
import com.jenerator.common.dto.JobStepResponse;
import com.jenerator.controlplane.domain.JobArtifactRecord;
import com.jenerator.controlplane.domain.JobRecord;
import com.jenerator.controlplane.domain.JobStepRecord;
import org.springframework.stereotype.Component;

@Component
public class JobMapper {
    public JobResponse toResponse(JobRecord record) {
        var request = record.request();
        return new JobResponse(
                record.id(),
                request.prompt(),
                request.videoType(),
                request.orientation(),
                request.durationPreset(),
                request.publishTarget(),
                request.voiceProvider(),
                request.mediaStyle(),
                request.contentCategory(),
                request.editorialWindow(),
                request.researchFocus(),
                request.listSize(),
                request.visualSource(),
                request.sourceUrl(),
                request.sourceTitle(),
                request.sourceScope(),
                request.seasonNumber(),
                request.episodeNumber(),
                request.musicAssetId(),
                request.visualAssetIds(),
                request.assetIds(),
                request.targetMetadata(),
                record.status(),
                request.orientation().width(),
                request.orientation().height(),
                request.durationPreset().targetSeconds(),
                record.title(),
                record.description(),
                record.tags(),
                record.citations(),
                record.madeForKids(),
                record.containsSyntheticMedia(),
                record.error(),
                record.createdAt(),
                record.updatedAt(),
                record.steps().stream().map(this::toStepResponse).toList(),
                record.artifacts().stream().map(this::toArtifactResponse).toList()
        );
    }

    private JobStepResponse toStepResponse(JobStepRecord record) {
        return new JobStepResponse(record.name(), record.status(), record.message(), record.updatedAt());
    }

    private JobArtifactResponse toArtifactResponse(JobArtifactRecord record) {
        return new JobArtifactResponse(
                record.id(),
                record.type(),
                record.filename(),
                record.url(),
                record.sizeBytes(),
                record.createdAt()
        );
    }
}
