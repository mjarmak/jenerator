package com.jenerator.worker.pipeline;

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
import com.jenerator.worker.config.WorkerProperties;
import com.jenerator.worker.model.GeneratedScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CaptionServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void createsAssCaptionTrackForPortraitJob() throws Exception {
        CaptionService service = new CaptionService(properties());
        RenderPresetService presetService = new RenderPresetService();
        JobResponse job = job(Orientation.PORTRAIT_9_16, PublishTarget.YOUTUBE_SHORT);
        var preset = presetService.forJob(job);
        var script = new GeneratedScript(
                "Mystery Recap",
                "A sharp hook opens the story before the key reveal changes every character motive and sets up the final recommendation.",
                "description",
                58
        );

        var track = service.create(job, script, preset);

        assertThat(track.cues()).isNotEmpty();
        assertThat(track.path()).exists();
        String ass = Files.readString(track.path());
        assertThat(ass).contains("[Script Info]", "[Events]", "PlayResX: 1080", "Dialogue:");
    }

    private WorkerProperties properties() {
        return new WorkerProperties(
                true,
                "test-worker",
                "http://localhost:8080",
                null,
                null,
                1000,
                tempDir,
                "ffmpeg",
                null
        );
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
