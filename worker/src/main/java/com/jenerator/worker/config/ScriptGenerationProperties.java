package com.jenerator.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jenerator.script")
public record ScriptGenerationProperties(
        String openaiApiKey,
        String openaiModel,
        int maxOutputTokens,
        boolean fallbackToTemplate
) {
    public ScriptGenerationProperties {
        openaiModel = hasText(openaiModel) ? openaiModel : "gpt-4.1-mini";
        maxOutputTokens = maxOutputTokens <= 0 ? 1_800 : maxOutputTokens;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
