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
import com.jenerator.worker.config.ScriptGenerationProperties;
import com.jenerator.worker.model.ResearchBrief;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptDraftServiceTest {
    private final ScriptDraftService service = new ScriptDraftService(
            new ScriptGenerationProperties("", "gpt-4.1-mini", 1800, true),
            new ObjectMapper()
    );

    @Test
    void fallbackTopListUsesTrendingTitlesAndDurationBudget() throws Exception {
        var script = service.draft(job(VideoType.TOP_LIST), research());

        assertThat(script.title()).contains("Top 5");
        assertThat(script.narration()).contains("using trending signals", "Number 1: Dune Part Two", "Number 2: Shogun");
        assertThat(wordCount(script.narration())).isLessThanOrEqualTo(116);
        assertThat(script.estimatedSeconds()).isEqualTo(58);
        assertThat(script.description()).contains("Research context (trending):", "Dune Part Two");
    }

    @Test
    void fallbackTopListUsesGrossingResearchFocus() throws Exception {
        var script = service.draft(job(VideoType.TOP_LIST, ResearchFocus.GROSSING), new ResearchBrief(
                "TMDB context says these movies are grossing this month.",
                List.of("Grossing Movie: Inside Out 2", "Popular Series: Shogun"),
                List.of("https://developer.themoviedb.org/reference/discover-movie"),
                Path.of("research-brief.json")
        ));

        assertThat(script.narration()).contains("using grossing signals", "Number 1: Inside Out 2");
        assertThat(script.narration()).doesNotContain("Grossing Movie:");
        assertThat(script.description()).contains("Research context (grossing):");
    }

    @Test
    void fallbackRecommendationHasRecommendationShape() throws Exception {
        var script = service.draft(job(VideoType.RECOMMENDATION), research());

        assertThat(script.title()).startsWith("Watch Next:");
        assertThat(script.narration()).contains("If you need something to watch next", "shortlist");
        assertThat(script.narration()).doesNotContain("Number 1:");
    }

    @Test
    void fallbackSummaryAndRecapUseDifferentNarrationShapes() throws Exception {
        var summary = service.draft(job(VideoType.SUMMARY), research());
        var recap = service.draft(job(VideoType.RECAP), research());

        assertThat(summary.title()).contains("Summary");
        assertThat(recap.title()).contains("Recap");
        assertThat(summary.narration()).contains("fast summary");
        assertThat(recap.narration()).contains("Let us recap");
    }

    @Test
    void fallbackSeasonSummaryUsesSeasonScope() throws Exception {
        var script = service.draft(job(VideoType.SUMMARY, ResearchFocus.TRENDING, SourceScope.SERIES_SEASON, 2, null), research());

        assertThat(script.narration()).contains("Example Show season 2");
        assertThat(script.narration()).doesNotContain("episode");
    }

    private ResearchBrief research() {
        return new ResearchBrief(
                "TMDB context says these titles are trending this week.",
                List.of("Trending Movie: Dune Part Two", "Trending Series: Shogun", "New Movie: Challengers"),
                List.of("https://developer.themoviedb.org/reference/discover-movie"),
                Path.of("research-brief.json")
        );
    }

    private JobResponse job(VideoType videoType) {
        return job(videoType, ResearchFocus.TRENDING);
    }

    private JobResponse job(VideoType videoType, ResearchFocus researchFocus) {
        return job(videoType, researchFocus, SourceScope.SERIES_EPISODE, 1, 2);
    }

    private JobResponse job(
            VideoType videoType,
            ResearchFocus researchFocus,
            SourceScope sourceScope,
            Integer seasonNumber,
            Integer episodeNumber
    ) {
        return new JobResponse(
                "job-1",
                "Explain why this title is trending without quoting dialogue.",
                videoType,
                Orientation.PORTRAIT_9_16,
                DurationPreset.ONE_MINUTE,
                PublishTarget.YOUTUBE_SHORT,
                VoiceProvider.OPENAI_TTS,
                MediaStyle.CINEMATIC_RECAP,
                ContentCategory.MOVIES_AND_SERIES,
                EditorialWindow.WEEK,
                researchFocus,
                5,
                VisualSource.SOURCE_SCREENSHOTS,
                "https://example.com/source",
                "Example Show",
                sourceScope,
                seasonNumber,
                episodeNumber,
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

    private int wordCount(String value) {
        return value.trim().split("\\s+").length;
    }
}
