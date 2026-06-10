package com.jenerator.controlplane.controller;

import com.jenerator.controlplane.api.WorkerStatusResponse;
import com.jenerator.controlplane.service.WorkerRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/owner")
public class OwnerController {
    private final WorkerRegistry workerRegistry;

    public OwnerController(WorkerRegistry workerRegistry) {
        this.workerRegistry = workerRegistry;
    }

    @GetMapping("/workers")
    public List<WorkerStatusResponse> workers() {
        return workerRegistry.listStatuses();
    }
}
