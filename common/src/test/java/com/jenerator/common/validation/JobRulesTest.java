package com.jenerator.common.validation;

import com.jenerator.common.dto.CreateJobRequest;
import com.jenerator.common.model.ContentCategory;
import com.jenerator.common.model.DurationPreset;
import com.jenerator.common.model.EditorialWindow;
import com.jenerator.common.model.MediaStyle;
import com.jenerator.common.model.Orientation;
import com.jenerator.common.model.PublishTarget;
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
                8,
                VisualSource.SOURCE_SCREENSHOTS,
                "https://example.com/show",
                "Example Show",
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
                8,
                visualSource,
                sourceUrl,
                "Example Show",
                1,
                1,
                null,
                visualAssetIds,
                visualAssetIds,
                Map.of()
        );
    }
}
