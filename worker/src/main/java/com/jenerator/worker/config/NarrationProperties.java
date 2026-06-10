package com.jenerator.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jenerator.tts")
public record NarrationProperties(
        String openaiApiKey,
        String openaiModel,
        String openaiVoice,
        String openaiInstructions,
        String qwenUrl,
        String qwenVoice,
        boolean fallbackToSilent
) {
    public NarrationProperties {
        openaiModel = hasText(openaiModel) ? openaiModel : "gpt-4o-mini-tts";
        openaiVoice = hasText(openaiVoice) ? openaiVoice : "coral";
        openaiInstructions = hasText(openaiInstructions)
                ? openaiInstructions
                : "Clear, energetic entertainment narrator. Keep pacing tight and pronunciation natural.";
        qwenVoice = hasText(qwenVoice) ? qwenVoice : "default";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
