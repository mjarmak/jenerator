package com.jenerator.worker.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jenerator.common.dto.JobResponse;
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
import java.util.concurrent.TimeUnit;

@Service
public class YoutubeUploadService {
    private final YoutubeProperties properties;
    private final WorkerProperties workerProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public YoutubeUploadService(YoutubeProperties properties, WorkerProperties workerProperties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.workerProperties = workerProperties;
        this.objectMapper = objectMapper;
    }

    public boolean configured() {
        return switch (properties.mode()) {
            case DRY_RUN -> true;
            case BROWSER -> !properties.browserUploadCommand().isEmpty();
            case API -> hasText(properties.clientId())
                    && hasText(properties.clientSecret())
                    && hasText(properties.refreshToken());
        };
    }

    public YoutubeUploadResult upload(JobResponse job) throws IOException, InterruptedException {
        if (properties.mode() == YoutubeProperties.UploadMode.DRY_RUN) {
            return writeDryRunPayload(job);
        }
        Path video = workerProperties.workspacePath().resolve(job.id()).resolve("preview.mp4");
        if (!Files.exists(video)) {
            throw new IllegalStateException("Rendered video not found: " + video);
        }
        if (properties.mode() == YoutubeProperties.UploadMode.BROWSER) {
            return uploadWithBrowser(job, video);
        }
        String accessToken = refreshAccessToken();
        String videoId = uploadMultipart(job, video, accessToken);
        return new YoutubeUploadResult(videoId, "https://www.youtube.com/watch?v=" + videoId, false);
    }

    private YoutubeUploadResult writeDryRunPayload(JobResponse job) throws IOException {
        Path jobDirectory = workerProperties.workspacePath().resolve(job.id());
        Files.createDirectories(jobDirectory);
        Path payload = jobDirectory.resolve("youtube-upload-dry-run.json");
        Map<String, Object> body = youtubeBody(job);
        Files.writeString(payload, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body));
        return new YoutubeUploadResult("dry-run-" + job.id(), payload.toUri().toString(), true);
    }

    private YoutubeUploadResult uploadWithBrowser(JobResponse job, Path video) throws IOException, InterruptedException {
        if (properties.browserUploadCommand().isEmpty()) {
            throw new IllegalStateException("Browser upload mode requires jenerator.youtube.browser-upload-command.");
        }
        Path jobDirectory = workerProperties.workspacePath().resolve(job.id());
        Files.createDirectories(jobDirectory);
        Path description = jobDirectory.resolve("youtube-description.txt");
        Files.writeString(description, youtubeDescription(job));
        Path payload = jobDirectory.resolve("youtube-browser-upload.json");
        Files.writeString(payload, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(youtubeBody(job)));

        List<String> command = new ArrayList<>(properties.browserUploadCommand());
        command.add("--video");
        command.add(video.toString());
        command.add("--title");
        command.add(job.title() == null ? "Jenerator video" : job.title());
        command.add("--description-file");
        command.add(description.toString());
        command.add("--tags");
        command.add(String.join(",", job.tags() == null ? List.of() : job.tags()));
        command.add("--privacy");
        command.add(properties.privacyStatus());
        command.add("--made-for-kids");
        command.add(Boolean.toString(job.madeForKids()));

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
        return new YoutubeUploadResult("browser-upload-" + job.id(), payload.toUri().toString(), false);
    }

    private String refreshAccessToken() throws IOException, InterruptedException {
        if (!configured()) {
            throw new IllegalStateException("YouTube API upload requires client id, client secret, and refresh token.");
        }
        String form = "client_id=" + encode(properties.clientId())
                + "&client_secret=" + encode(properties.clientSecret())
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
        byte[] metadata = objectMapper.writeValueAsBytes(youtubeBody(job));
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

    private Map<String, Object> youtubeBody(JobResponse job) {
        return Map.of(
                "snippet", Map.of(
                        "title", job.title() == null ? "Jenerator video" : job.title(),
                        "description", youtubeDescription(job),
                        "tags", job.tags() == null ? List.of() : job.tags(),
                        "categoryId", "24"
                ),
                "status", Map.of(
                        "privacyStatus", properties.privacyStatus(),
                        "selfDeclaredMadeForKids", job.madeForKids(),
                        "containsSyntheticMedia", job.containsSyntheticMedia()
                )
        );
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

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
