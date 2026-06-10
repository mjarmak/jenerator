package com.jenerator.worker.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jenerator.common.dto.JobResponse;
import com.jenerator.common.model.DurationPreset;
import com.jenerator.common.model.ResearchFocus;
import com.jenerator.common.model.SourceScope;
import com.jenerator.worker.config.ScriptGenerationProperties;
import com.jenerator.worker.model.GeneratedScript;
import com.jenerator.worker.model.ResearchBrief;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
public class ScriptDraftService {
    private final ScriptGenerationProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ScriptDraftService(ScriptGenerationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public GeneratedScript draft(JobResponse job, ResearchBrief research) throws IOException, InterruptedException {
        if (hasText(properties.openaiApiKey())) {
            try {
                return openAiDraft(job, research);
            } catch (Exception exception) {
                if (!properties.fallbackToTemplate()) {
                    if (exception instanceof IOException ioException) {
                        throw ioException;
                    }
                    if (exception instanceof InterruptedException interruptedException) {
                        throw interruptedException;
                    }
                    if (exception instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new IllegalStateException("OpenAI script generation failed.", exception);
                }
            }
        }
        return templateDraft(job, research);
    }

    private GeneratedScript openAiDraft(JobResponse job, ResearchBrief research) throws IOException, InterruptedException {
        Map<String, Object> payload = Map.of(
                "model", properties.openaiModel(),
                "input", List.of(
                        Map.of(
                                "role", "system",
                                "content", systemPrompt()
                        ),
                        Map.of(
                                "role", "user",
                                "content", userPrompt(job, research)
                        )
                ),
                "max_output_tokens", properties.maxOutputTokens(),
                "store", false
        );
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                .header("Authorization", "Bearer " + properties.openaiApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenAI script generation failed with status " + response.statusCode() + ": " + response.body());
        }
        JsonNode json = objectMapper.readTree(outputText(response.body()));
        int seconds = job.durationPreset() == DurationPreset.ONE_MINUTE ? 58 : job.targetSeconds();
        String title = textOr(json, "title", title(job));
        String narration = textOr(json, "narration", templateDraft(job, research).narration());
        String description = textOr(json, "description", "Generated draft for review.");
        return new GeneratedScript(title, fitWords(narration, Math.max(95, seconds * 2)), description, seconds);
    }

    private GeneratedScript templateDraft(JobResponse job, ResearchBrief research) {
        int seconds = job.durationPreset() == DurationPreset.ONE_MINUTE ? 58 : job.targetSeconds();
        String title = title(job);
        int targetWords = Math.max(95, seconds * 2);
        String narration = switch (job.videoType()) {
            case SUMMARY -> summaryNarration(job, research);
            case RECAP -> recapNarration(job, research);
            case TOP_LIST -> topListNarration(job, research);
            case RECOMMENDATION -> recommendationNarration(job, research);
        };
        narration = fitWords(narration, targetWords);
        String description = description(job, research);
        return new GeneratedScript(title, narration, description, seconds);
    }

    private String systemPrompt() {
        return """
                You write concise YouTube entertainment videos about movies and series.
                Return JSON only with keys: title, description, narration.
                Do not include markdown.
                Narration must be voiceover-ready, no scene directions, no timestamps, no citations inside the narration.
                Avoid claims not supported by the provided research. Never include copyrighted dialogue or song lyrics.
                """;
    }

    private String userPrompt(JobResponse job, ResearchBrief research) {
        int seconds = job.durationPreset() == DurationPreset.ONE_MINUTE ? 58 : job.targetSeconds();
        return """
                Create a %d second %s for %s.
                Content category: %s. Editorial window: %s. Research focus: %s. Desired list size: %d.
                Publish target: %s. Orientation: %s.
                Source scope: %s. Source title: %s. Season: %s. Episode: %s. Source URL: %s.
                User prompt: %s
                Research summary: %s
                Research titles: %s
                Structure: %s
                Write a strong hook, tight factual beats, and a clean ending. Keep it safe for review and upload.
                """.formatted(
                seconds,
                job.videoType(),
                sourceSubject(job),
                job.contentCategory(),
                job.editorialWindow(),
                job.researchFocus(),
                job.listSize(),
                job.publishTarget(),
                job.orientation(),
                sourceScope(job),
                blank(job.sourceTitle()),
                job.seasonNumber() == null ? "" : job.seasonNumber(),
                job.episodeNumber() == null ? "" : job.episodeNumber(),
                blank(job.sourceUrl()),
                job.prompt(),
                research == null ? "" : research.summary(),
                research == null || research.trendingTitles() == null ? List.of() : research.trendingTitles(),
                structure(job)
        );
    }

    private String summaryNarration(JobResponse job, ResearchBrief research) {
        String subject = subject(job);
        return """
                Here is the fast summary of %s. %s The core idea is simple: the story builds around the choices, pressure, and consequences that make this title worth catching up on.
                The important context is %s. Focus on the main conflict, the emotional turn, and the ending setup without quoting dialogue or spoiling more than the prompt asks for.
                If you are deciding whether to watch, this summary should tell you the premise, the stakes, and why people are paying attention right now.
                """.formatted(subject, prompt(job), researchAngle(research));
    }

    private String recapNarration(JobResponse job, ResearchBrief research) {
        String subject = subject(job);
        return """
                Let us recap %s quickly. %s The episode or season moves through a clear chain of cause and effect: a problem appears, the characters react, and the consequences reshape the next choice.
                The key beats to track are the turning point, the reveal, and the final question left on the table. %s
                By the end, the value is not only what happened, but why it changes the story going forward.
                """.formatted(subject, prompt(job), researchAngle(research));
    }

    private String topListNarration(JobResponse job, ResearchBrief research) {
        List<String> titles = rankedTitles(job, research);
        StringBuilder builder = new StringBuilder();
        builder.append("Here are ")
                .append(Math.min(job.listSize(), titles.size()))
                .append(' ')
                .append(label(job).toLowerCase())
                .append(" to watch this ")
                .append(job.editorialWindow().name().toLowerCase())
                .append(" using ")
                .append(focusLabel(job))
                .append(" signals")
                .append(". ");
        for (int index = 0; index < titles.size(); index += 1) {
            builder.append("Number ")
                    .append(index + 1)
                    .append(": ")
                    .append(titles.get(index))
                    .append(". ");
        }
        builder.append("Use this list as a quick watch queue, then pick the title that best matches your mood tonight.");
        return builder.toString();
    }

    private String recommendationNarration(JobResponse job, ResearchBrief research) {
        String subject = subject(job);
        return """
                If you need something to watch next, start with %s. %s The recommendation angle is %s.
                This is a good fit if you want a title with clear stakes, easy momentum, and enough conversation around it to feel current.
                Put it on your shortlist if the premise matches your mood, then compare it with the other trending picks before you commit.
                """.formatted(subject, prompt(job), researchAngle(research));
    }

    private String description(JobResponse job, ResearchBrief research) {
        String description = """
                AI-assisted %s for %s.

                Prompt:
                %s

                Review the preview, title, description, tags, sources, synthetic-media flag, and Made for Kids setting before upload.
                """.formatted(
                job.videoType().name().toLowerCase().replace('_', ' '),
                subject(job),
                job.prompt()
        ).trim();
        if (research != null && research.trendingTitles() != null && !research.trendingTitles().isEmpty()) {
            description += "\n\nResearch context (" + focusLabel(job) + "):\n- " + String.join("\n- ", research.trendingTitles());
        }
        return description;
    }

    private String structure(JobResponse job) {
        String target = sourceScopeLabel(job);
        return switch (job.videoType()) {
            case SUMMARY -> "brief " + target + " premise, main conflict, key turning point, why it matters now";
            case RECAP -> target + " events, why they happened, turning point, ending setup";
            case TOP_LIST -> "ranked countdown using the provided research titles where possible";
            case RECOMMENDATION -> "who should watch, why it is timely, mood fit, final recommendation";
        };
    }

    private List<String> rankedTitles(JobResponse job, ResearchBrief research) {
        List<String> titles = research == null || research.trendingTitles() == null
                ? List.of()
                : research.trendingTitles()
                .stream()
                .map(this::cleanTitle)
                .filter(this::hasText)
                .distinct()
                .limit(Math.max(3, Math.min(job.listSize(), 8)))
                .toList();
        if (!titles.isEmpty()) {
            return titles;
        }
        return List.of(subject(job), "a current breakout pick", "a reliable audience favorite");
    }

    private String subject(JobResponse job) {
        if (hasText(job.sourceTitle())) {
            return sourceSubject(job);
        }
        return job.contentCategory() == null ? "this title" : label(job).toLowerCase();
    }

    private String sourceSubject(JobResponse job) {
        String title = hasText(job.sourceTitle()) ? job.sourceTitle() : "the requested movie or series";
        return switch (sourceScope(job)) {
            case MOVIE -> title;
            case SERIES_EPISODE -> title + " season " + blankNumber(job.seasonNumber()) + " episode " + blankNumber(job.episodeNumber());
            case SERIES_SEASON -> title + " season " + blankNumber(job.seasonNumber());
            case SERIES_SHOW -> title + " as a whole series";
        };
    }

    private SourceScope sourceScope(JobResponse job) {
        return job.sourceScope() == null ? SourceScope.MOVIE : job.sourceScope();
    }

    private String sourceScopeLabel(JobResponse job) {
        return switch (sourceScope(job)) {
            case MOVIE -> "movie";
            case SERIES_EPISODE -> "episode";
            case SERIES_SEASON -> "season";
            case SERIES_SHOW -> "series";
        };
    }

    private String blankNumber(Integer value) {
        return value == null ? "" : value.toString();
    }

    private String prompt(JobResponse job) {
        return hasText(job.prompt()) ? job.prompt() : "The prompt asks for a concise entertainment video.";
    }

    private String researchAngle(ResearchBrief research) {
        if (research == null || !hasText(research.summary())) {
            return "the available prompt and source details";
        }
        return research.summary();
    }

    private String cleanTitle(String value) {
        return value.replaceFirst("^(?:(?:Trending|New|Popular|Grossing)\\s+)?(?:Movie|Series):\\s*", "").trim();
    }

    private String outputText(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode output = root.path("output");
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            for (JsonNode part : content) {
                if ("output_text".equals(part.path("type").asText()) && hasText(part.path("text").asText())) {
                    return stripJsonFence(part.path("text").asText());
                }
            }
        }
        if (hasText(root.path("output_text").asText())) {
            return stripJsonFence(root.path("output_text").asText());
        }
        throw new IllegalStateException("OpenAI script generation did not return output_text.");
    }

    private String stripJsonFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }

    private String textOr(JsonNode json, String field, String fallback) {
        String value = json.path(field).asText();
        return hasText(value) ? value.trim() : fallback;
    }

    private String title(JobResponse job) {
        String base = hasText(job.sourceTitle()) ? job.sourceTitle() : job.prompt().replaceAll("\\s+", " ").trim();
        String clipped = base.length() > 54 ? base.substring(0, 51) + "..." : base;
        return switch (job.videoType()) {
            case SUMMARY -> clipped + " Summary";
            case RECAP -> clipped + " Recap";
            case TOP_LIST -> "Top " + job.listSize() + " " + label(job) + " This " + titleCase(job.editorialWindow().name());
            case RECOMMENDATION -> "Watch Next: " + label(job);
        };
    }

    private String label(JobResponse job) {
        return switch (job.contentCategory()) {
            case MOVIES -> "Movies";
            case SERIES -> "Series";
            case MOVIES_AND_SERIES -> "Movies and Series";
        };
    }

    private String focusLabel(JobResponse job) {
        var focus = job.researchFocus() == null ? ResearchFocus.TRENDING : job.researchFocus();
        return switch (focus) {
            case TRENDING -> "trending";
            case GROSSING -> "grossing";
            case NEW_RELEASES -> "new release";
            case POPULAR -> "popular";
        };
    }

    private String titleCase(String value) {
        String lower = value.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String fitWords(String text, int targetWords) {
        String[] words = text.split("\\s+");
        if (words.length <= targetWords) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < targetWords; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(words[i]);
        }
        return builder.append('.').toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
