package com.jenerator.controlplane.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.jenerator.common.dto.CreateJobRequest;
import com.jenerator.common.dto.UpdateJobArtifactRequest;
import com.jenerator.common.dto.UpdateJobReviewRequest;
import com.jenerator.common.dto.UpdateJobStepRequest;
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
import com.jenerator.common.validation.JobValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobServiceTest {
    private final JobService service = new JobService(new JobMapper());

    @TempDir
    Path tempDir;

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

    @Test
    void workerWithoutFfmpegDoesNotClaimRenderJob() {
        var created = service.create(request(Orientation.LANDSCAPE_16_9, PublishTarget.YOUTUBE_VIDEO));

        var claim = service.claim("worker-1", false, true);

        assertThat(claim.claimed()).isFalse();
        assertThat(service.get(created.id()).status()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void workerWithoutYoutubeDoesNotClaimApprovedUpload() {
        var created = service.create(request(Orientation.PORTRAIT_9_16, PublishTarget.YOUTUBE_SHORT));
        approveForUpload(created.id());

        var claim = service.claim("worker-1", true, false);

        assertThat(claim.claimed()).isFalse();
        assertThat(service.get(created.id()).status()).isEqualTo(JobStatus.APPROVED_FOR_UPLOAD);
    }

    @Test
    void uploadWorkerCanClaimApprovedUploadBehindQueuedRenderJob() {
        var queuedRender = service.create(request(Orientation.LANDSCAPE_16_9, PublishTarget.YOUTUBE_VIDEO));
        var uploadReady = service.create(request(Orientation.PORTRAIT_9_16, PublishTarget.YOUTUBE_SHORT));
        approveForUpload(uploadReady.id());

        var claim = service.claim("worker-1", false, true);

        assertThat(claim.claimed()).isTrue();
        assertThat(claim.job().id()).isEqualTo(uploadReady.id());
        assertThat(claim.job().status()).isEqualTo(JobStatus.UPLOADING);
        assertThat(service.get(queuedRender.id()).status()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void persistsJobsAcrossServiceRestart() {
        ControlPlaneStateStore store = stateStore();
        JobService first = new JobService(new JobMapper(), store);
        var created = first.create(request(Orientation.PORTRAIT_9_16, PublishTarget.YOUTUBE_SHORT));

        JobService second = new JobService(new JobMapper(), store);

        assertThat(second.get(created.id()).id()).isEqualTo(created.id());
        assertThat(second.list()).hasSize(1);
    }

    @Test
    void requeuesClaimedJobsAfterRestart() {
        ControlPlaneStateStore store = stateStore();
        JobService first = new JobService(new JobMapper(), store);
        var created = first.create(request(Orientation.PORTRAIT_9_16, PublishTarget.YOUTUBE_SHORT));
        first.claim("worker-1");

        JobService second = new JobService(new JobMapper(), store);

        assertThat(second.get(created.id()).status()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void cancelsQueuedJob() {
        var created = service.create(request(Orientation.PORTRAIT_9_16, PublishTarget.YOUTUBE_SHORT));

        var cancelled = service.cancel(created.id());

        assertThat(cancelled.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(cancelled.steps()).anySatisfy(step -> assertThat(step.name()).isEqualTo("CANCELLED"));
    }

    @Test
    void cancelledJobIgnoresLateWorkerStepUpdates() {
        var created = service.create(request(Orientation.PORTRAIT_9_16, PublishTarget.YOUTUBE_SHORT));
        service.claim("worker-1");
        service.cancel(created.id());

        service.updateStep(created.id(), new UpdateJobStepRequest(
                "RENDER",
                "DONE",
                "Late worker update.",
                JobStatus.WAITING_FOR_APPROVAL
        ));

        var current = service.get(created.id());
        assertThat(current.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(current.steps()).noneSatisfy(step -> assertThat(step.name()).isEqualTo("RENDER"));
    }

    @Test
    void approvalRequiresPreviewAndReviewMetadata() {
        var created = service.create(request(Orientation.PORTRAIT_9_16, PublishTarget.YOUTUBE_SHORT));
        service.updateStep(created.id(), new UpdateJobStepRequest(
                "RENDER",
                "DONE",
                "Preview ready.",
                JobStatus.WAITING_FOR_APPROVAL
        ));

        assertThatThrownBy(() -> service.approveUpload(created.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Review a rendered preview video first")
                .hasMessageContaining("At least one tag is required")
                .hasMessageContaining("At least one citation is required");
    }

    @Test
    void approvesUploadWhenPreviewAndReviewMetadataAreReady() {
        var created = service.create(request(Orientation.PORTRAIT_9_16, PublishTarget.YOUTUBE_SHORT));
        approveForUpload(created.id());

        var approved = service.get(created.id());

        assertThat(approved.status()).isEqualTo(JobStatus.APPROVED_FOR_UPLOAD);
    }

    private void approveForUpload(String jobId) {
        service.updateStep(jobId, new UpdateJobStepRequest(
                "RENDER",
                "DONE",
                "Preview ready.",
                JobStatus.WAITING_FOR_APPROVAL
        ));
        service.addArtifact(jobId, new UpdateJobArtifactRequest(
                "PREVIEW_VIDEO",
                "preview.mp4",
                "/api/jobs/" + jobId + "/artifacts/preview/file",
                1024
        ));
        service.updateReview(jobId, new UpdateJobReviewRequest(
                "Reviewed title",
                "Reviewed description",
                List.of("movies", "recap"),
                List.of("https://example.com/source"),
                false,
                true
        ));
        service.approveUpload(jobId);
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
                Map.of("title", "Example")
        );
    }

    private ControlPlaneStateStore stateStore() {
        return new ControlPlaneStateStore(JsonMapper.builder().findAndAddModules().build(), tempDir);
    }
}
