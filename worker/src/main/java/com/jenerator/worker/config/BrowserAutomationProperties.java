package com.jenerator.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "jenerator.browser")
public record BrowserAutomationProperties(
        List<String> sourceCaptureCommand,
        int sourceCaptureShots,
        int sourceCaptureDelaySeconds,
        int sourceCaptureIntervalSeconds,
        int sourceCaptureManualSeconds,
        boolean sourceCaptureAutoPlay,
        String sourceCaptureSkipKey,
        int sourceCaptureSkipCount,
        String sourceCaptureUserDataDir,
        String sourceCaptureChannel,
        boolean sourceCaptureHeadless
) {
    public BrowserAutomationProperties {
        sourceCaptureCommand = sourceCaptureCommand == null ? List.of() : List.copyOf(sourceCaptureCommand);
        sourceCaptureShots = sourceCaptureShots <= 0 ? 6 : sourceCaptureShots;
        sourceCaptureDelaySeconds = sourceCaptureDelaySeconds < 0 ? 0 : sourceCaptureDelaySeconds;
        sourceCaptureIntervalSeconds = sourceCaptureIntervalSeconds <= 0 ? 4 : sourceCaptureIntervalSeconds;
        sourceCaptureManualSeconds = sourceCaptureManualSeconds < 0 ? 0 : sourceCaptureManualSeconds;
        sourceCaptureSkipKey = sourceCaptureSkipKey == null ? "" : sourceCaptureSkipKey.trim();
        sourceCaptureSkipCount = sourceCaptureSkipCount < 0 ? 0 : sourceCaptureSkipCount;
        sourceCaptureUserDataDir = sourceCaptureUserDataDir == null ? "" : sourceCaptureUserDataDir.trim();
        sourceCaptureChannel = sourceCaptureChannel == null ? "" : sourceCaptureChannel.trim();
    }
}
