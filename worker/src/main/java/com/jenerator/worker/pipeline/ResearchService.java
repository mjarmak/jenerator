package com.jenerator.worker.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jenerator.common.dto.JobResponse;
import com.jenerator.common.model.ContentCategory;
import com.jenerator.common.model.ResearchFocus;
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
import java.time.LocalDate;
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

        List<String> trending = hasText(properties.tmdbApiToken()) ? fetchEditorialTitles(job) : List.of();
        List<String> citations = new ArrayList<>();
        if (hasText(job.sourceUrl())) {
            citations.add(job.sourceUrl());
        }
        if (!trending.isEmpty()) {
            citations.addAll(tmdbCitations(job));
        }

        String summary = summary(job, trending);
        Path artifact = jobDirectory.resolve("research-brief.json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", summary);
        body.put("videoType", job.videoType());
        body.put("contentCategory", job.contentCategory());
        body.put("editorialWindow", job.editorialWindow());
        body.put("researchFocus", job.researchFocus());
        body.put("listSize", job.listSize());
        body.put("sourceUrl", job.sourceUrl());
        body.put("sourceTitle", job.sourceTitle());
        body.put("sourceScope", job.sourceScope());
        body.put("trendingTitles", trending);
        body.put("citations", citations);
        Files.writeString(artifact, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body));
        return new ResearchBrief(summary, trending, citations, artifact);
    }

    private List<String> fetchEditorialTitles(JobResponse job) throws IOException, InterruptedException {
        List<String> titles = new ArrayList<>();
        ResearchFocus focus = job.researchFocus() == null ? ResearchFocus.TRENDING : job.researchFocus();
        switch (focus) {
            case TRENDING -> {
                if (includesMovies(job.contentCategory())) {
                    titles.addAll(fetchTrendingTitles(job, "movie", "Trending Movie"));
                }
                if (includesSeries(job.contentCategory())) {
                    titles.addAll(fetchTrendingTitles(job, "tv", "Trending Series"));
                }
            }
            case GROSSING -> {
                if (includesMovies(job.contentCategory())) {
                    titles.addAll(fetchDiscoverTitles(job, "movie", "primary_release_date", "revenue.desc", "Grossing Movie"));
                }
                if (includesSeries(job.contentCategory())) {
                    titles.addAll(fetchPopularSeries(job));
                }
            }
            case NEW_RELEASES -> {
                if (includesMovies(job.contentCategory())) {
                    titles.addAll(fetchDiscoverTitles(job, "movie", "primary_release_date", "popularity.desc", "New Movie"));
                }
                if (includesSeries(job.contentCategory())) {
                    titles.addAll(fetchDiscoverTitles(job, "tv", "first_air_date", "popularity.desc", "New Series"));
                }
            }
            case POPULAR -> titles.addAll(fetchPopularFallback(job));
        }
        if (titles.isEmpty()) {
            titles.addAll(fetchPopularFallback(job));
        }
        return titles.stream()
                .distinct()
                .limit(job.listSize())
                .toList();
    }

    private List<String> fetchTrendingTitles(JobResponse job, String mediaPath, String label) throws IOException, InterruptedException {
        URI uri = UriComponentsBuilder
                .fromUriString("https://api.themoviedb.org/3/trending/{mediaPath}/{timeWindow}")
                .queryParam("language", properties.tmdbLanguage())
                .buildAndExpand(mediaPath, tmdbTrendingWindow())
                .toUri();
        return fetchTitles(uri, label, job.listSize());
    }

    private List<String> fetchDiscoverTitles(JobResponse job, String mediaPath, String dateField, String sortBy, String label) throws IOException, InterruptedException {
        DateWindow dates = dates(job);
        URI uri = UriComponentsBuilder
                .fromUriString("https://api.themoviedb.org/3/discover/{mediaPath}")
                .queryParam("language", properties.tmdbLanguage())
                .queryParam("page", 1)
                .queryParam("sort_by", sortBy)
                .queryParam(dateField + ".gte", dates.start())
                .queryParam(dateField + ".lte", dates.end())
                .buildAndExpand(mediaPath)
                .toUri();
        return fetchTitles(uri, label, job.listSize());
    }

    private List<String> fetchPopularFallback(JobResponse job) throws IOException, InterruptedException {
        List<String> titles = new ArrayList<>();
        if (includesMovies(job.contentCategory())) {
            titles.addAll(fetchTitles(URI.create("https://api.themoviedb.org/3/movie/popular?language=" + properties.tmdbLanguage() + "&page=1"), "Popular Movie", job.listSize()));
        }
        if (includesSeries(job.contentCategory())) {
            titles.addAll(fetchPopularSeries(job));
        }
        return titles;
    }

    private List<String> fetchPopularSeries(JobResponse job) throws IOException, InterruptedException {
        return fetchTitles(URI.create("https://api.themoviedb.org/3/tv/popular?language=" + properties.tmdbLanguage() + "&page=1"), "Popular Series", job.listSize());
    }

    private List<String> fetchTitles(URI uri, String label, int limit) throws IOException, InterruptedException {
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
            if (titles.size() >= limit) {
                break;
            }
            String title = firstText(item.path("title").asText(), item.path("name").asText());
            if (hasText(title)) {
                titles.add(label + ": " + title);
            }
        }
        return titles;
    }

    private List<String> tmdbCitations(JobResponse job) {
        List<String> citations = new ArrayList<>();
        ResearchFocus focus = job.researchFocus() == null ? ResearchFocus.TRENDING : job.researchFocus();
        if (includesMovies(job.contentCategory())) {
            if (focus == ResearchFocus.TRENDING) {
                citations.add("https://developer.themoviedb.org/reference/trending-movies");
            }
            if (focus == ResearchFocus.GROSSING || focus == ResearchFocus.NEW_RELEASES) {
                citations.add("https://developer.themoviedb.org/reference/discover-movie");
            }
            if (focus == ResearchFocus.POPULAR) {
                citations.add("https://developer.themoviedb.org/reference/movie-popular-list");
            }
        }
        if (includesSeries(job.contentCategory())) {
            if (focus == ResearchFocus.TRENDING) {
                citations.add("https://developer.themoviedb.org/reference/trending-tv");
            }
            if (focus == ResearchFocus.NEW_RELEASES) {
                citations.add("https://developer.themoviedb.org/reference/discover-tv");
            }
            if (focus == ResearchFocus.POPULAR || focus == ResearchFocus.GROSSING) {
                citations.add("https://developer.themoviedb.org/reference/tv-series-popular-list");
            }
        }
        return citations.stream().distinct().toList();
    }

    private String summary(JobResponse job, List<String> trending) {
        StringBuilder builder = new StringBuilder();
        builder.append("Primary source scope: ").append(sourceScope(job)).append(". ");
        if (hasText(job.sourceTitle())) {
            builder.append("Primary source: ").append(sourceSubject(job)).append(". ");
        }
        builder.append("Prompt: ").append(job.prompt()).append(". ");
        builder.append("Editorial target: ").append(job.contentCategory())
                .append(" over ").append(job.editorialWindow())
                .append(" with ").append(focusLabel(job.researchFocus()))
                .append(", list size ").append(job.listSize()).append(". ");
        if (!trending.isEmpty()) {
            builder.append("TMDB ").append(focusLabel(job.researchFocus())).append(" context: ")
                    .append(String.join(", ", trending)).append(".");
        }
        return builder.toString().trim();
    }

    private String focusLabel(ResearchFocus focus) {
        ResearchFocus normalized = focus == null ? ResearchFocus.TRENDING : focus;
        return switch (normalized) {
            case TRENDING -> "trending";
            case GROSSING -> "grossing";
            case NEW_RELEASES -> "new release";
            case POPULAR -> "popular";
        };
    }

    String tmdbTrendingWindow() {
        String configured = properties.tmdbTimeWindow() == null ? "" : properties.tmdbTimeWindow().trim().toLowerCase();
        if ("day".equals(configured) || "week".equals(configured)) {
            return configured;
        }
        return "week";
    }

    private DateWindow dates(JobResponse job) {
        LocalDate end = LocalDate.now();
        LocalDate start = switch (job.editorialWindow()) {
            case WEEK -> end.minusDays(7);
            case MONTH -> end.minusMonths(1);
            case YEAR -> LocalDate.of(end.getYear(), 1, 1);
        };
        return new DateWindow(start, end);
    }

    private boolean includesMovies(ContentCategory category) {
        return category == ContentCategory.MOVIES || category == ContentCategory.MOVIES_AND_SERIES;
    }

    private boolean includesSeries(ContentCategory category) {
        return category == ContentCategory.SERIES || category == ContentCategory.MOVIES_AND_SERIES;
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first : second;
    }

    private String sourceSubject(JobResponse job) {
        String title = hasText(job.sourceTitle()) ? job.sourceTitle() : "the source";
        return switch (sourceScope(job)) {
            case MOVIE -> title;
            case SERIES_EPISODE -> title + " season " + blankNumber(job.seasonNumber()) + " episode " + blankNumber(job.episodeNumber());
            case SERIES_SEASON -> title + " season " + blankNumber(job.seasonNumber());
            case SERIES_SHOW -> title + " as a whole series";
        };
    }

    private com.jenerator.common.model.SourceScope sourceScope(JobResponse job) {
        return job.sourceScope() == null ? com.jenerator.common.model.SourceScope.MOVIE : job.sourceScope();
    }

    private String blankNumber(Integer value) {
        return value == null ? "" : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record DateWindow(LocalDate start, LocalDate end) {
    }
}
