package com.jenerator.common.validation;

import com.jenerator.common.dto.CreateJobRequest;
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JobRulesTest {
    @Test
    void validatesAllOrientationAndPublishTargetCombinations() {
        assertThat(errors(Orientation.PORTRAIT_9_16, PublishTarget.YOUTUBE_SHORT)).isEmpty();
        assertThat(errors(Orientation.LANDSCAPE_16_9, PublishTarget.YOUTUBE_VIDEO)).isEmpty();
        assertThat(errors(Orientation.LANDSCAPE_16_9, PublishTarget.YOUTUBE_SHORT)).hasSize(1);
        assertThat(errors(Orientation.PORTRAIT_9_16, PublishTarget.YOUTUBE_VIDEO)).hasSize(1);
    }

    @Test
    void durationPresetsFitPublishedTargets() {
        assertThat(DurationPreset.ONE_MINUTE.minSeconds()).isEqualTo(55);
        assertThat(DurationPreset.ONE_MINUTE.maxSeconds()).isEqualTo(60);
        assertThat(DurationPreset.AUTO_SHORT.maxSeconds()).isEqualTo(180);
    }

    @Test
    void sourceScreenshotsNeedUrlOrUploadedVisuals() {
        CreateJobRequest request = request(VisualSource.SOURCE_SCREENSHOTS, null, List.of());

        assertThat(JobRules.validate(request)).contains("Source screenshots need a source URL or uploaded screenshot assets.");
    }

    @Test
    void uploadedVisualsNeedAssetIds() {
        CreateJobRequest request = request(VisualSource.UPLOADED_ASSETS, null, List.of());

        assertThat(JobRules.validate(request)).contains("Uploaded visuals mode needs at least one uploaded visual asset.");
    }

    @Test
    void defaultsResearchFocusToTrending() {
        CreateJobRequest request = new CreateJobRequest(
                "recap the latest mystery release",
                VideoType.RECAP,
                Orientation.PORTRAIT_9_16,
                DurationPreset.ONE_MINUTE,
                PublishTarget.YOUTUBE_SHORT,
                VoiceProvider.OPENAI_TTS,
                MediaStyle.CINEMATIC_RECAP,
                ContentCategory.MOVIES_AND_SERIES,
                EditorialWindow.WEEK,
                null,
                8,
                VisualSource.SOURCE_SCREENSHOTS,
                "https://example.com/show",
                "Example Show",
                null,
                1,
                1,
                null,
                List.of(),
                List.of(),
                Map.of()
        );

        assertThat(request.researchFocus()).isEqualTo(ResearchFocus.TRENDING);
    }

    @Test
    void validatesSourceScopeNumberRequirements() {
        assertThat(requestWithScope(SourceScope.MOVIE, 1, null))
                .contains("Movie jobs should not include season or episode numbers.");
        assertThat(requestWithScope(SourceScope.SERIES_EPISODE, 1, null))
                .contains("Series episode jobs need both season and episode numbers.");
        assertThat(requestWithScope(SourceScope.SERIES_SEASON, null, null))
                .contains("Series season jobs need a season number.");
        assertThat(requestWithScope(SourceScope.SERIES_SEASON, 1, 2))
                .contains("Series season jobs should not include an episode number.");
        assertThat(requestWithScope(SourceScope.SERIES_SHOW, 1, null))
                .contains("Whole-series jobs should not include season or episode numbers.");
        assertThat(requestWithScope(SourceScope.SERIES_EPISODE, 1, 2)).isEmpty();
    }

    private List<String> errors(Orientation orientation, PublishTarget publishTarget) {
        return JobRules.validate(new CreateJobRequest(
                "recap the latest mystery release",
                VideoType.RECAP,
                orientation,
                DurationPreset.ONE_MINUTE,
                publishTarget,
                VoiceProvider.OPENAI_TTS,
                MediaStyle.CINEMATIC_RECAP,
                ContentCategory.MOVIES_AND_SERIES,
                EditorialWindow.WEEK,
                ResearchFocus.TRENDING,
                8,
                VisualSource.SOURCE_SCREENSHOTS,
                "https://example.com/show",
                "Example Show",
                SourceScope.SERIES_EPISODE,
                1,
                1,
                null,
                List.of(),
                List.of(),
                Map.of()
        ));
    }

    private CreateJobRequest request(VisualSource visualSource, String sourceUrl, List<String> visualAssetIds) {
        return new CreateJobRequest(
                "recap the latest mystery release",
                VideoType.RECAP,
                Orientation.PORTRAIT_9_16,
                DurationPreset.ONE_MINUTE,
                PublishTarget.YOUTUBE_SHORT,
                VoiceProvider.OPENAI_TTS,
                MediaStyle.CINEMATIC_RECAP,
                ContentCategory.MOVIES_AND_SERIES,
                EditorialWindow.WEEK,
                ResearchFocus.TRENDING,
                8,
                visualSource,
                sourceUrl,
                "Example Show",
                SourceScope.SERIES_EPISODE,
                1,
                1,
                null,
                visualAssetIds,
                visualAssetIds,
                Map.of()
        );
    }

    private List<String> requestWithScope(SourceScope sourceScope, Integer seasonNumber, Integer episodeNumber) {
        return JobRules.validate(new CreateJobRequest(
                "recap the latest mystery release",
                VideoType.RECAP,
                Orientation.PORTRAIT_9_16,
                DurationPreset.ONE_MINUTE,
                PublishTarget.YOUTUBE_SHORT,
                VoiceProvider.OPENAI_TTS,
                MediaStyle.CINEMATIC_RECAP,
                ContentCategory.MOVIES_AND_SERIES,
                EditorialWindow.WEEK,
                ResearchFocus.TRENDING,
                8,
                VisualSource.SOURCE_SCREENSHOTS,
                "https://example.com/show",
                "Example Show",
                sourceScope,
                seasonNumber,
                episodeNumber,
                null,
                List.of(),
                List.of(),
                Map.of()
        ));
    }
}
