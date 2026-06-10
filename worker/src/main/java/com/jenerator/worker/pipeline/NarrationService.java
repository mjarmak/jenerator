package com.jenerator.worker.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jenerator.common.dto.JobResponse;
import com.jenerator.common.model.VoiceProvider;
import com.jenerator.worker.config.NarrationProperties;
import com.jenerator.worker.config.WorkerProperties;
import com.jenerator.worker.model.AudioTrack;
import com.jenerator.worker.model.GeneratedScript;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class NarrationService {
    private final NarrationProperties properties;
    private final WorkerProperties workerProperties;
    private final WorkerReadinessService readinessService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public NarrationService(
            NarrationProperties properties,
            WorkerProperties workerProperties,
            WorkerReadinessService readinessService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.workerProperties = workerProperties;
        this.readinessService = readinessService;
        this.objectMapper = objectMapper;
    }

    public AudioTrack generate(JobResponse job, GeneratedScript script) throws IOException, InterruptedException {
        Path jobDirectory = workerProperties.workspacePath().resolve(job.id());
        Files.createDirectories(jobDirectory);
        Files.writeString(jobDirectory.resolve("narration.txt"), script.narration());
        if (job.voiceProvider() == VoiceProvider.OPENAI_TTS) {
            return openAi(jobDirectory, script);
        }
        return qwen(jobDirectory, script);
    }

    private AudioTrack openAi(Path jobDirectory, GeneratedScript script) throws IOException, InterruptedException {
        if (!hasText(properties.openaiApiKey())) {
            return fallback(jobDirectory, script, "OpenAI TTS key is not configured.");
        }
        Map<String, String> payload = Map.of(
                "model", properties.openaiModel(),
                "voice", properties.openaiVoice(),
                "input", script.narration(),
                "instructions", properties.openaiInstructions(),
                "response_format", "mp3"
        );
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/audio/speech"))
                .header("Authorization", "Bearer " + properties.openaiApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)))
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return fallback(jobDirectory, script, "OpenAI TTS request failed with status " + response.statusCode() + ".");
        }
        Path output = jobDirectory.resolve("narration-openai.mp3");
        Files.write(output, response.body());
        return new AudioTrack(output, "NARRATION_AUDIO", "Generated narration with OpenAI TTS.");
    }

    private AudioTrack qwen(Path jobDirectory, GeneratedScript script) throws IOException, InterruptedException {
        if (!hasText(properties.qwenUrl())) {
            return fallback(jobDirectory, script, "Qwen local TTS URL is not configured.");
        }
        Map<String, String> payload = Map.of(
                "text", script.narration(),
                "voice", properties.qwenVoice(),
                "format", "mp3"
        );
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.qwenUrl()))
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg,application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)))
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return fallback(jobDirectory, script, "Qwen local TTS request failed with status " + response.statusCode() + ".");
        }
        Path output = jobDirectory.resolve("narration-qwen.mp3");
        Files.write(output, response.body());
        return new AudioTrack(output, "NARRATION_AUDIO", "Generated narration with local Qwen TTS.");
    }

    private AudioTrack fallback(Path jobDirectory, GeneratedScript script, String reason) throws IOException, InterruptedException {
        if (!properties.fallbackToSilent()) {
            throw new IllegalStateException(reason);
        }
        if (readinessService.ffmpegAvailable()) {
            Path output = jobDirectory.resolve("narration-placeholder.mp3");
            List<String> command = new ArrayList<>();
            command.add(workerProperties.ffmpegPath());
            command.add("-y");
            command.add("-f");
            command.add("lavfi");
            command.add("-i");
            command.add("anullsrc=channel_layout=stereo:sample_rate=48000");
            command.add("-t");
            command.add(Integer.toString(script.estimatedSeconds()));
            command.add("-q:a");
            command.add("9");
            command.add("-acodec");
            command.add("libmp3lame");
            command.add(output.toString());
            Process process = new ProcessBuilder(command)
                    .directory(jobDirectory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(jobDirectory.resolve("tts-placeholder.log").toFile())
                    .start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Timed out while generating silent narration placeholder.");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Failed to generate silent narration placeholder.");
            }
            return new AudioTrack(output, "NARRATION_PLACEHOLDER", reason + " Silent placeholder was generated.");
        }
        Path output = jobDirectory.resolve("narration-placeholder.txt");
        Files.writeString(output, reason + System.lineSeparator() + script.narration());
        return new AudioTrack(output, "NARRATION_PLACEHOLDER", reason + " Script placeholder was generated.");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
