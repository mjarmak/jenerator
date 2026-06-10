package com.jenerator.worker.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jenerator.common.dto.JobArtifactResponse;
import com.jenerator.common.dto.JobResponse;
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
import com.jenerator.worker.config.YoutubeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YoutubeUploadServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void usesLocalPreviewWhenItExists() throws Exception {
        ControlPlaneClient client = mock(ControlPlaneClient.class);
        YoutubeUploadService service = service(client);
        Path preview = tempDir.resolve("job-1").resolve("preview.mp4");
        Files.createDirectories(preview.getParent());
        Files.write(preview, new byte[] { 1, 2, 3 });

        Path resolved = service.resolveRenderedVideo(job(List.of(previewArtifact())));

        assertThat(resolved).isEqualTo(preview);
        verify(client, never()).downloadArtifact(any(), any());
    }

    @Test
    void downloadsPreviewArtifactWhenLocalPreviewIsMissing() throws Exception {
        ControlPlaneClient client = mock(ControlPlaneClient.class);
        YoutubeUploadService service = service(client);
        Path downloaded = tempDir.resolve("job-1").resolve("downloaded-artifacts").resolve("artifact-preview.mp4");
        when(client.downloadArtifact(any(), any())).thenReturn(downloaded);

        Path resolved = service.resolveRenderedVideo(job(List.of(previewArtifact())));

        assertThat(resolved).isEqualTo(downloaded);
        verify(client).downloadArtifact(previewArtifact(), tempDir.resolve("job-1").resolve("downloaded-artifacts"));
    }

    @Test
    void dryRunUploadReturnsPayloadPathForControlPlaneArtifactStorage() throws Exception {
        YoutubeUploadService service = service(mock(ControlPlaneClient.class));

        var result = service.upload(job(List.of()));

        assertThat(result.dryRun()).isTrue();
        assertThat(result.artifactPath()).exists();
        assertThat(result.artifactPath().getFileName().toString()).isEqualTo("youtube-upload-dry-run.json");
        assertThat(Files.readString(result.artifactPath())).contains("\"privacyStatus\" : \"private\"");
    }

    @Test
    void normalizesYoutubeMetadataToUploadLimits() {
        YoutubeUploadService service = service(mock(ControlPlaneClient.class));
        JobResponse job = jobWithMetadata(
                "A".repeat(140) + "<bad>",
                "Intro <bad>\n" + "description ".repeat(700),
                List.of(
                        "movies",
                        "movie recaps",
                        "#movie,news",
                        "x".repeat(480),
                        "another tag"
                )
        );

        var metadata = service.metadata(job);

        assertThat(metadata.title()).hasSize(100).doesNotContain("<", ">");
        assertThat(metadata.description().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(5_000);
        assertThat(metadata.description()).doesNotContain("<", ">");
        assertThat(tagCost(metadata.tags())).isLessThanOrEqualTo(500);
        assertThat(metadata.tags()).contains("movies", "movie recaps", "movie news");
    }

    @Test
    void apiModeCanBeConfiguredWithDesktopOAuthCredentials() {
        YoutubeUploadService service = service(
                mock(ControlPlaneClient.class),
                new YoutubeProperties(YoutubeProperties.UploadMode.API, "client-id", "", "refresh-token", List.of(), "private")
        );

        assertThat(service.configured()).isTrue();
    }

    private YoutubeUploadService service(ControlPlaneClient client) {
        return service(client, new YoutubeProperties(YoutubeProperties.UploadMode.DRY_RUN, "", "", "", List.of(), "private"));
    }

    private YoutubeUploadService service(ControlPlaneClient client, YoutubeProperties properties) {
        return new YoutubeUploadService(
                properties,
                new WorkerProperties(true, "worker", "http://localhost:8080", "", "", 1000, tempDir, "ffmpeg", ""),
                client,
                new ObjectMapper()
        );
    }

    private JobArtifactResponse previewArtifact() {
        return new JobArtifactResponse(
                "artifact-1",
                "PREVIEW_VIDEO",
                "preview.mp4",
                "/api/jobs/job-1/artifacts/artifact-1/file",
                123,
                Instant.parse("2026-06-10T12:00:00Z")
        );
    }

    private JobResponse job(List<JobArtifactResponse> artifacts) {
        return jobWithMetadata("Title", "Description", List.of(), artifacts);
    }

    private JobResponse jobWithMetadata(String title, String description, List<String> tags) {
        return jobWithMetadata(title, description, tags, List.of());
    }

    private JobResponse jobWithMetadata(String title, String description, List<String> tags, List<JobArtifactResponse> artifacts) {
        return new JobResponse(
                "job-1",
                "prompt",
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
                "https://example.com/source",
                "Example Show",
                SourceScope.SERIES_EPISODE,
                1,
                2,
                null,
                List.of(),
                List.of(),
                Map.of(),
                JobStatus.WAITING_FOR_APPROVAL,
                Orientation.PORTRAIT_9_16.width(),
                Orientation.PORTRAIT_9_16.height(),
                DurationPreset.ONE_MINUTE.targetSeconds(),
                title,
                description,
                tags,
                List.of(),
                false,
                true,
                null,
                Instant.now(),
                Instant.now(),
                List.of(),
                artifacts
        );
    }

    private int tagCost(List<String> tags) {
        int total = 0;
        for (int index = 0; index < tags.size(); index += 1) {
            if (index > 0) {
                total += 1;
            }
            String tag = tags.get(index);
            total += tag.length();
            if (tag.contains(" ")) {
                total += 2;
            }
        }
        return total;
    }
}
