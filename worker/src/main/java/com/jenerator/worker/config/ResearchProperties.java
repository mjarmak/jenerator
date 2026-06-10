package com.jenerator.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jenerator.research")
public record ResearchProperties(
        String tmdbApiToken,
        String tmdbLanguage,
        String tmdbTimeWindow
) {
    public ResearchProperties {
        tmdbLanguage = hasText(tmdbLanguage) ? tmdbLanguage : "en-US";
        tmdbTimeWindow = hasText(tmdbTimeWindow) ? tmdbTimeWindow : "week";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
