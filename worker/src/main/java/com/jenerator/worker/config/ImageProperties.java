package com.jenerator.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jenerator.images")
public record ImageProperties(
        String openaiApiKey,
        String openaiModel,
        String quality,
        String outputFormat,
        int generatedCount,
        boolean fallbackToManifest
) {
    public ImageProperties {
        openaiModel = hasText(openaiModel) ? openaiModel : "gpt-image-2";
        quality = hasText(quality) ? quality : "low";
        outputFormat = hasText(outputFormat) ? outputFormat : "png";
        generatedCount = generatedCount <= 0 ? 4 : Math.min(generatedCount, 8);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
