package com.jenerator.controlplane.service;

import com.jenerator.controlplane.api.AssetResponse;
import com.jenerator.controlplane.domain.AssetRecord;
import com.jenerator.controlplane.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AssetService {
    private final Path storageDirectory;
    private final ConcurrentMap<String, AssetRecord> assets = new ConcurrentHashMap<>();

    public AssetService(@Value("${jenerator.asset-storage-path:data/assets}") Path storageDirectory) {
        this.storageDirectory = storageDirectory.toAbsolutePath().normalize();
    }

    public AssetResponse store(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalStateException("Asset file is empty.");
        }
        Files.createDirectories(storageDirectory);
        String id = UUID.randomUUID().toString();
        String originalName = file.getOriginalFilename() == null ? "asset.bin" : file.getOriginalFilename();
        String safeName = originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        Path target = storageDirectory.resolve(id + "-" + safeName).normalize();
        if (!target.startsWith(storageDirectory)) {
            throw new IllegalStateException("Asset path resolved outside the storage directory.");
        }
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        AssetRecord record = new AssetRecord(
                id,
                safeName,
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                file.getSize(),
                target.toString(),
                Instant.now()
        );
        assets.put(id, record);
        return toResponse(record);
    }

    public List<AssetResponse> list() {
        return assets.values()
                .stream()
                .sorted(Comparator.comparing(AssetRecord::createdAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    public ResponseEntity<Resource> load(String id) {
        AssetRecord record = assets.get(id);
        if (record == null) {
            throw new NotFoundException("Asset not found: " + id);
        }
        try {
            Resource resource = new UrlResource(Path.of(record.storagePath()).toUri());
            if (!resource.exists()) {
                throw new NotFoundException("Asset file not found: " + id);
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(record.contentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + record.filename() + "\"")
                    .body(resource);
        } catch (MalformedURLException exception) {
            throw new IllegalStateException("Asset file URL is invalid.", exception);
        }
    }

    private AssetResponse toResponse(AssetRecord record) {
        return new AssetResponse(
                record.id(),
                record.filename(),
                record.contentType(),
                record.sizeBytes(),
                "/api/assets/" + record.id() + "/file",
                record.createdAt()
        );
    }
}
