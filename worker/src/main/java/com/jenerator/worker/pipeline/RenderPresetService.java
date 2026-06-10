package com.jenerator.worker.pipeline;

import com.jenerator.common.dto.JobResponse;
import com.jenerator.common.model.Orientation;
import com.jenerator.worker.model.RenderPreset;
import org.springframework.stereotype.Service;

@Service
public class RenderPresetService {
    public RenderPreset forJob(JobResponse job) {
        int duration = job.durationPreset().targetSeconds();
        if (job.orientation() == Orientation.PORTRAIT_9_16) {
            return new RenderPreset(
                    1080,
                    1920,
                    duration,
                    30,
                    64,
                    "FAST_VERTICAL",
                    "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920"
            );
        }
        return new RenderPreset(
                1920,
                1080,
                duration,
                30,
                48,
                "WATCH_PAGE",
                "scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080"
        );
    }
}
