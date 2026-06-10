package com.jenerator.worker.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jenerator.common.dto.JobResponse;
import com.jenerator.common.model.DurationPreset;
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
        String narration = """
                Hook: here is the entertainment story worth watching right now.
                Setup: %s
                Research angle: %s
                Main beats: keep the recap focused on stakes, character turns, and why viewers are talking about it now.
                Watch note: if this matches your mood, add it to your list and compare it with the next recommendation.
                """.formatted(job.prompt(), research == null ? "" : research.summary()).replaceAll("\\s+", " ").trim();
        narration = fitWords(narration, targetWords);
        String description = "Generated draft for review. Confirm rights, sources, and final wording before upload.";
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
                Publish target: %s. Orientation: %s.
                Source title: %s. Season: %s. Episode: %s. Source URL: %s.
                User prompt: %s
                Research summary: %s
                Trending titles: %s
                Write a strong hook, 3-5 tight beats, and a clean recommendation-style ending.
                """.formatted(
                seconds,
                job.videoType(),
                job.sourceTitle() == null ? "the requested movie or series" : job.sourceTitle(),
                job.publishTarget(),
                job.orientation(),
                blank(job.sourceTitle()),
                job.seasonNumber() == null ? "" : job.seasonNumber(),
                job.episodeNumber() == null ? "" : job.episodeNumber(),
                blank(job.sourceUrl()),
                job.prompt(),
                research == null ? "" : research.summary(),
                research == null || research.trendingTitles() == null ? List.of() : research.trendingTitles()
        );
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
            case TOP_LIST -> "Top Picks: " + clipped;
            case RECOMMENDATION -> "Watch Next: " + clipped;
        };
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
