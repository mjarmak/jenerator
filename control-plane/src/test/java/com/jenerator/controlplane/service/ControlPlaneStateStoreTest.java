package com.jenerator.controlplane.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.jenerator.controlplane.domain.AssetRecord;
import com.jenerator.controlplane.domain.StoredArtifactRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ControlPlaneStateStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsAssetsAndArtifacts() {
        ControlPlaneStateStore store = new ControlPlaneStateStore(JsonMapper.builder().findAndAddModules().build(), tempDir);
        AssetRecord asset = new AssetRecord("asset-1", "music.mp3", "audio/mpeg", 123, "data/assets/music.mp3", Instant.now());
        StoredArtifactRecord artifact = new StoredArtifactRecord("artifact-1", "job-1", "preview.mp4", "video/mp4", Path.of("data/artifacts/preview.mp4"));

        store.writeAssets(List.of(asset));
        store.writeArtifacts(List.of(artifact));

        assertThat(store.readAssets()).containsExactly(asset);
        assertThat(store.readArtifacts()).containsExactly(artifact);
    }
}
