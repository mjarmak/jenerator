package com.jenerator.worker.model;

import java.nio.file.Path;
import java.util.List;

public record ResearchBrief(
        String summary,
        List<String> trendingTitles,
        List<String> citations,
        Path artifactPath
) {
}
