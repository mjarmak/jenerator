package com.jenerator.worker.pipeline;

import com.jenerator.common.dto.JobResponse;
import com.jenerator.common.dto.UpdateJobReviewRequest;
import com.jenerator.common.dto.UpdateJobArtifactRequest;
import com.jenerator.common.dto.UpdateJobStepRequest;
import com.jenerator.common.dto.WorkerHeartbeatRequest;
import com.jenerator.common.model.JobStatus;
import com.jenerator.worker.client.ControlPlaneClient;
import com.jenerator.worker.config.WorkerProperties;
import com.jenerator.worker.model.AudioTrack;
import com.jenerator.worker.model.CaptionTrack;
import com.jenerator.worker.model.GeneratedScript;
import com.jenerator.worker.model.MediaPlan;
import com.jenerator.worker.model.RenderArtifact;
import com.jenerator.worker.model.RenderPreset;
import com.jenerator.worker.model.ResearchBrief;
import com.jenerator.worker.model.YoutubeUploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
public class WorkerPoller {
    private static final Logger log = LoggerFactory.getLogger(WorkerPoller.class);

    private final WorkerProperties properties;
    private final ControlPlaneClient controlPlaneClient;
    private final WorkerReadinessService readinessService;
    private final ResearchService researchService;
    private final ScriptDraftService scriptDraftService;
    private final NarrationService narrationService;
    private final MediaPreparationService mediaPreparationService;
    private final RenderPresetService renderPresetService;
    private final CaptionService captionService;
    private final FfmpegRenderService ffmpegRenderService;
    private final YoutubeUploadService youtubeUploadService;

    public WorkerPoller(
            WorkerProperties properties,
            ControlPlaneClient controlPlaneClient,
            WorkerReadinessService readinessService,
            ResearchService researchService,
            ScriptDraftService scriptDraftService,
            NarrationService narrationService,
            MediaPreparationService mediaPreparationService,
            RenderPresetService renderPresetService,
            CaptionService captionService,
            FfmpegRenderService ffmpegRenderService,
            YoutubeUploadService youtubeUploadService
    ) {
        this.properties = properties;
        this.controlPlaneClient = controlPlaneClient;
        this.readinessService = readinessService;
        this.researchService = researchService;
        this.scriptDraftService = scriptDraftService;
        this.narrationService = narrationService;
        this.mediaPreparationService = mediaPreparationService;
        this.renderPresetService = renderPresetService;
        this.captionService = captionService;
        this.ffmpegRenderService = ffmpegRenderService;
        this.youtubeUploadService = youtubeUploadService;
    }

    @Scheduled(fixedDelayString = "${jenerator.worker.poll-delay-ms:15000}")
    public void poll() {
        if (!properties.enabled()) {
            return;
        }
        try {
            if (controlPlaneClient.ensureToken().isEmpty()) {
                log.warn("Worker has no token. Configure jenerator.worker.token or pairing-code.");
                return;
            }
            heartbeat();
            var claim = controlPlaneClient.claimJob();
            if (!claim.claimed() || claim.job() == null) {
                return;
            }
            if (claim.job().status() == JobStatus.CLAIMED) {
                renderJob(claim.job());
            } else if (claim.job().status() == JobStatus.UPLOADING) {
                uploadJob(claim.job());
            }
        } catch (Exception exception) {
            log.error("Worker polling failed", exception);
        }
    }

    private void heartbeat() {
        controlPlaneClient.heartbeat(new WorkerHeartbeatRequest(
                properties.name(),
                readinessService.ffmpegAvailable(),
                readinessService.qwenAvailable(),
                youtubeUploadService.configured(),
                Map.of("workspace", properties.workspacePath().toString())
        ));
    }

    private void renderJob(JobResponse job) {
        try {
            ensureActive(job, "research");
            update(job, "RESEARCH", "RUNNING", "Building source and trending research brief.", JobStatus.RUNNING);
            ResearchBrief research = researchService.research(job);
            ensureActive(job, "research artifact upload");
            controlPlaneClient.addArtifactFile(job.id(), "RESEARCH_BRIEF", research.artifactPath());
            update(job, "RESEARCH", "DONE", research.summary(), JobStatus.RUNNING);

            ensureActive(job, "script drafting");
            update(job, "SCRIPT", "RUNNING", "Drafting script and metadata.", JobStatus.RUNNING);
            GeneratedScript script = scriptDraftService.draft(job, research);
            Path scriptArtifact = writeScriptArtifact(job, script);
            ensureActive(job, "script artifact upload");
            controlPlaneClient.addArtifactFile(job.id(), "SCRIPT_DRAFT", scriptArtifact);
            controlPlaneClient.updateReview(job.id(), new UpdateJobReviewRequest(
                    script.title(),
                    script.description(),
                    tags(job, research),
                    research.citations(),
                    false,
                    true
            ));
            update(job, "SCRIPT", "DONE", "Script drafted for the selected duration.", JobStatus.RUNNING);

            ensureActive(job, "voice generation");
            update(job, "VOICE", "RUNNING", "Generating narration audio with " + job.voiceProvider() + ".", JobStatus.RUNNING);
            AudioTrack audio = narrationService.generate(job, script);
            ensureActive(job, "voice artifact upload");
            controlPlaneClient.addArtifactFile(job.id(), audio.type(), audio.path());
            update(job, "VOICE", "DONE", audio.message(), JobStatus.RUNNING);

            ensureActive(job, "media preparation");
            update(job, "MEDIA", "RUNNING", "Preparing visuals and background music.", JobStatus.RUNNING);
            MediaPlan media = mediaPreparationService.prepare(job);
            ensureActive(job, "media artifact upload");
            if (media.manifestPath() != null) {
                controlPlaneClient.addArtifactFile(job.id(), "VISUAL_CAPTURE_PLAN", media.manifestPath());
            }
            if (media.downloadedVisuals() != null) {
                media.downloadedVisuals()
                        .stream()
                        .filter(path -> path.getFileName().toString().startsWith("generated-visual"))
                        .forEach(path -> controlPlaneClient.addArtifactFile(job.id(), "GENERATED_VISUAL", path));
            }
            update(job, "MEDIA", "DONE", mediaSummary(media), JobStatus.RUNNING);

            ensureActive(job, "render preset");
            update(job, "RENDER_PRESET", "RUNNING", "Preparing orientation and duration preset.", JobStatus.RUNNING);
            RenderPreset preset = renderPresetService.forJob(job);
            update(job, "RENDER_PRESET", "DONE", preset.width() + "x" + preset.height() + " at " + preset.durationSeconds() + " seconds.", JobStatus.RUNNING);

            ensureActive(job, "caption timing");
            update(job, "CAPTIONS", "RUNNING", "Timing narration captions.", JobStatus.RUNNING);
            CaptionTrack captions = captionService.create(job, script, preset);
            ensureActive(job, "caption artifact upload");
            controlPlaneClient.addArtifactFile(job.id(), "CAPTIONS_ASS", captions.path());
            update(job, "CAPTIONS", "DONE", captions.cues().size() + " caption cue(s) generated.", JobStatus.RUNNING);

            ensureActive(job, "FFmpeg render");
            update(job, "RENDER", "RUNNING", "Rendering preview with FFmpeg.", JobStatus.RUNNING);
            RenderArtifact artifact = ffmpegRenderService.render(job, script, preset, audio, media, captions);
            ensureActive(job, "preview artifact upload");
            controlPlaneClient.addArtifactFile(job.id(), artifact.type(), artifact.path());
            update(job, "RENDER", "DONE", "Preview artifact is ready.", JobStatus.WAITING_FOR_APPROVAL);
        } catch (JobStoppedException exception) {
            log.info("Stopped job {}: {}", job.id(), exception.getMessage());
        } catch (Exception exception) {
            if (isCancelled(job)) {
                log.info("Stopped job {} after cancellation while handling exception: {}", job.id(), exception.getMessage());
                return;
            }
            update(job, "FAILED", "ERROR", exception.getMessage(), JobStatus.FAILED);
        }
    }

    private void uploadJob(JobResponse job) {
        try {
            ensureActive(job, "YouTube upload");
            update(job, "UPLOAD", "RUNNING", "Uploading approved video.", JobStatus.UPLOADING);
            YoutubeUploadResult result = youtubeUploadService.upload(job);
            ensureActive(job, "YouTube upload artifact");
            String artifactType = result.dryRun() ? "YOUTUBE_DRY_RUN" : "YOUTUBE_UPLOAD";
            if (result.artifactPath() != null) {
                controlPlaneClient.addArtifactFile(job.id(), artifactType, result.artifactPath());
            } else {
                controlPlaneClient.addArtifact(job.id(), new UpdateJobArtifactRequest(
                        artifactType,
                        result.videoId() + ".json",
                        result.url(),
                        0
                ));
            }
            update(job, "UPLOAD", "DONE", result.dryRun() ? "Dry-run upload payload created." : "Uploaded to YouTube.", JobStatus.COMPLETED);
        } catch (JobStoppedException exception) {
            log.info("Stopped upload for job {}: {}", job.id(), exception.getMessage());
        } catch (Exception exception) {
            if (isCancelled(job)) {
                log.info("Stopped upload for job {} after cancellation while handling exception: {}", job.id(), exception.getMessage());
                return;
            }
            update(job, "UPLOAD", "ERROR", exception.getMessage(), JobStatus.FAILED);
        }
    }

    private void update(JobResponse job, String step, String status, String message, JobStatus jobStatus) {
        controlPlaneClient.updateStep(job.id(), new UpdateJobStepRequest(step, status, message, jobStatus));
    }

    private String mediaSummary(MediaPlan media) {
        int visuals = media.downloadedVisuals() == null ? 0 : media.downloadedVisuals().size();
        String music = media.musicPath() == null ? "no music" : "music ready";
        String visual = media.visualPath() == null ? "fallback visual" : "visual ready";
        return visual + ", " + visuals + " prepared visual asset(s), " + music + ".";
    }

    private java.util.List<String> tags(JobResponse job, ResearchBrief research) {
        java.util.List<String> tags = new java.util.ArrayList<>();
        tags.add("movies");
        tags.add("series");
        tags.add(job.videoType().name().toLowerCase());
        tags.add(job.contentCategory().name().toLowerCase());
        tags.add(job.editorialWindow().name().toLowerCase());
        if (job.researchFocus() != null) {
            tags.add(job.researchFocus().name().toLowerCase());
        }
        if (job.sourceTitle() != null && !job.sourceTitle().isBlank()) {
            tags.add(job.sourceTitle());
        }
        if (research.trendingTitles() != null) {
            research.trendingTitles().stream().limit(3).forEach(tags::add);
        }
        return tags.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(12)
                .toList();
    }

    private Path writeScriptArtifact(JobResponse job, GeneratedScript script) throws IOException {
        Path jobDirectory = properties.workspacePath().resolve(job.id());
        Files.createDirectories(jobDirectory);
        Path path = jobDirectory.resolve("script-draft.json");
        String body = """
                {
                  "title": %s,
                  "description": %s,
                  "estimatedSeconds": %d,
                  "narration": %s
                }
                """.formatted(
                jsonString(script.title()),
                jsonString(script.description()),
                script.estimatedSeconds(),
                jsonString(script.narration())
        );
        Files.writeString(path, body);
        return path;
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t") + "\"";
    }

    private void ensureActive(JobResponse job, String nextAction) {
        JobStatus status = controlPlaneClient.getJob(job.id()).status();
        if (status == JobStatus.CANCELLED) {
            throw new JobStoppedException("cancelled before " + nextAction + ".");
        }
        if (status == JobStatus.FAILED || status == JobStatus.COMPLETED) {
            throw new JobStoppedException("already " + status + " before " + nextAction + ".");
        }
    }

    private boolean isCancelled(JobResponse job) {
        try {
            return controlPlaneClient.getJob(job.id()).status() == JobStatus.CANCELLED;
        } catch (Exception exception) {
            log.warn("Could not re-check cancellation state for job {}", job.id(), exception);
            return false;
        }
    }

    private static class JobStoppedException extends RuntimeException {
        JobStoppedException(String message) {
            super(message);
        }
    }
}
