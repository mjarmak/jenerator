package com.jenerator.worker.pipeline;

import com.jenerator.common.dto.JobResponse;
import com.jenerator.worker.config.WorkerProperties;
import com.jenerator.worker.model.AudioTrack;
import com.jenerator.worker.model.CaptionTrack;
import com.jenerator.worker.model.GeneratedScript;
import com.jenerator.worker.model.MediaPlan;
import com.jenerator.worker.model.RenderArtifact;
import com.jenerator.worker.model.RenderPreset;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class FfmpegRenderService {
    private final WorkerProperties properties;
    private final WorkerReadinessService readinessService;

    public FfmpegRenderService(WorkerProperties properties, WorkerReadinessService readinessService) {
        this.properties = properties;
        this.readinessService = readinessService;
    }

    public RenderArtifact render(
            JobResponse job,
            GeneratedScript script,
            RenderPreset preset,
            AudioTrack audio,
            MediaPlan media,
            CaptionTrack captions
    ) throws IOException, InterruptedException {
        Path jobDirectory = properties.workspacePath().resolve(job.id());
        Files.createDirectories(jobDirectory);
        Files.writeString(jobDirectory.resolve("script.txt"), script.narration());
        Files.writeString(jobDirectory.resolve("render-preset.txt"), preset.toString());

        if (!readinessService.ffmpegAvailable()) {
            Path manifest = jobDirectory.resolve("render-manifest.txt");
            Files.writeString(manifest, buildManifest(job, script, preset, media, captions));
            return new RenderArtifact(manifest, "RENDER_MANIFEST", manifest.toUri().toString(), Files.size(manifest));
        }

        Path output = jobDirectory.resolve("preview.mp4");
        List<String> command = buildCommand(output, script, preset, audio, media, captions);
        Files.writeString(jobDirectory.resolve("ffmpeg-command.txt"), String.join(" ", command));

        Process process = new ProcessBuilder(command)
                .directory(jobDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(jobDirectory.resolve("ffmpeg.log").toFile())
                .start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("FFmpeg render timed out.");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("FFmpeg render failed. See " + jobDirectory.resolve("ffmpeg.log"));
        }
        return new RenderArtifact(output, "PREVIEW_VIDEO", output.toUri().toString(), Files.size(output));
    }

    List<String> buildCommand(Path output, GeneratedScript script, RenderPreset preset, AudioTrack audio, MediaPlan media, CaptionTrack captions) {
        String headline = escapeDrawText(script.title());
        List<Path> slideshowVisuals = slideshowVisuals(media);
        int visualInputCount = slideshowVisuals.size() > 1 ? slideshowVisuals.size() : 1;
        int narrationInputIndex = visualInputCount;
        List<String> command = new ArrayList<>();
        command.add(properties.ffmpegPath());
        command.add("-y");
        addVisualInputs(command, preset, media == null ? null : media.visualPath(), slideshowVisuals);
        if (audio != null && audio.path() != null && isAudioFile(audio.path())) {
            command.add("-i");
            command.add(audio.path().toString());
        } else {
            command.add("-f");
            command.add("lavfi");
            command.add("-i");
            command.add("anullsrc=channel_layout=stereo:sample_rate=48000");
        }
        boolean hasMusic = media != null && media.musicPath() != null && isAudioFile(media.musicPath());
        if (hasMusic) {
            command.add("-stream_loop");
            command.add("-1");
            command.add("-i");
            command.add(media.musicPath().toString());
        }
        command.add("-filter_complex");
        command.add(videoFilters(headline, preset, captions, visualInputCount) + ";"
                + audioFilters(hasMusic, preset.durationSeconds(), narrationInputIndex, narrationInputIndex + 1));
        command.add("-map");
        command.add("[vout]");
        command.add("-map");
        command.add("[aout]");
        command.add("-t");
        command.add(Integer.toString(preset.durationSeconds()));
        command.add("-c:v");
        command.add("libx264");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-c:a");
        command.add("aac");
        command.add(output.toString());
        return command;
    }

    private String buildManifest(JobResponse job, GeneratedScript script, RenderPreset preset, MediaPlan media, CaptionTrack captions) {
        int visualCount = media == null || media.downloadedVisuals() == null ? 0 : media.downloadedVisuals().size();
        return """
                job=%s
                title=%s
                orientation=%s
                size=%dx%d
                duration=%d
                pacing=%s
                visual=%s
                visuals=%d
                music=%s
                captions=%s
                narration=%s
                """.formatted(
                job.id(),
                script.title(),
                job.orientation(),
                preset.width(),
                preset.height(),
                preset.durationSeconds(),
                preset.pacing(),
                media == null || media.visualPath() == null ? "" : media.visualPath(),
                visualCount,
                media == null || media.musicPath() == null ? "" : media.musicPath(),
                captions == null ? "" : captions.path(),
                script.narration()
        );
    }

    private String videoFilters(String headline, RenderPreset preset, CaptionTrack captions, int visualInputCount) {
        if (visualInputCount <= 1) {
            return "[0:v]" + scaleCropFilter(preset)
                    + ",drawtext=text='" + headline + "':fontcolor=white:fontsize=" + preset.captionFontSize()
                    + ":x=(w-text_w)/2:y=(h*0.38)-text_h"
                    + subtitleFilter(captions)
                    + ",format=yuv420p[vout]";
        }

        int segmentSeconds = slideshowSegmentSeconds(preset, visualInputCount);
        StringBuilder filter = new StringBuilder();
        for (int index = 0; index < visualInputCount; index += 1) {
            filter.append("[")
                    .append(index)
                    .append(":v]")
                    .append(scaleCropFilter(preset))
                    .append(",trim=duration=")
                    .append(segmentSeconds)
                    .append(",setpts=PTS-STARTPTS[v")
                    .append(index)
                    .append("];");
        }
        for (int index = 0; index < visualInputCount; index += 1) {
            filter.append("[v").append(index).append("]");
        }
        filter.append("concat=n=")
                .append(visualInputCount)
                .append(":v=1:a=0,trim=duration=")
                .append(preset.durationSeconds())
                .append(",setpts=PTS-STARTPTS,drawtext=text='")
                .append(headline)
                .append("':fontcolor=white:fontsize=")
                .append(preset.captionFontSize())
                .append(":x=(w-text_w)/2:y=(h*0.38)-text_h")
                .append(subtitleFilter(captions))
                .append(",format=yuv420p[vout]");
        return filter.toString();
    }

    private String scaleCropFilter(RenderPreset preset) {
        return "scale=" + preset.width() + ":" + preset.height() + ":force_original_aspect_ratio=increase,"
                + "crop=" + preset.width() + ":" + preset.height();
    }

    private String subtitleFilter(CaptionTrack captions) {
        if (captions == null || captions.path() == null) {
            return "";
        }
        String filename = captions.path().getFileName().toString()
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'");
        return ",subtitles='" + filename + "'";
    }

    private void addVisualInputs(List<String> command, RenderPreset preset, Path visualPath, List<Path> slideshowVisuals) {
        if (slideshowVisuals.size() > 1) {
            int segmentSeconds = slideshowSegmentSeconds(preset, slideshowVisuals.size());
            for (Path visual : slideshowVisuals) {
                command.add("-loop");
                command.add("1");
                command.add("-framerate");
                command.add(Integer.toString(preset.framesPerSecond()));
                command.add("-t");
                command.add(Integer.toString(segmentSeconds));
                command.add("-i");
                command.add(visual.toString());
            }
            return;
        }
        addVisualInput(command, preset, visualPath);
    }

    private void addVisualInput(List<String> command, RenderPreset preset, Path visualPath) {
        if (visualPath == null || !isVisualFile(visualPath)) {
            command.add("-f");
            command.add("lavfi");
            command.add("-i");
            command.add("color=c=0x111827:s=" + preset.width() + "x" + preset.height() + ":d=" + preset.durationSeconds() + ":r=" + preset.framesPerSecond());
            return;
        }
        if (isImageFile(visualPath)) {
            command.add("-loop");
            command.add("1");
            command.add("-framerate");
            command.add(Integer.toString(preset.framesPerSecond()));
            command.add("-i");
            command.add(visualPath.toString());
            return;
        }
        command.add("-stream_loop");
        command.add("-1");
        command.add("-i");
        command.add(visualPath.toString());
    }

    private String audioFilters(boolean hasMusic, int durationSeconds, int narrationInputIndex, int musicInputIndex) {
        if (!hasMusic) {
            return "[" + narrationInputIndex + ":a]anull[aout]";
        }
        return "[" + narrationInputIndex + ":a]volume=1.0[narr];[" + musicInputIndex + ":a]volume=0.18,atrim=0:" + durationSeconds
                + "[music];[narr][music]amix=inputs=2:duration=first:dropout_transition=2[aout]";
    }

    private int slideshowSegmentSeconds(RenderPreset preset, int visualCount) {
        return Math.max(1, (int) Math.ceil((double) preset.durationSeconds() / visualCount));
    }

    private List<Path> slideshowVisuals(MediaPlan media) {
        if (media == null || media.downloadedVisuals() == null) {
            return List.of();
        }
        return media.downloadedVisuals()
                .stream()
                .filter(this::isImageFile)
                .toList();
    }

    private String escapeDrawText(String text) {
        return text.replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace(",", "\\,");
    }

    private boolean isAudioFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        return filename.endsWith(".mp3")
                || filename.endsWith(".wav")
                || filename.endsWith(".m4a")
                || filename.endsWith(".aac")
                || filename.endsWith(".flac")
                || filename.endsWith(".ogg");
    }

    private boolean isImageFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        return filename.endsWith(".png")
                || filename.endsWith(".jpg")
                || filename.endsWith(".jpeg")
                || filename.endsWith(".webp")
                || filename.endsWith(".bmp");
    }

    private boolean isVisualFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        return isImageFile(path)
                || filename.endsWith(".mp4")
                || filename.endsWith(".mov")
                || filename.endsWith(".mkv")
                || filename.endsWith(".webm");
    }
}
