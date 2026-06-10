package com.jenerator.controlplane.controller;

import com.jenerator.common.model.ContentCategory;
import com.jenerator.common.model.DurationPreset;
import com.jenerator.common.model.EditorialWindow;
import com.jenerator.common.model.Orientation;
import com.jenerator.common.model.PublishTarget;
import com.jenerator.common.model.VisualSource;
import com.jenerator.common.model.VoiceProvider;
import com.jenerator.controlplane.api.SettingsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private static final String YOUTUBE_UPLOAD_WARNING = "By clicking 'upload,' you certify that the content you are uploading complies with the YouTube Terms of Service, including the YouTube Community Guidelines. Please be sure not to violate others' copyright or privacy rights.";

    private final String apiBaseUrl;

    public SettingsController(@Value("${jenerator.public-api-base-url:http://localhost:8080}") String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    @GetMapping
    public SettingsResponse get() {
        return new SettingsResponse(
                apiBaseUrl,
                Arrays.stream(Orientation.values()).map(Enum::name).toList(),
                Arrays.stream(DurationPreset.values()).map(Enum::name).toList(),
                Arrays.stream(PublishTarget.values()).map(Enum::name).toList(),
                Arrays.stream(VoiceProvider.values()).map(Enum::name).toList(),
                Arrays.stream(ContentCategory.values()).map(Enum::name).toList(),
                Arrays.stream(EditorialWindow.values()).map(Enum::name).toList(),
                Arrays.stream(VisualSource.values()).map(Enum::name).toList(),
                YOUTUBE_UPLOAD_WARNING
        );
    }
}
