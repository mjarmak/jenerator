package com.jenerator.worker.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jenerator.common.dto.JobArtifactResponse;
import com.jenerator.common.dto.JobResponse;
import com.jenerator.worker.client.ControlPlaneClient;
import com.jenerator.worker.config.WorkerProperties;
import com.jenerator.worker.config.YoutubeProperties;
import com.jenerator.worker.model.YoutubeUploadResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

@Service
public class YoutubeUploadService {
    private static final int TITLE_MAX_CHARS = 100;
    private static final int DESCRIPTION_MAX_BYTES = 5_000;
    private static final int TAGS_MAX_CHARS = 500;

    private final YoutubeProperties properties;
    private final WorkerProperties workerProperties;
    private final ControlPlaneClient controlPlaneClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public YoutubeUploadService(
            YoutubeProperties properties,
            WorkerProperties workerProperties,
            ControlPlaneClient controlPlaneClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.workerProperties = workerProperties;
        this.controlPlaneClient = controlPlaneClient;
        this.objectMapper = objectMapper;
    }

    public boolean configured() {
        return switch (properties.mode()) {
            case DRY_RUN -> true;
            case BROWSER -> !properties.browserUploadCommand().isEmpty();
            case API -> hasText(properties.clientId())
                    && hasText(properties.refreshToken());
        };
    }

    public YoutubeUploadResult upload(JobResponse job) throws IOException, InterruptedException {
        if (properties.mode() == YoutubeProperties.UploadMode.DRY_RUN) {
            return writeDryRunPayload(job);
        }
        Path video = resolveRenderedVideo(job);
        if (properties.mode() == YoutubeProperties.UploadMode.BROWSER) {
            return uploadWithBrowser(job, video);
        }
        String accessToken = refreshAccessToken();
        String videoId = uploadMultipart(job, video, accessToken);
        return new YoutubeUploadResult(videoId, "https://www.youtube.com/watch?v=" + videoId, false, null);
    }

    Path resolveRenderedVideo(JobResponse job) throws IOException {
        Path jobDirectory = workerProperties.workspacePath().resolve(job.id());
        Path localPreview = jobDirectory.resolve("preview.mp4");
        if (Files.exists(localPreview)) {
            return localPreview;
        }
        JobArtifactResponse preview = previewArtifact(job);
        if (preview == null) {
            throw new IllegalStateException("Rendered video not found locally and no PREVIEW_VIDEO artifact is available: " + localPreview);
        }
        return controlPlaneClient.downloadArtifact(preview, jobDirectory.resolve("downloaded-artifacts"));
    }

    private YoutubeUploadResult writeDryRunPayload(JobResponse job) throws IOException {
        Path jobDirectory = workerProperties.workspacePath().resolve(job.id());
        Files.createDirectories(jobDirectory);
        Path payload = jobDirectory.resolve("youtube-upload-dry-run.json");
        Map<String, Object> body = youtubeBody(metadata(job));
        Files.writeString(payload, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body));
        return new YoutubeUploadResult("dry-run-" + job.id(), payload.toUri().toString(), true, payload);
    }

    private YoutubeUploadResult uploadWithBrowser(JobResponse job, Path video) throws IOException, InterruptedException {
        if (properties.browserUploadCommand().isEmpty()) {
            throw new IllegalStateException("Browser upload mode requires jenerator.youtube.browser-upload-command.");
        }
        Path jobDirectory = workerProperties.workspacePath().resolve(job.id());
        Files.createDirectories(jobDirectory);
        YoutubeMetadata metadata = metadata(job);
        Path description = jobDirectory.resolve("youtube-description.txt");
        Files.writeString(description, metadata.description());
        Path payload = jobDirectory.resolve("youtube-browser-upload.json");
        Files.writeString(payload, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(youtubeBody(metadata)));

        List<String> command = new ArrayList<>(properties.browserUploadCommand());
        command.add("--video");
        command.add(video.toString());
        command.add("--title");
        command.add(metadata.title());
        command.add("--description-file");
        command.add(description.toString());
        command.add("--tags");
        command.add(String.join(",", metadata.tags()));
        command.add("--privacy");
        command.add(properties.privacyStatus());
        command.add("--made-for-kids");
        command.add(Boolean.toString(job.madeForKids()));
        command.add("--contains-synthetic-media");
        command.add(Boolean.toString(job.containsSyntheticMedia()));

        Process process = new ProcessBuilder(command)
                .directory(jobDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(jobDirectory.resolve("youtube-browser-upload.log").toFile())
                .start();
        if (!process.waitFor(45, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException("Browser upload timed out.");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Browser upload command failed. See " + jobDirectory.resolve("youtube-browser-upload.log"));
        }
        return new YoutubeUploadResult("browser-upload-" + job.id(), payload.toUri().toString(), false, payload);
    }

    private String refreshAccessToken() throws IOException, InterruptedException {
        if (!configured()) {
            throw new IllegalStateException("YouTube API upload requires client id and refresh token.");
        }
        String form = "client_id=" + encode(properties.clientId())
                + clientSecretFormValue()
                + "&refresh_token=" + encode(properties.refreshToken())
                + "&grant_type=refresh_token";
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Failed to refresh YouTube access token: " + response.body());
        }
        JsonNode json = objectMapper.readTree(response.body());
        return json.path("access_token").asText();
    }

    private String uploadMultipart(JobResponse job, Path video, String accessToken) throws IOException, InterruptedException {
        String boundary = "jenerator-" + UUID.randomUUID();
        byte[] metadata = objectMapper.writeValueAsBytes(youtubeBody(metadata(job)));
        byte[] videoBytes = Files.readAllBytes(video);
        byte[] body = multipartBody(boundary, metadata, videoBytes);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=multipart&part=snippet,status"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "multipart/related; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("YouTube upload failed: " + response.body());
        }
        return objectMapper.readTree(response.body()).path("id").asText();
    }

    YoutubeMetadata metadata(JobResponse job) {
        return new YoutubeMetadata(
                normalizeTitle(job.title()),
                truncateUtf8Bytes(cleanText(youtubeDescription(job)), DESCRIPTION_MAX_BYTES),
                normalizeTags(job.tags()),
                properties.privacyStatus(),
                job.madeForKids(),
                job.containsSyntheticMedia()
        );
    }

    private Map<String, Object> youtubeBody(YoutubeMetadata metadata) {
        return Map.of(
                "snippet", Map.of(
                        "title", metadata.title(),
                        "description", metadata.description(),
                        "tags", metadata.tags(),
                        "categoryId", "24"
                ),
                "status", Map.of(
                        "privacyStatus", metadata.privacyStatus(),
                        "selfDeclaredMadeForKids", metadata.madeForKids(),
                        "containsSyntheticMedia", metadata.containsSyntheticMedia()
                )
        );
    }

    private JobArtifactResponse previewArtifact(JobResponse job) {
        if (job.artifacts() == null) {
            return null;
        }
        return job.artifacts()
                .stream()
                .filter(artifact -> "PREVIEW_VIDEO".equals(artifact.type()))
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private String youtubeDescription(JobResponse job) {
        String description = job.description() == null ? "" : job.description();
        if (job.citations() == null || job.citations().isEmpty()) {
            return description;
        }
        return description + "\n\nSources:\n- " + String.join("\n- ", job.citations());
    }

    private byte[] multipartBody(String boundary, byte[] metadata, byte[] videoBytes) {
        byte[] start = ("--" + boundary + "\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] middle = ("\r\n--" + boundary + "\r\n"
                + "Content-Type: video/mp4\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] end = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[start.length + metadata.length + middle.length + videoBytes.length + end.length];
        int offset = 0;
        System.arraycopy(start, 0, body, offset, start.length);
        offset += start.length;
        System.arraycopy(metadata, 0, body, offset, metadata.length);
        offset += metadata.length;
        System.arraycopy(middle, 0, body, offset, middle.length);
        offset += middle.length;
        System.arraycopy(videoBytes, 0, body, offset, videoBytes.length);
        offset += videoBytes.length;
        System.arraycopy(end, 0, body, offset, end.length);
        return body;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private String normalizeTitle(String value) {
        String title = cleanText(defaultText(value, "Jenerator video"));
        if (!hasText(title)) {
            title = "Jenerator video";
        }
        return truncateChars(title, TITLE_MAX_CHARS);
    }

    private String cleanText(String value) {
        return defaultText(value, "")
                .replace('<', ' ')
                .replace('>', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ")
                .replaceAll("[ \t]+", " ")
                .replaceAll(" *\r?\n *", "\n")
                .trim();
    }

    private String truncateChars(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars).trim();
    }

    private String truncateUtf8Bytes(String value, int maxBytes) {
        if (value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        int bytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int nextBytes = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + nextBytes > maxBytes) {
                break;
            }
            builder.append(character);
            bytes += nextBytes;
            offset += Character.charCount(codePoint);
        }
        return builder.toString().trim();
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> normalized = tags.stream()
                .map(this::normalizeTag)
                .filter(this::hasText)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(() -> new LinkedHashSet<String>()),
                        ArrayList::new
                ));
        List<String> accepted = new ArrayList<>();
        for (String tag : normalized) {
            accepted.add(tag);
            if (tagCost(accepted) > TAGS_MAX_CHARS) {
                accepted.remove(accepted.size() - 1);
            }
        }
        return List.copyOf(accepted);
    }

    private String normalizeTag(String value) {
        return cleanText(value)
                .replace(",", " ")
                .replaceFirst("^#+", "")
                .replaceAll("\\s+", " ")
                .trim();
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

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String clientSecretFormValue() {
        if (!hasText(properties.clientSecret())) {
            return "";
        }
        return "&client_secret=" + encode(properties.clientSecret());
    }

    record YoutubeMetadata(
            String title,
            String description,
            List<String> tags,
            String privacyStatus,
            boolean madeForKids,
            boolean containsSyntheticMedia
    ) {
    }
}
