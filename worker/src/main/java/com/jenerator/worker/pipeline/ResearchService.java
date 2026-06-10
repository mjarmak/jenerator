package com.jenerator.worker.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jenerator.common.dto.JobResponse;
import com.jenerator.worker.config.ResearchProperties;
import com.jenerator.worker.config.WorkerProperties;
import com.jenerator.worker.model.ResearchBrief;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResearchService {
    private final ResearchProperties properties;
    private final WorkerProperties workerProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ResearchService(ResearchProperties properties, WorkerProperties workerProperties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.workerProperties = workerProperties;
        this.objectMapper = objectMapper;
    }

    public ResearchBrief research(JobResponse job) throws IOException, InterruptedException {
        Path jobDirectory = workerProperties.workspacePath().resolve(job.id());
        Files.createDirectories(jobDirectory);

        List<String> trending = hasText(properties.tmdbApiToken()) ? fetchTrendingTitles() : List.of();
        List<String> citations = new ArrayList<>();
        if (hasText(job.sourceUrl())) {
            citations.add(job.sourceUrl());
        }
        if (!trending.isEmpty()) {
            citations.add("https://developer.themoviedb.org/reference/trending-all");
        }

        String summary = summary(job, trending);
        Path artifact = jobDirectory.resolve("research-brief.json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", summary);
        body.put("videoType", job.videoType());
        body.put("sourceUrl", job.sourceUrl());
        body.put("sourceTitle", job.sourceTitle());
        body.put("trendingTitles", trending);
        body.put("citations", citations);
        Files.writeString(artifact, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body));
        return new ResearchBrief(summary, trending, citations, artifact);
    }

    private List<String> fetchTrendingTitles() throws IOException, InterruptedException {
        String timeWindow = properties.tmdbTimeWindow().equals("day") ? "day" : "week";
        URI uri = UriComponentsBuilder
                .fromUriString("https://api.themoviedb.org/3/trending/all/{timeWindow}")
                .queryParam("language", properties.tmdbLanguage())
                .buildAndExpand(timeWindow)
                .toUri();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + properties.tmdbApiToken())
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("TMDB trending request failed with status " + response.statusCode() + ".");
        }
        JsonNode results = objectMapper.readTree(response.body()).path("results");
        List<String> titles = new ArrayList<>();
        for (JsonNode item : results) {
            if (titles.size() >= 8) {
                break;
            }
            String title = firstText(item.path("title").asText(), item.path("name").asText());
            if (hasText(title)) {
                titles.add(title);
            }
        }
        return titles;
    }

    private String summary(JobResponse job, List<String> trending) {
        StringBuilder builder = new StringBuilder();
        if (hasText(job.sourceTitle())) {
            builder.append("Primary source title: ").append(job.sourceTitle()).append(". ");
        }
        if (job.seasonNumber() != null) {
            builder.append("Season ").append(job.seasonNumber()).append(". ");
        }
        if (job.episodeNumber() != null) {
            builder.append("Episode ").append(job.episodeNumber()).append(". ");
        }
        builder.append("Prompt: ").append(job.prompt()).append(". ");
        if (!trending.isEmpty()) {
            builder.append("Current TMDB trending context: ").append(String.join(", ", trending)).append(".");
        }
        return builder.toString().trim();
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first : second;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
