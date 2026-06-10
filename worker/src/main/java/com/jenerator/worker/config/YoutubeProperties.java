package com.jenerator.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "jenerator.youtube")
public record YoutubeProperties(
        UploadMode mode,
        String clientId,
        String clientSecret,
        String refreshToken,
        List<String> browserUploadCommand,
        String privacyStatus
) {
    public YoutubeProperties {
        mode = mode == null ? UploadMode.DRY_RUN : mode;
        browserUploadCommand = browserUploadCommand == null ? List.of() : List.copyOf(browserUploadCommand);
        privacyStatus = privacyStatus == null || privacyStatus.isBlank() ? "private" : privacyStatus;
    }

    public enum UploadMode {
        DRY_RUN,
        API,
        BROWSER
    }
}
