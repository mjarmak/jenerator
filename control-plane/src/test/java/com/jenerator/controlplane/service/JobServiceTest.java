package com.jenerator.controlplane.service;

import com.jenerator.common.dto.CreateJobRequest;
import com.jenerator.common.model.ContentCategory;
import com.jenerator.common.model.DurationPreset;
import com.jenerator.common.model.EditorialWindow;
import com.jenerator.common.model.JobStatus;
import com.jenerator.common.model.MediaStyle;
import com.jenerator.common.model.Orientation;
import com.jenerator.common.model.PublishTarget;
import com.jenerator.common.model.VideoType;
import com.jenerator.common.model.VisualSource;
import com.jenerator.common.model.VoiceProvider;
import com.jenerator.common.validation.JobValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobServiceTest {
    private final JobService service = new JobService(new JobMapper());

    @Test
    void createsValidPortraitShortJob() {
        var response = service.create(request(Orientation.PORTRAIT_9_16, PublishTarget.YOUTUBE_SHORT));

        assertThat(response.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(response.width()).isEqualTo(1080);
        assertThat(response.height()).isEqualTo(1920);
        assertThat(response.targetSeconds()).isEqualTo(58);
    }

    @Test
    void rejectsLandscapeShortJob() {
        assertThatThrownBy(() -> service.create(request(Orientation.LANDSCAPE_16_9, PublishTarget.YOUTUBE_SHORT)))
                .isInstanceOf(JobValidationException.class);
    }

    @Test
    void workerClaimsOldestQueuedJob() {
        var created = service.create(request(Orientation.LANDSCAPE_16_9, PublishTarget.YOUTUBE_VIDEO));

        var claim = service.claim("worker-1");

        assertThat(claim.claimed()).isTrue();
        assertThat(claim.job().id()).isEqualTo(created.id());
        assertThat(claim.job().status()).isEqualTo(JobStatus.CLAIMED);
    }

    private CreateJobRequest request(Orientation orientation, PublishTarget publishTarget) {
        return new CreateJobRequest(
                "Create a mystery series recap",
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
                Map.of("title", "Example")
        );
    }
}
