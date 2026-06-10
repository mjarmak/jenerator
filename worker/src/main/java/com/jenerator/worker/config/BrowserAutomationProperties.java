package com.jenerator.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "jenerator.browser")
public record BrowserAutomationProperties(
        List<String> sourceCaptureCommand,
        int sourceCaptureShots,
        int sourceCaptureDelaySeconds
) {
    public BrowserAutomationProperties {
        sourceCaptureCommand = sourceCaptureCommand == null ? List.of() : List.copyOf(sourceCaptureCommand);
        sourceCaptureShots = sourceCaptureShots <= 0 ? 6 : sourceCaptureShots;
        sourceCaptureDelaySeconds = sourceCaptureDelaySeconds < 0 ? 0 : sourceCaptureDelaySeconds;
    }
}
