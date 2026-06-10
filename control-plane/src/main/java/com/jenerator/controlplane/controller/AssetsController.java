package com.jenerator.controlplane.controller;

import com.jenerator.controlplane.api.AssetResponse;
import com.jenerator.controlplane.service.AssetService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/assets")
public class AssetsController {
    private final AssetService assetService;

    public AssetsController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public List<AssetResponse> list() {
        return assetService.list();
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        return assetService.load(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssetResponse upload(@RequestParam("file") MultipartFile file) throws IOException {
        return assetService.store(file);
    }
}
