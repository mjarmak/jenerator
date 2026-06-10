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
import com.jenerator.worker.config.ResearchProperties;
import com.jenerator.worker.config.WorkerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void writesResearchBriefWithoutExternalToken() throws Exception {
        ResearchService service = service(new ResearchProperties("", "en-US", "week"));

        var brief = service.research(job());

        assertThat(brief.trendingTitles()).isEmpty();
        assertThat(brief.citations()).containsExactly("https://example.com/source");
        assertThat(brief.artifactPath()).exists();
        String artifact = Files.readString(brief.artifactPath());
        assertThat(artifact).contains("\"videoType\" : \"TOP_LIST\"", "\"researchFocus\" : \"TRENDING\"", "\"trendingTitles\" : [ ]");
        assertThat(brief.summary()).contains("Editorial target: MOVIES_AND_SERIES over WEEK with trending");
    }

    @Test
    void normalizesTmdbTrendingWindowToAllowedValues() {
        assertThat(service(new ResearchProperties("token", "en-US", "day")).tmdbTrendingWindow()).isEqualTo("day");
        assertThat(service(new ResearchProperties("token", "en-US", "week")).tmdbTrendingWindow()).isEqualTo("week");
        assertThat(service(new ResearchProperties("token", "en-US", "month")).tmdbTrendingWindow()).isEqualTo("week");
    }

    private ResearchService service(ResearchProperties properties) {
        return new ResearchService(
                properties,
                new WorkerProperties(true, "worker", "http://localhost:8080", "", "", 1000, tempDir, "ffmpeg", ""),
                new ObjectMapper()
        );
    }

    private JobResponse job() {
        return new JobResponse(
                "job-1",
                "Find the most watchable new releases.",
                VideoType.TOP_LIST,
                Orientation.PORTRAIT_9_16,
                DurationPreset.ONE_MINUTE,
                PublishTarget.YOUTUBE_SHORT,
                VoiceProvider.OPENAI_TTS,
                MediaStyle.TOP_LIST_COUNTDOWN,
                ContentCategory.MOVIES_AND_SERIES,
                EditorialWindow.WEEK,
                ResearchFocus.TRENDING,
                5,
                VisualSource.AI_GENERATED_IMAGES,
                "https://example.com/source",
                "Example Show",
                SourceScope.SERIES_SHOW,
                null,
                null,
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
