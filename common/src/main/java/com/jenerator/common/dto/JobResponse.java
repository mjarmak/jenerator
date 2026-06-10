package com.jenerator.common.dto;

import com.jenerator.common.model.ContentCategory;
import com.jenerator.common.model.DurationPreset;
import com.jenerator.common.model.EditorialWindow;
import com.jenerator.common.model.JobStatus;
import com.jenerator.common.model.MediaStyle;
import com.jenerator.common.model.Orientation;
import com.jenerator.common.model.PublishTarget;
import com.jenerator.common.model.ResearchFocus;
import com.jenerator.common.model.SourceScope;
import com.jenerator.common.model.VideoType;
import com.jenerator.common.model.VisualSource;
import com.jenerator.common.model.VoiceProvider;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record JobResponse(
        String id,
        String prompt,
        VideoType videoType,
        Orientation orientation,
        DurationPreset durationPreset,
        PublishTarget publishTarget,
        VoiceProvider voiceProvider,
        MediaStyle mediaStyle,
        ContentCategory contentCategory,
        EditorialWindow editorialWindow,
        ResearchFocus researchFocus,
        int listSize,
        VisualSource visualSource,
        String sourceUrl,
        String sourceTitle,
        SourceScope sourceScope,
        Integer seasonNumber,
        Integer episodeNumber,
        String musicAssetId,
        List<String> visualAssetIds,
        List<String> assetIds,
        Map<String, String> targetMetadata,
        JobStatus status,
        int width,
        int height,
        int targetSeconds,
        String title,
        String description,
        List<String> tags,
        List<String> citations,
        boolean madeForKids,
        boolean containsSyntheticMedia,
        String error,
        Instant createdAt,
        Instant updatedAt,
        List<JobStepResponse> steps,
        List<JobArtifactResponse> artifacts
) {
}
