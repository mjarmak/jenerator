package com.jenerator.common.validation;

import com.jenerator.common.dto.CreateJobRequest;
import com.jenerator.common.model.DurationPreset;
import com.jenerator.common.model.Orientation;
import com.jenerator.common.model.PublishTarget;
import com.jenerator.common.model.VideoType;
import com.jenerator.common.model.VisualSource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public final class JobRules {
    private JobRules() {
    }

    public static List<String> validate(CreateJobRequest request) {
        List<String> errors = new ArrayList<>();
        if (request == null) {
            errors.add("Job request is required.");
            return errors;
        }

        if (request.publishTarget() == PublishTarget.YOUTUBE_SHORT
                && request.orientation() != Orientation.PORTRAIT_9_16) {
            errors.add("YouTube Shorts must use PORTRAIT_9_16 so YouTube classifies them as Shorts.");
        }

        if (request.publishTarget() == PublishTarget.YOUTUBE_VIDEO
                && request.orientation() != Orientation.LANDSCAPE_16_9) {
            errors.add("Use LANDSCAPE_16_9 for normal YouTube videos; portrait videos under three minutes may be classified as Shorts.");
        }

        if (request.publishTarget() == PublishTarget.YOUTUBE_SHORT
                && request.durationPreset() == DurationPreset.AUTO_SHORT
                && request.durationPreset().maxSeconds() > 180) {
            errors.add("YouTube Shorts must stay at or below three minutes.");
        }

        if (request.visualSource() == VisualSource.SOURCE_SCREENSHOTS
                && !hasText(request.sourceUrl())
                && request.visualAssetIds().isEmpty()) {
            errors.add("Source screenshots need a source URL or uploaded screenshot assets.");
        }

        if (request.visualSource() == VisualSource.UPLOADED_ASSETS
                && request.visualAssetIds().isEmpty()) {
            errors.add("Uploaded visuals mode needs at least one uploaded visual asset.");
        }

        if (hasText(request.sourceUrl()) && !isHttpUrl(request.sourceUrl())) {
            errors.add("Source URL must be an http or https URL.");
        }

        if (request.videoType() == VideoType.TOP_LIST
                && (request.listSize() < 3 || request.listSize() > 20)) {
            errors.add("Top list size must be between 3 and 20.");
        }

        switch (request.sourceScope()) {
            case MOVIE -> {
                if (request.seasonNumber() != null || request.episodeNumber() != null) {
                    errors.add("Movie jobs should not include season or episode numbers.");
                }
            }
            case SERIES_EPISODE -> {
                if (request.seasonNumber() == null || request.episodeNumber() == null) {
                    errors.add("Series episode jobs need both season and episode numbers.");
                }
            }
            case SERIES_SEASON -> {
                if (request.seasonNumber() == null) {
                    errors.add("Series season jobs need a season number.");
                }
                if (request.episodeNumber() != null) {
                    errors.add("Series season jobs should not include an episode number.");
                }
            }
            case SERIES_SHOW -> {
                if (request.seasonNumber() != null || request.episodeNumber() != null) {
                    errors.add("Whole-series jobs should not include season or episode numbers.");
                }
            }
        }

        return errors;
    }

    public static void requireValid(CreateJobRequest request) {
        List<String> errors = validate(request);
        if (!errors.isEmpty()) {
            throw new JobValidationException(errors);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
