package com.jenerator.controlplane.api;

import java.util.List;

public record SettingsResponse(
        String apiBaseUrl,
        List<String> supportedOrientations,
        List<String> supportedDurationPresets,
        List<String> supportedPublishTargets,
        List<String> supportedVoiceProviders,
        List<String> supportedContentCategories,
        List<String> supportedEditorialWindows,
        List<String> supportedVisualSources,
        String youtubeUploadWarning
) {
}
