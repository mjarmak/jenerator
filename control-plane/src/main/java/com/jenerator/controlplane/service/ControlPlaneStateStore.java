package com.jenerator.controlplane.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jenerator.controlplane.domain.AssetRecord;
import com.jenerator.controlplane.domain.JobSnapshot;
import com.jenerator.controlplane.domain.StoredArtifactRecord;
import com.jenerator.controlplane.domain.StoredArtifactSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;

@Service
public class ControlPlaneStateStore {
    private final ObjectMapper objectMapper;
    private final Path stateDirectory;

    public ControlPlaneStateStore(
            ObjectMapper objectMapper,
            @Value("${jenerator.state-storage-path:data/state}") Path stateDirectory
    ) {
        this.objectMapper = objectMapper;
        this.stateDirectory = stateDirectory.toAbsolutePath().normalize();
    }

    public List<JobSnapshot> readJobs() {
        return readList(jobsPath(), new TypeReference<>() {
        });
    }

    public void writeJobs(Collection<JobSnapshot> jobs) {
        write(jobsPath(), jobs);
    }

    public List<AssetRecord> readAssets() {
        return readList(assetsPath(), new TypeReference<>() {
        });
    }

    public void writeAssets(Collection<AssetRecord> assets) {
        write(assetsPath(), assets);
    }

    public List<StoredArtifactRecord> readArtifacts() {
        return readList(artifactsPath(), new TypeReference<List<StoredArtifactSnapshot>>() {
        }).stream()
                .map(StoredArtifactSnapshot::toRecord)
                .toList();
    }

    public void writeArtifacts(Collection<StoredArtifactRecord> artifacts) {
        write(artifactsPath(), artifacts.stream().map(StoredArtifactSnapshot::from).toList());
    }

    private Path jobsPath() {
        return stateDirectory.resolve("jobs.json");
    }

    private Path assetsPath() {
        return stateDirectory.resolve("assets.json");
    }

    private Path artifactsPath() {
        return stateDirectory.resolve("artifacts.json");
    }

    private <T> List<T> readList(Path path, TypeReference<List<T>> type) {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(path.toFile(), type);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read state file: " + path, exception);
        }
    }

    private void write(Path path, Object value) {
        try {
            Files.createDirectories(stateDirectory);
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), value);
            moveIntoPlace(temp, path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write state file: " + path, exception);
        }
    }

    private void moveIntoPlace(Path temp, Path path) throws IOException {
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
