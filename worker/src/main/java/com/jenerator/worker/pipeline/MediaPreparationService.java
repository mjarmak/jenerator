package com.jenerator.worker.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jenerator.common.dto.JobResponse;
import com.jenerator.common.model.Orientation;
import com.jenerator.common.model.VisualSource;
import com.jenerator.worker.client.ControlPlaneClient;
import com.jenerator.worker.config.BrowserAutomationProperties;
import com.jenerator.worker.config.ImageProperties;
import com.jenerator.worker.config.WorkerProperties;
import com.jenerator.worker.model.MediaPlan;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class MediaPreparationService {
    private final WorkerProperties workerProperties;
    private final ImageProperties imageProperties;
    private final BrowserAutomationProperties browserProperties;
    private final ControlPlaneClient controlPlaneClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public MediaPreparationService(
            WorkerProperties workerProperties,
            ImageProperties imageProperties,
            BrowserAutomationProperties browserProperties,
            ControlPlaneClient controlPlaneClient,
            ObjectMapper objectMapper
    ) {
        this.workerProperties = workerProperties;
        this.imageProperties = imageProperties;
        this.browserProperties = browserProperties;
        this.controlPlaneClient = controlPlaneClient;
        this.objectMapper = objectMapper;
    }

    public MediaPlan prepare(JobResponse job) throws IOException, InterruptedException {
        Path jobDirectory = workerProperties.workspacePath().resolve(job.id());
        Path assetDirectory = jobDirectory.resolve("assets");
        Files.createDirectories(assetDirectory);

        Path music = hasText(job.musicAssetId())
                ? controlPlaneClient.downloadAsset(job.musicAssetId(), assetDirectory.resolve("music"))
                : null;

        List<Path> visuals = new ArrayList<>();
        for (String assetId : job.visualAssetIds()) {
            visuals.add(downloadVisual(assetId, assetDirectory.resolve("visuals")));
        }

        Path manifest = null;
        if (job.visualSource() == VisualSource.AI_GENERATED_IMAGES) {
            visuals.addAll(generateImages(job, jobDirectory));
            if (visuals.isEmpty()) {
                manifest = writeManifest(job, jobDirectory, "OpenAI image generation is not configured or failed.");
            }
        } else if (job.visualSource() == VisualSource.SOURCE_SCREENSHOTS && visuals.isEmpty()) {
            visuals.addAll(captureSource(job, jobDirectory));
            if (visuals.isEmpty()) {
                manifest = writeManifest(job, jobDirectory, "Source URL is queued for local screenshot capture.");
            }
        } else if (job.visualSource() == VisualSource.UPLOADED_ASSETS && visuals.isEmpty()) {
            manifest = writeManifest(job, jobDirectory, "No uploaded visual assets were provided.");
        }

        Path visual = visuals.stream().filter(this::isVisualFile).findFirst().orElse(null);
        return new MediaPlan(visual, visuals, music, manifest);
    }

    private List<Path> captureSource(JobResponse job, Path jobDirectory) throws IOException, InterruptedException {
        if (!hasText(job.sourceUrl()) || browserProperties.sourceCaptureCommand().isEmpty()) {
            return List.of();
        }
        Path outputDirectory = jobDirectory.resolve("captured-visuals");
        Files.createDirectories(outputDirectory);
        List<String> command = sourceCaptureCommand(job, outputDirectory);

        Process process = new ProcessBuilder(command)
                .directory(jobDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(jobDirectory.resolve("browser-capture.log").toFile())
                .start();
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            return List.of();
        }
        if (process.exitValue() != 0) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(outputDirectory)) {
            return paths.filter(this::isVisualFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    List<String> sourceCaptureCommand(JobResponse job, Path outputDirectory) {
        List<String> command = new ArrayList<>(browserProperties.sourceCaptureCommand());
        command.add("--url");
        command.add(job.sourceUrl());
        command.add("--out");
        command.add(outputDirectory.toString());
        command.add("--width");
        command.add(Integer.toString(job.width()));
        command.add("--height");
        command.add(Integer.toString(job.height()));
        command.add("--shots");
        command.add(Integer.toString(browserProperties.sourceCaptureShots()));
        command.add("--delay");
        command.add(Integer.toString(browserProperties.sourceCaptureDelaySeconds()));
        command.add("--interval");
        command.add(Integer.toString(browserProperties.sourceCaptureIntervalSeconds()));
        if (browserProperties.sourceCaptureManualSeconds() > 0) {
            command.add("--manual-seconds");
            command.add(Integer.toString(browserProperties.sourceCaptureManualSeconds()));
        }
        if (browserProperties.sourceCaptureAutoPlay()) {
            command.add("--auto-play");
            command.add("true");
        }
        if (hasText(browserProperties.sourceCaptureSkipKey()) && browserProperties.sourceCaptureSkipCount() > 0) {
            command.add("--skip-key");
            command.add(browserProperties.sourceCaptureSkipKey());
            command.add("--skip-count");
            command.add(Integer.toString(browserProperties.sourceCaptureSkipCount()));
        }
        if (hasText(browserProperties.sourceCaptureUserDataDir())) {
            command.add("--user-data-dir");
            command.add(browserProperties.sourceCaptureUserDataDir());
        }
        if (hasText(browserProperties.sourceCaptureChannel())) {
            command.add("--channel");
            command.add(browserProperties.sourceCaptureChannel());
        }
        command.add("--headless");
        command.add(Boolean.toString(browserProperties.sourceCaptureHeadless()));
        return command;
    }

    private Path downloadVisual(String assetId, Path directory) {
        try {
            return controlPlaneClient.downloadAsset(assetId, directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to download visual asset " + assetId, exception);
        }
    }

    private List<Path> generateImages(JobResponse job, Path jobDirectory) throws IOException, InterruptedException {
        if (!hasText(imageProperties.openaiApiKey())) {
            return List.of();
        }
        List<Path> generated = new ArrayList<>();
        for (int index = 0; index < imageProperties.generatedCount(); index += 1) {
            Path image = generateImage(job, jobDirectory, index + 1, imageProperties.generatedCount());
            if (image != null) {
                generated.add(image);
            } else if (!imageProperties.fallbackToManifest()) {
                break;
            }
        }
        return generated;
    }

    private Path generateImage(JobResponse job, Path jobDirectory, int sceneIndex, int sceneCount) throws IOException, InterruptedException {
        if (!hasText(imageProperties.openaiApiKey())) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", imageProperties.openaiModel());
        payload.put("prompt", imagePrompt(job, sceneIndex, sceneCount));
        payload.put("size", job.orientation() == Orientation.PORTRAIT_9_16 ? "1024x1536" : "1536x1024");
        payload.put("quality", imageProperties.quality());
        payload.put("output_format", imageProperties.outputFormat());
        payload.put("n", 1);

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/images/generations"))
                .header("Authorization", "Bearer " + imageProperties.openaiApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (imageProperties.fallbackToManifest()) {
                return null;
            }
            throw new IllegalStateException("OpenAI image generation failed with status " + response.statusCode() + ": " + response.body());
        }

        JsonNode image = objectMapper.readTree(response.body()).path("data").path(0).path("b64_json");
        if (image.isMissingNode() || image.asText().isBlank()) {
            if (imageProperties.fallbackToManifest()) {
                return null;
            }
            throw new IllegalStateException("OpenAI image generation response did not include image data.");
        }
        String extension = imageProperties.outputFormat().equalsIgnoreCase("jpeg") ? "jpg" : imageProperties.outputFormat().toLowerCase();
        Path output = jobDirectory.resolve("generated-visual-%02d.%s".formatted(sceneIndex, extension));
        Files.write(output, Base64.getDecoder().decode(image.asText()));
        return output;
    }

    private Path writeManifest(JobResponse job, Path jobDirectory, String reason) throws IOException {
        Path manifest = jobDirectory.resolve("visual-capture-plan.txt");
        Files.writeString(manifest, """
                reason=%s
                visualSource=%s
                sourceUrl=%s
                sourceTitle=%s
                sourceScope=%s
                season=%s
                episode=%s
                note=For Netflix/Prime/DRM sources, capture must use a signed-in local browser session or uploaded screenshots; the worker does not bypass DRM.
                """.formatted(
                reason,
                job.visualSource(),
                blank(job.sourceUrl()),
                blank(job.sourceTitle()),
                job.sourceScope(),
                job.seasonNumber() == null ? "" : job.seasonNumber(),
                job.episodeNumber() == null ? "" : job.episodeNumber()
        ));
        return manifest;
    }

    String imagePrompt(JobResponse job, int sceneIndex, int sceneCount) {
        return """
                Cinematic entertainment recap background image for a YouTube video.
                Subject: %s
                Prompt: %s
                Style: %s
                Scene %d of %d: %s
                Do not include readable text, captions, logos, actor likenesses, or platform UI.
                Leave clean central space because titles and captions will be rendered by FFmpeg.
                """.formatted(
                hasText(job.sourceTitle()) ? job.sourceTitle() : "movie or series topic",
                job.prompt(),
                job.mediaStyle(),
                sceneIndex,
                sceneCount,
                sceneDirection(sceneIndex)
        ).replaceAll("\\s+", " ").trim();
    }

    private String sceneDirection(int sceneIndex) {
        return switch ((sceneIndex - 1) % 4) {
            case 0 -> "wide establishing composition with cinematic lighting";
            case 1 -> "tense character-focused composition without recognizable actor likeness";
            case 2 -> "dynamic conflict or discovery moment with room for captions";
            default -> "final recommendation or cliffhanger mood with clean negative space";
        };
    }

    private boolean isVisualFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        return filename.endsWith(".png")
                || filename.endsWith(".jpg")
                || filename.endsWith(".jpeg")
                || filename.endsWith(".webp")
                || filename.endsWith(".bmp")
                || filename.endsWith(".mp4")
                || filename.endsWith(".mov")
                || filename.endsWith(".mkv")
                || filename.endsWith(".webm");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }
}
