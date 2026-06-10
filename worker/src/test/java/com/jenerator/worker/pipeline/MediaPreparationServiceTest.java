package com.jenerator.worker.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.jenerator.worker.config.BrowserAutomationProperties;
import com.jenerator.worker.config.ImageProperties;
import com.jenerator.worker.config.WorkerProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MediaPreparationServiceTest {
    @Test
    void generatedImagePromptsVaryBySceneAndPreserveTextFreeInstruction() {
        var service = service(new ImageProperties("key", "gpt-image-2", "low", "png", 4, true));

        String first = service.imagePrompt(job(), 1, 4);
        String third = service.imagePrompt(job(), 3, 4);

        assertThat(first).contains("Scene 1 of 4", "wide establishing composition", "Do not include readable text");
        assertThat(third).contains("Scene 3 of 4", "dynamic conflict or discovery moment");
        assertThat(first).isNotEqualTo(third);
    }

    @Test
    void imagePropertyGeneratedCountIsClamped() {
        assertThat(new ImageProperties("", "", "", "", 0, true).generatedCount()).isEqualTo(4);
        assertThat(new ImageProperties("", "", "", "", 99, true).generatedCount()).isEqualTo(8);
        assertThat(new ImageProperties("", "", "", "", 2, true).generatedCount()).isEqualTo(2);
    }

    @Test
    void sourceCaptureCommandIncludesPersistentBrowserProfileOptions() {
        var browserProperties = new BrowserAutomationProperties(
                List.of("node", "capture.mjs"),
                8,
                2,
                5,
                45,
                true,
                "ArrowRight",
                3,
                "C:/Projects/jenerator/data/source-browser-profile",
                "chrome",
                false
        );
        var service = service(new ImageProperties("", "", "", "", 4, true), browserProperties);

        List<String> command = service.sourceCaptureCommand(job(), Path.of("workspace/job-1/captured-visuals"));

        assertThat(command).containsSubsequence("--user-data-dir", "C:/Projects/jenerator/data/source-browser-profile");
        assertThat(command).containsSubsequence("--channel", "chrome");
        assertThat(command).containsSubsequence("--headless", "false");
        assertThat(command).containsSubsequence("--auto-play", "true");
        assertThat(command).containsSubsequence("--skip-key", "ArrowRight", "--skip-count", "3");
    }

    private MediaPreparationService service(ImageProperties imageProperties) {
        return service(imageProperties, new BrowserAutomationProperties(List.of(), 6, 0, 4, 0, false, "", 0, "", "", false));
    }

    private MediaPreparationService service(ImageProperties imageProperties, BrowserAutomationProperties browserProperties) {
        return new MediaPreparationService(
                new WorkerProperties(true, "worker", "http://localhost:8080", "", "", 1000, Path.of("workspace"), "ffmpeg", ""),
                imageProperties,
                browserProperties,
                mock(ControlPlaneClient.class),
                new ObjectMapper()
        );
    }

    private JobResponse job() {
        return new JobResponse(
                "job-1",
                "Create a spoiler-light recap with current hype.",
                VideoType.RECAP,
                Orientation.PORTRAIT_9_16,
                DurationPreset.ONE_MINUTE,
                PublishTarget.YOUTUBE_SHORT,
                VoiceProvider.OPENAI_TTS,
                MediaStyle.CINEMATIC_RECAP,
                ContentCategory.MOVIES_AND_SERIES,
                EditorialWindow.WEEK,
                ResearchFocus.TRENDING,
                5,
                VisualSource.AI_GENERATED_IMAGES,
                "https://example.com/source",
                "Example Show",
                SourceScope.SERIES_EPISODE,
                1,
                2,
                null,
                List.of(),
                List.of(),
                Map.of(),
                JobStatus.QUEUED,
                Orientation.PORTRAIT_9_16.width(),
                Orientation.PORTRAIT_9_16.height(),
                DurationPreset.ONE_MINUTE.targetSeconds(),
                null,
                null,
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
