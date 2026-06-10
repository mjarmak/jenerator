package com.jenerator.worker.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Base64;
import java.util.Iterator;
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
                .header("Accept", "audio/mpeg,audio/wav,audio/x-wav,application/octet-stream,application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)))
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return fallback(jobDirectory, script, "Qwen local TTS request failed with status " + response.statusCode() + ".");
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        AudioPayload audio = qwenAudioPayload(contentType, response.body());
        if (audio == null || audio.bytes().length == 0) {
            return fallback(jobDirectory, script, "Qwen local TTS response did not include audio data.");
        }
        Path output = jobDirectory.resolve("narration-qwen." + audio.extension());
        Files.write(output, audio.bytes());
        return new AudioTrack(output, "NARRATION_AUDIO", "Generated narration with local Qwen TTS.");
    }

    AudioPayload qwenAudioPayload(String contentType, byte[] body) throws IOException {
        if (body == null || body.length == 0) {
            return null;
        }
        if (looksLikeJson(contentType, body)) {
            JsonNode root = objectMapper.readTree(body);
            JsonAudio jsonAudio = findJsonAudio(root);
            if (jsonAudio == null || !hasText(jsonAudio.base64())) {
                return null;
            }
            try {
                return new AudioPayload(Base64.getDecoder().decode(stripDataUri(jsonAudio.base64())), extension(jsonAudio.format(), contentType));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
        return new AudioPayload(body, extension("", contentType));
    }

    private JsonAudio findJsonAudio(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            String format = firstText(
                    node.path("format").asText(),
                    node.path("mime_type").asText(),
                    node.path("content_type").asText()
            );
            for (String field : List.of("audio", "audio_data", "audioData", "audio_base64", "audioBase64", "b64_json", "content")) {
                String value = node.path(field).asText();
                if (hasText(value) && looksLikeBase64Audio(value)) {
                    return new JsonAudio(value, firstText(format, dataUriMime(value)));
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonAudio nested = findJsonAudio(entry.getValue());
                if (nested != null) {
                    return hasText(nested.format()) ? nested : new JsonAudio(nested.base64(), format);
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonAudio nested = findJsonAudio(child);
                if (nested != null) {
                    return nested;
                }
            }
        }
        if (node.isTextual() && looksLikeBase64Audio(node.asText())) {
            return new JsonAudio(node.asText(), dataUriMime(node.asText()));
        }
        return null;
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

    private boolean looksLikeJson(String contentType, byte[] body) {
        if (contentType != null && contentType.toLowerCase().contains("json")) {
            return true;
        }
        int index = 0;
        while (index < body.length && Character.isWhitespace(body[index])) {
            index++;
        }
        return index < body.length && (body[index] == '{' || body[index] == '[');
    }

    private boolean looksLikeBase64Audio(String value) {
        String normalized = stripDataUri(value).replaceAll("\\s+", "");
        return normalized.length() >= 8 && normalized.matches("[A-Za-z0-9+/]+={0,2}");
    }

    private String stripDataUri(String value) {
        String trimmed = value.trim();
        int comma = trimmed.indexOf(',');
        if (trimmed.startsWith("data:") && comma >= 0) {
            return trimmed.substring(comma + 1);
        }
        return trimmed.replaceAll("\\s+", "");
    }

    private String dataUriMime(String value) {
        String trimmed = value.trim();
        int semicolon = trimmed.indexOf(';');
        if (trimmed.startsWith("data:") && semicolon > "data:".length()) {
            return trimmed.substring("data:".length(), semicolon);
        }
        return "";
    }

    private String extension(String format, String contentType) {
        String lower = firstText(format, contentType).toLowerCase();
        if (lower.contains("wav")) {
            return "wav";
        }
        if (lower.contains("mpeg") || lower.contains("mp3")) {
            return "mp3";
        }
        if (lower.contains("ogg")) {
            return "ogg";
        }
        if (lower.contains("flac")) {
            return "flac";
        }
        return "mp3";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    record AudioPayload(byte[] bytes, String extension) {
    }

    private record JsonAudio(String base64, String format) {
    }
}
