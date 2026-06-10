package com.jenerator.common.dto;

import com.jenerator.common.model.ContentCategory;
import com.jenerator.common.model.DurationPreset;
import com.jenerator.common.model.EditorialWindow;
import com.jenerator.common.model.MediaStyle;
import com.jenerator.common.model.Orientation;
import com.jenerator.common.model.PublishTarget;
import com.jenerator.common.model.ResearchFocus;
import com.jenerator.common.model.SourceScope;
import com.jenerator.common.model.VideoType;
import com.jenerator.common.model.VisualSource;
import com.jenerator.common.model.VoiceProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record CreateJobRequest(
        @NotBlank @Size(max = 4_000) String prompt,
        @NotNull VideoType videoType,
        @NotNull Orientation orientation,
        @NotNull DurationPreset durationPreset,
        @NotNull PublishTarget publishTarget,
        @NotNull VoiceProvider voiceProvider,
        @NotNull MediaStyle mediaStyle,
        ContentCategory contentCategory,
        EditorialWindow editorialWindow,
        ResearchFocus researchFocus,
        Integer listSize,
        @NotNull VisualSource visualSource,
        @Size(max = 2_048) String sourceUrl,
        @Size(max = 160) String sourceTitle,
        SourceScope sourceScope,
        Integer seasonNumber,
        Integer episodeNumber,
        @Size(max = 120) String musicAssetId,
        List<String> visualAssetIds,
        List<String> assetIds,
        Map<String, String> targetMetadata
) {
    public CreateJobRequest {
        contentCategory = contentCategory == null ? ContentCategory.MOVIES_AND_SERIES : contentCategory;
        editorialWindow = editorialWindow == null ? EditorialWindow.WEEK : editorialWindow;
        researchFocus = researchFocus == null ? ResearchFocus.TRENDING : researchFocus;
        listSize = listSize == null ? 8 : listSize;
        if (listSize < 3 || listSize > 20) {
            throw new IllegalArgumentException("listSize must be between 3 and 20.");
        }
        sourceUrl = sourceUrl == null || sourceUrl.isBlank() ? null : sourceUrl.trim();
        sourceTitle = sourceTitle == null || sourceTitle.isBlank() ? null : sourceTitle.trim();
        sourceScope = sourceScope == null ? defaultSourceScope(seasonNumber, episodeNumber) : sourceScope;
        if (seasonNumber != null && seasonNumber < 1) {
            throw new IllegalArgumentException("seasonNumber must be positive when provided.");
        }
        if (episodeNumber != null && episodeNumber < 1) {
            throw new IllegalArgumentException("episodeNumber must be positive when provided.");
        }
        visualAssetIds = visualAssetIds == null ? List.of() : List.copyOf(visualAssetIds);
        assetIds = assetIds == null ? List.of() : List.copyOf(assetIds);
        targetMetadata = targetMetadata == null ? Map.of() : Map.copyOf(targetMetadata);
    }

    private static SourceScope defaultSourceScope(Integer seasonNumber, Integer episodeNumber) {
        if (seasonNumber != null && episodeNumber != null) {
            return SourceScope.SERIES_EPISODE;
        }
        if (seasonNumber != null) {
            return SourceScope.SERIES_SEASON;
        }
        return SourceScope.MOVIE;
    }
}
