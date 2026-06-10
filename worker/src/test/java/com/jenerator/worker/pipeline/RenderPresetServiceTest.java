package com.jenerator.worker.pipeline;

import com.jenerator.common.dto.JobResponse;
import com.jenerator.common.model.DurationPreset;
import com.jenerator.common.model.JobStatus;
import com.jenerator.common.model.MediaStyle;
import com.jenerator.common.model.Orientation;
import com.jenerator.common.model.PublishTarget;
import com.jenerator.common.model.VideoType;
import com.jenerator.common.model.VisualSource;
import com.jenerator.common.model.VoiceProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RenderPresetServiceTest {
    private final RenderPresetService service = new RenderPresetService();

    @Test
    void buildsPortraitPreset() {
        var preset = service.forJob(job(Orientation.PORTRAIT_9_16, PublishTarget.YOUTUBE_SHORT));

        assertThat(preset.width()).isEqualTo(1080);
        assertThat(preset.height()).isEqualTo(1920);
        assertThat(preset.captionFontSize()).isEqualTo(64);
    }

    @Test
    void buildsLandscapePreset() {
        var preset = service.forJob(job(Orientation.LANDSCAPE_16_9, PublishTarget.YOUTUBE_VIDEO));

        assertThat(preset.width()).isEqualTo(1920);
        assertThat(preset.height()).isEqualTo(1080);
        assertThat(preset.captionFontSize()).isEqualTo(48);
    }

    private JobResponse job(Orientation orientation, PublishTarget target) {
        return new JobResponse(
                "job-1",
                "prompt",
                VideoType.RECAP,
                orientation,
                DurationPreset.ONE_MINUTE,
                target,
                VoiceProvider.OPENAI_TTS,
                MediaStyle.CINEMATIC_RECAP,
                VisualSource.SOURCE_SCREENSHOTS,
                "https://example.com/show",
                "Example Show",
                1,
                1,
                null,
                List.of(),
                List.of(),
                Map.of(),
                JobStatus.QUEUED,
                orientation.width(),
                orientation.height(),
                DurationPreset.ONE_MINUTE.targetSeconds(),
                "Title",
                "Description",
                List.of(),
                List.of(),
                false,
                true,
                null,
                Instant.now(),
                Instant.now(),
                List.of(),
                List.of()
        );
    }
}
