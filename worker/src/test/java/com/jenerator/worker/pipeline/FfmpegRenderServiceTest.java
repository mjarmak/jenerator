package com.jenerator.worker.pipeline;

import com.jenerator.worker.config.WorkerProperties;
import com.jenerator.worker.model.AudioTrack;
import com.jenerator.worker.model.GeneratedScript;
import com.jenerator.worker.model.MediaPlan;
import com.jenerator.worker.model.RenderPreset;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FfmpegRenderServiceTest {
    private final FfmpegRenderService service = new FfmpegRenderService(
            new WorkerProperties(true, "worker", "http://localhost:8080", "", "", 1000, Path.of("workspace"), "ffmpeg", ""),
            null
    );

    @Test
    void buildsSlideshowFromMultipleImageVisuals() {
        var command = service.buildCommand(
                Path.of("preview.mp4"),
                new GeneratedScript("Episode Recap", "Narration", "Description", 12),
                new RenderPreset(1080, 1920, 12, 30, 64, "fast", ""),
                new AudioTrack(Path.of("voice.wav"), "NARRATION_AUDIO", "voice ready"),
                new MediaPlan(
                        Path.of("shot-1.png"),
                        List.of(Path.of("shot-1.png"), Path.of("shot-2.jpg"), Path.of("shot-3.webp")),
                        Path.of("music.mp3"),
                        null
                ),
                null
        );

        String filters = filters(command);

        assertThat(command.stream().filter("-i"::equals).count()).isEqualTo(5);
        assertThat(command).containsSubsequence("-loop", "1", "-framerate", "30", "-t", "4", "-i", "shot-1.png");
        assertThat(filters).contains("concat=n=3:v=1:a=0");
        assertThat(filters).contains("[3:a]volume=1.0[narr]");
        assertThat(filters).contains("[4:a]volume=0.18");
    }

    @Test
    void keepsSingleVideoVisualAsLoopedBackground() {
        var command = service.buildCommand(
                Path.of("preview.mp4"),
                new GeneratedScript("Movie Recap", "Narration", "Description", 58),
                new RenderPreset(1920, 1080, 58, 30, 48, "wide", ""),
                new AudioTrack(Path.of("voice.wav"), "NARRATION_AUDIO", "voice ready"),
                new MediaPlan(Path.of("clip.mp4"), List.of(Path.of("clip.mp4")), null, null),
                null
        );

        String filters = filters(command);

        assertThat(command.stream().filter("-i"::equals).count()).isEqualTo(2);
        assertThat(command).containsSubsequence("-stream_loop", "-1", "-i", "clip.mp4");
        assertThat(filters).doesNotContain("concat=n=");
        assertThat(filters).contains("[1:a]anull[aout]");
    }

    private String filters(List<String> command) {
        int filterIndex = command.indexOf("-filter_complex");
        assertThat(filterIndex).isGreaterThanOrEqualTo(0);
        return command.get(filterIndex + 1);
    }
}
