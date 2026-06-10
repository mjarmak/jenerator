package com.jenerator.worker.pipeline;

import com.jenerator.common.dto.JobResponse;
import com.jenerator.common.model.Orientation;
import com.jenerator.worker.config.WorkerProperties;
import com.jenerator.worker.model.CaptionCue;
import com.jenerator.worker.model.CaptionTrack;
import com.jenerator.worker.model.GeneratedScript;
import com.jenerator.worker.model.RenderPreset;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class CaptionService {
    private final WorkerProperties properties;

    public CaptionService(WorkerProperties properties) {
        this.properties = properties;
    }

    public CaptionTrack create(JobResponse job, GeneratedScript script, RenderPreset preset) throws IOException {
        Path jobDirectory = properties.workspacePath().resolve(job.id());
        Files.createDirectories(jobDirectory);
        List<CaptionCue> cues = cues(script.narration(), preset);
        Path path = jobDirectory.resolve("captions.ass");
        Files.writeString(path, ass(job, preset, cues));
        return new CaptionTrack(path, cues);
    }

    private List<CaptionCue> cues(String narration, RenderPreset preset) {
        List<String> chunks = chunks(narration, preset.pacing().equals("FAST_VERTICAL") ? 7 : 10);
        if (chunks.isEmpty()) {
            return List.of();
        }
        double cueDuration = Math.max(1.35, preset.durationSeconds() / (double) chunks.size());
        List<CaptionCue> cues = new ArrayList<>();
        double cursor = 0.0;
        for (String chunk : chunks) {
            double end = Math.min(preset.durationSeconds(), cursor + cueDuration);
            cues.add(new CaptionCue(cursor, end, chunk));
            cursor = end;
            if (cursor >= preset.durationSeconds()) {
                break;
            }
        }
        return cues;
    }

    private List<String> chunks(String text, int maxWords) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] words = normalized.split(" ");
        List<String> chunks = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String word : words) {
            if (count >= maxWords || builder.length() + word.length() > 54) {
                chunks.add(builder.toString());
                builder = new StringBuilder();
                count = 0;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(word);
            count++;
        }
        if (!builder.isEmpty()) {
            chunks.add(builder.toString());
        }
        return chunks;
    }

    private String ass(JobResponse job, RenderPreset preset, List<CaptionCue> cues) {
        boolean portrait = job.orientation() == Orientation.PORTRAIT_9_16;
        int fontSize = portrait ? preset.captionFontSize() : preset.captionFontSize() + 4;
        int marginV = portrait ? 250 : 92;
        int marginH = portrait ? 84 : 180;
        StringBuilder builder = new StringBuilder();
        builder.append("[Script Info]\n");
        builder.append("ScriptType: v4.00+\n");
        builder.append("PlayResX: ").append(preset.width()).append('\n');
        builder.append("PlayResY: ").append(preset.height()).append("\n\n");
        builder.append("[V4+ Styles]\n");
        builder.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n");
        builder.append("Style: Default,Arial,").append(fontSize).append(",&H00FFFFFF,&H00FFFFFF,&HDD000000,&HAA000000,-1,0,0,0,100,100,0,0,1,5,1,2,")
                .append(marginH).append(',').append(marginH).append(',').append(marginV).append(",1\n\n");
        builder.append("[Events]\n");
        builder.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");
        for (CaptionCue cue : cues) {
            builder.append("Dialogue: 0,")
                    .append(timestamp(cue.startSeconds()))
                    .append(',')
                    .append(timestamp(cue.endSeconds()))
                    .append(",Default,,0,0,0,,")
                    .append(escapeAss(cue.text()))
                    .append('\n');
        }
        return builder.toString();
    }

    private String timestamp(double seconds) {
        int centiseconds = (int) Math.round(seconds * 100);
        int cs = centiseconds % 100;
        int totalSeconds = centiseconds / 100;
        int s = totalSeconds % 60;
        int totalMinutes = totalSeconds / 60;
        int m = totalMinutes % 60;
        int h = totalMinutes / 60;
        return "%d:%02d:%02d.%02d".formatted(h, m, s, cs);
    }

    private String escapeAss(String value) {
        return value.replace("\\", "\\\\")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("\n", "\\N");
    }
}
