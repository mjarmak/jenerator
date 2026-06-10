package com.jenerator.worker.client;

import com.jenerator.common.dto.WorkerHeartbeatRequest;
import com.jenerator.common.model.JobStatus;
import com.jenerator.worker.config.WorkerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ControlPlaneClientTest {
    @Test
    void rePairsAndRetriesWhenCachedWorkerTokenIsRejected() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ControlPlaneClient client = new ControlPlaneClient(properties(), builder);

        server.expect(requestTo("http://control-plane/api/workers/register"))
                .andRespond(withSuccess(registration("worker-1", "stale-token"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://control-plane/api/workers/heartbeat"))
                .andExpect(header("X-Worker-Token", "stale-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("http://control-plane/api/workers/register"))
                .andRespond(withSuccess(registration("worker-2", "fresh-token"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://control-plane/api/workers/heartbeat"))
                .andExpect(header("X-Worker-Token", "fresh-token"))
                .andRespond(withSuccess());

        client.heartbeat(new WorkerHeartbeatRequest("worker", true, false, true, Map.of()));

        server.verify();
    }

    @Test
    void getsCurrentJobStatusWithWorkerToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ControlPlaneClient client = new ControlPlaneClient(properties("worker-token", null), builder);

        server.expect(requestTo("http://control-plane/api/workers/jobs/job-1"))
                .andExpect(header("X-Worker-Token", "worker-token"))
                .andRespond(withSuccess(jobResponse(JobStatus.CANCELLED), MediaType.APPLICATION_JSON));

        assertThat(client.getJob("job-1").status()).isEqualTo(JobStatus.CANCELLED);

        server.verify();
    }

    private WorkerProperties properties() {
        return properties(null, "pair");
    }

    private WorkerProperties properties(String token, String pairingCode) {
        return new WorkerProperties(
                true,
                "worker",
                "http://control-plane",
                token,
                pairingCode,
                1000,
                Path.of("workspace"),
                "ffmpeg",
                null
        );
    }

    private String registration(String workerId, String token) {
        return """
                {
                  "workerId": "%s",
                  "workerToken": "%s"
                }
                """.formatted(workerId, token);
    }

    private String jobResponse(JobStatus status) {
        return """
                {
                  "id": "job-1",
                  "prompt": "Create a recap",
                  "videoType": "RECAP",
                  "orientation": "PORTRAIT_9_16",
                  "durationPreset": "ONE_MINUTE",
                  "publishTarget": "YOUTUBE_SHORT",
                  "voiceProvider": "OPENAI_TTS",
                  "mediaStyle": "CINEMATIC_RECAP",
                  "contentCategory": "MOVIES_AND_SERIES",
                  "editorialWindow": "WEEK",
                  "listSize": 8,
                  "visualSource": "AI_GENERATED_IMAGES",
                  "sourceUrl": null,
                  "sourceTitle": null,
                  "seasonNumber": null,
                  "episodeNumber": null,
                  "musicAssetId": null,
                  "visualAssetIds": [],
                  "assetIds": [],
                  "targetMetadata": {},
                  "status": "%s",
                  "width": 1080,
                  "height": 1920,
                  "targetSeconds": 60,
                  "title": "Example",
                  "description": "Example",
                  "tags": [],
                  "citations": [],
                  "madeForKids": false,
                  "containsSyntheticMedia": true,
                  "error": null,
                  "createdAt": "2026-06-10T00:00:00Z",
                  "updatedAt": "2026-06-10T00:00:00Z",
                  "steps": [],
                  "artifacts": []
                }
                """.formatted(status);
    }
}
