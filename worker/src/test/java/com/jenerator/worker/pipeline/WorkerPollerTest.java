package com.jenerator.worker.pipeline;

import com.jenerator.common.dto.JobResponse;
import com.jenerator.common.dto.UpdateJobStepRequest;
import com.jenerator.common.dto.WorkerClaimResponse;
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
import com.jenerator.worker.client.ControlPlaneClient;
import com.jenerator.worker.config.WorkerProperties;
import com.jenerator.worker.model.ResearchBrief;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerPollerTest {
    @Mock
    private ControlPlaneClient controlPlaneClient;
    @Mock
    private WorkerReadinessService readinessService;
    @Mock
    private ResearchService researchService;
    @Mock
    private ScriptDraftService scriptDraftService;
    @Mock
    private NarrationService narrationService;
    @Mock
    private MediaPreparationService mediaPreparationService;
    @Mock
    private RenderPresetService renderPresetService;
    @Mock
    private CaptionService captionService;
    @Mock
    private FfmpegRenderService ffmpegRenderService;
    @Mock
    private YoutubeUploadService youtubeUploadService;

    private WorkerPoller poller;

    @BeforeEach
    void setUp() {
        poller = new WorkerPoller(
                new WorkerProperties(true, "worker", "http://control-plane", "token", null, 1000, Path.of("workspace"), "ffmpeg", null),
                controlPlaneClient,
                readinessService,
                researchService,
                scriptDraftService,
                narrationService,
                mediaPreparationService,
                renderPresetService,
                captionService,
                ffmpegRenderService,
                youtubeUploadService
        );
        when(controlPlaneClient.ensureToken()).thenReturn(Optional.of("token"));
        when(readinessService.ffmpegAvailable()).thenReturn(true);
        when(readinessService.qwenAvailable()).thenReturn(false);
        when(youtubeUploadService.configured()).thenReturn(true);
    }

    @Test
    void stopsRenderWithoutFailingWhenJobIsCancelledBetweenStages() throws Exception {
        JobResponse claimed = job(JobStatus.CLAIMED);
        when(controlPlaneClient.claimJob()).thenReturn(new WorkerClaimResponse(true, claimed));
        when(controlPlaneClient.getJob(claimed.id()))
                .thenReturn(job(JobStatus.RUNNING), job(JobStatus.CANCELLED));
        when(researchService.research(claimed))
                .thenReturn(new ResearchBrief("Research ready.", List.of(), List.of(), Path.of("research.json")));

        poller.poll();

        verify(controlPlaneClient).updateStep(eq(claimed.id()), argThat(this::isResearchRunning));
        verify(controlPlaneClient, never()).addArtifactFile(eq(claimed.id()), anyString(), any());
        verify(scriptDraftService, never()).draft(any(), any());
        verify(controlPlaneClient, never()).updateStep(eq(claimed.id()), argThat(this::isFailedUpdate));
    }

    @Test
    void skipsYoutubeUploadWhenApprovedJobWasCancelledBeforeUploadStarts() throws Exception {
        JobResponse uploading = job(JobStatus.UPLOADING);
        when(controlPlaneClient.claimJob()).thenReturn(new WorkerClaimResponse(true, uploading));
        when(controlPlaneClient.getJob(uploading.id())).thenReturn(job(JobStatus.CANCELLED));

        poller.poll();

        verify(youtubeUploadService, never()).upload(any());
        verify(controlPlaneClient, never()).updateStep(eq(uploading.id()), argThat(this::isFailedUpdate));
    }

    private boolean isResearchRunning(UpdateJobStepRequest request) {
        return request != null
                && "RESEARCH".equals(request.stepName())
                && "RUNNING".equals(request.stepStatus())
                && request.jobStatus() == JobStatus.RUNNING;
    }

    private boolean isFailedUpdate(UpdateJobStepRequest request) {
        return request != null && request.jobStatus() == JobStatus.FAILED;
    }

    private JobResponse job(JobStatus status) {
        Instant now = Instant.now();
        return new JobResponse(
                "job-1",
                "Create a weekly movie recap",
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
                VisualSource.AI_GENERATED_IMAGES,
                null,
                "Example show",
                SourceScope.SERIES_SHOW,
                null,
                null,
                null,
                List.of(),
                List.of(),
                Map.of(),
                status,
                1080,
                1920,
                60,
                "Example title",
                "Example description",
                List.of(),
                List.of(),
                false,
                true,
                null,
                now,
                now,
                List.of(),
                List.of()
        );
    }
}
