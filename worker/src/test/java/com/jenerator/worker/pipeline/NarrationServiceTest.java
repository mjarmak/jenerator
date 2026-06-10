package com.jenerator.worker.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jenerator.worker.config.NarrationProperties;
import com.jenerator.worker.config.WorkerProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class NarrationServiceTest {
    private final NarrationService service = new NarrationService(
            new NarrationProperties("", "gpt-4o-mini-tts", "coral", "", "", "default", true),
            new WorkerProperties(true, "worker", "http://localhost:8080", "", "", 1000, Path.of("workspace"), "ffmpeg", ""),
            null,
            new ObjectMapper()
    );

    @Test
    void acceptsRawAudioBytesFromQwen() throws Exception {
        byte[] body = new byte[] { 73, 68, 51, 4, 0 };

        var payload = service.qwenAudioPayload("audio/mpeg", body);

        assertThat(payload.bytes()).containsExactly(body);
        assertThat(payload.extension()).isEqualTo("mp3");
    }

    @Test
    void acceptsFlatJsonBase64AudioFromQwen() throws Exception {
        String audio = Base64.getEncoder().encodeToString("audio-bytes".getBytes(StandardCharsets.UTF_8));
        byte[] body = """
                {
                  "audio": "%s",
                  "format": "wav"
                }
                """.formatted(audio).getBytes(StandardCharsets.UTF_8);

        var payload = service.qwenAudioPayload("application/json", body);

        assertThat(new String(payload.bytes(), StandardCharsets.UTF_8)).isEqualTo("audio-bytes");
        assertThat(payload.extension()).isEqualTo("wav");
    }

    @Test
    void acceptsNestedDataUriAudioFromQwen() throws Exception {
        String audio = Base64.getEncoder().encodeToString("nested-audio".getBytes(StandardCharsets.UTF_8));
        byte[] body = """
                {
                  "result": {
                    "audio_base64": "data:audio/wav;base64,%s"
                  }
                }
                """.formatted(audio).getBytes(StandardCharsets.UTF_8);

        var payload = service.qwenAudioPayload("", body);

        assertThat(new String(payload.bytes(), StandardCharsets.UTF_8)).isEqualTo("nested-audio");
        assertThat(payload.extension()).isEqualTo("wav");
    }

    @Test
    void returnsNullWhenJsonHasNoAudioPayload() throws Exception {
        byte[] body = """
                {
                  "status": "ok"
                }
                """.getBytes(StandardCharsets.UTF_8);

        assertThat(service.qwenAudioPayload("application/json", body)).isNull();
    }

    @Test
    void returnsNullWhenJsonAudioPayloadIsMalformed() throws Exception {
        byte[] body = """
                {
                  "audio": "not-really-base64"
                }
                """.getBytes(StandardCharsets.UTF_8);

        assertThat(service.qwenAudioPayload("application/json", body)).isNull();
    }
}
