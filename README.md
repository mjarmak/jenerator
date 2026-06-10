# Jenerator

Jenerator is a cloud-controlled, local-worker video generation app for YouTube movie and series content.

The cloud app lets you create configurable video jobs from a phone or desktop. A local PC worker polls for jobs, renders videos with FFmpeg, and uploads after review.

## Projects

- `frontend` - Angular 22 + Angular Material cloud UI.
- `common` - Shared Java contracts and validation rules.
- `control-plane` - Spring Boot 4 API for jobs, assets, worker polling, previews, and approvals.
- `worker` - Local Spring Boot worker for rendering and upload orchestration.

## Toolchain

- Node.js compatible with Angular 22: `^22.22.3 || ^24.15.0 || ^26.0.0`
- Java 25
- Maven 3.6.3+
- FFmpeg for local rendering

The current checked-in code targets Java 25 and Angular 22. If your machine still has older Node or Java on `PATH`, install the required versions before building.

## Local Run

Set different values before exposing this beyond your machine:

- `JENERATOR_OWNER_API_KEY` protects phone/desktop job and asset controls.
- `JENERATOR_WORKER_PAIRING_CODE` lets the local PC worker pair with the control plane.
- `JENERATOR_TTS_OPENAI_API_KEY` enables OpenAI TTS for jobs that select `OPENAI_TTS`.
- `JENERATOR_TTS_QWEN_URL` points to a local Qwen-compatible TTS HTTP endpoint for jobs that select `QWEN_LOCAL`.
- `JENERATOR_IMAGES_OPENAI_API_KEY` enables generated background images for jobs that select AI images.
- `JENERATOR_SCRIPT_OPENAI_API_KEY` enables OpenAI script generation from the prompt, source, and research brief.
- `JENERATOR_RESEARCH_TMDB_API_TOKEN` enables trending movie/series context for generated scripts.
- `JENERATOR_YOUTUBE_MODE=DRY_RUN` keeps uploads disabled until local YouTube credentials are configured on the worker.

Useful commands:

```powershell
mvn -q clean test
cd frontend
npm install
npm run build
```

For a full local stack:

```powershell
docker compose -f docker-compose.local.yml up --build
```

Open the frontend, enter the owner key, create a configured job, wait for the worker to render a preview, then approve upload from the review block.

YouTube OAuth secrets are only used by the worker. The cloud UI does not require Google login; it only needs the owner key.

Upload approval is guarded in both the UI and control plane. A job must have a rendered preview video plus nonblank title, description, tags, and citations before it can move into the local worker upload queue.

Jobs can be cancelled from the owner UI while queued, rendering, waiting for approval, or approved for upload. Cancellation is sticky in the control plane: late updates from a worker that was already rendering are ignored so a cancelled job does not return to the approval queue. The worker also re-checks job status between expensive stages and before artifact/upload completion so a cancelled job stops before the next research, TTS, media, FFmpeg, or YouTube step.

## Worker Readiness

The local worker registers with the pairing code, sends heartbeat capability details, and appears in the Create Video screen after you enter the owner key. The owner-only endpoint `GET /api/owner/workers` reports whether each PC worker is online, has FFmpeg, can reach local Qwen TTS, and has YouTube upload configured. Worker tokens are never returned to the UI.

Worker readiness also affects scheduling. Render jobs are only claimed by workers that report FFmpeg available, and approved uploads are only claimed by workers that report YouTube upload configured. This prevents a phone-created job from getting stuck on a PC that can poll but cannot render or upload.

If the control plane restarts and forgets in-memory worker tokens, the worker automatically clears the rejected token, pairs again with `JENERATOR_WORKER_PAIRING_CODE`, and retries the failed worker request once.

## Persistence

The control plane writes restart-safe JSON state under `jenerator.state-storage-path` (`data/state` by default). Uploaded asset files live under `data/assets`, preview artifacts under `data/artifacts`, and the Docker Compose file mounts `/app/data` so jobs, uploads, previews, and approvals survive container restarts. Jobs that were `CLAIMED`, `RUNNING`, or `UPLOADING` during a restart are returned to a claimable state so the worker can resume them.

## Rendering

The worker uses FFmpeg as the open-source editor. Images or captured screenshots provide the visual background, generated narration is mixed with optional uploaded music, and captions are produced as timed ASS subtitle files by code before being burned into the video. This keeps on-screen text editable and deterministic instead of asking an image model to draw text.

For local Qwen TTS, the worker accepts raw audio responses (`audio/mpeg`, `audio/wav`, or `application/octet-stream`) and JSON envelopes containing base64 audio fields such as `audio`, `audio_data`, `audio_base64`, `audioBase64`, `b64_json`, or nested equivalents. If Qwen is unavailable and `jenerator.tts.fallback-to-silent=true`, the worker creates a silent placeholder so the rest of the render pipeline can still be reviewed.

For AI image mode, the worker generates multiple text-free background images (`jenerator.images.generated-count`, default `4`, maximum `8`) with varied scene prompts, uploads them as generated visual artifacts, and renders them through the same FFmpeg slideshow path used for screenshots.

## Job Targeting

Each job can target movies, series, or both; a week, month, or year editorial window; a research focus of `TRENDING`, `GROSSING`, `NEW_RELEASES`, or `POPULAR`; and a list size from 3 to 20. The worker uses those fields with TMDB trending, discover, revenue-sorted movie discovery, and popular endpoints when `JENERATOR_RESEARCH_TMDB_API_TOKEN` is configured, then passes the research brief into script generation. Trending uses TMDB's `day` or `week` window, with `week` as the safe default. TV has no direct box-office grossing equivalent, so mixed grossing jobs combine revenue-sorted movies with popular series context.

If OpenAI script generation is not configured or fails and `jenerator.script.fallback-to-template=true`, the worker still creates a duration-bounded draft. The fallback script uses different structures for summaries, recaps, top lists, and recommendations, and folds in any available TMDB research titles for top-list jobs.

`POST /api/jobs` accepts the main video configuration in one payload: `prompt`, `videoType`, `orientation`, `durationPreset`, `publishTarget`, `voiceProvider`, `mediaStyle`, `contentCategory`, `editorialWindow`, `researchFocus`, `listSize`, `visualSource`, optional `sourceUrl`/`sourceTitle`, `sourceScope` (`MOVIE`, `SERIES_EPISODE`, `SERIES_SEASON`, or `SERIES_SHOW`), optional season/episode numbers, `musicAssetId`, uploaded visual asset ids, and target metadata.

## Local Source Capture

Uploaded screenshots work without extra setup. For source URLs, configure the worker with a local capture command. A Playwright helper is included at `tools/source-capture-playwright`.

Use `sourceScope` to tell the script generator what the link or screenshots represent: a movie, one series episode, a season, or the whole series. Episode jobs require both season and episode numbers; season jobs require only season number; movie and whole-series jobs should leave season and episode blank.

```powershell
cd tools/source-capture-playwright
npm install
npx playwright install chromium
```

Then configure the worker with a command list similar to:

```yaml
jenerator:
  browser:
    source-capture-command:
      - node
      - C:/Projects/jenerator/tools/source-capture-playwright/capture.mjs
    source-capture-shots: 8
    source-capture-manual-seconds: 45
    source-capture-auto-play: true
    source-capture-skip-key: ArrowRight
    source-capture-skip-count: 2
    source-capture-user-data-dir: C:/Projects/jenerator/data/source-browser-profile
    source-capture-channel: chrome
    source-capture-headless: false
```

The helper can use a persistent browser profile when you need a signed-in local browser session. Set `source-capture-user-data-dir` to a dedicated local profile folder, open the streaming site once on the PC to sign in, then reuse that profile for jobs. The worker passes viewport size, shot count, interval, manual wait time, auto-play, skip-key, browser channel, headless mode, and profile settings to the helper. DRM-protected streams may still render black screenshots; in that case upload screenshots manually from your own playback source.

When multiple image screenshots are prepared, the worker renders them as an FFmpeg slideshow before burning in generated title text and captions.

## YouTube Upload

Recommended default: keep `JENERATOR_YOUTUBE_MODE=DRY_RUN` while testing renders. For unattended uploads, the most reliable framework is the YouTube Data API from the local worker with a refresh token. If you specifically want to use your signed-in browser instead, use Playwright through `BROWSER` mode.

For API upload, create a Google OAuth client that can use a loopback redirect URI such as `http://127.0.0.1:53682/oauth2callback`, then run the local helper on the worker PC. The helper requests only the YouTube upload scope, listens locally for the redirect, exchanges the code for a refresh token, and can write a worker env file. This OAuth flow is only for the PC worker upload process; it is separate from the Jenerator owner login.

```powershell
cd tools/youtube-oauth
npm run start -- --credentials C:/secrets/youtube-client.json --open true --out C:/secrets/jenerator-youtube.env
```

You can also pass `--client-id` and optional `--client-secret` directly instead of `--credentials`. Keep the generated env file outside the repository.

When an approved job is uploaded in API or browser mode, the worker uses its local `preview.mp4` if present. If the local worker workspace was cleared or a different worker claims the upload, it downloads the approved `PREVIEW_VIDEO` artifact from the control plane before uploading.

Dry-run and browser upload payload JSON files are uploaded back to the control plane as downloadable job artifacts, so the phone UI never points at private `file:` paths on the worker PC.

Before any dry-run, API, or browser upload, the worker normalizes YouTube metadata to YouTube Data API limits: title is capped at 100 characters, description at 5000 UTF-8 bytes, and tags at 500 calculated characters including commas and quoted multi-word tags. The worker also strips `<` and `>` from title/description because YouTube rejects them. Reference: `https://developers.google.com/youtube/v3/docs/videos`.

```powershell
cd tools/youtube-browser-upload
npm install
npx playwright install chromium
```

Worker config example:

```yaml
jenerator:
  youtube:
    mode: BROWSER
    browser-upload-command:
      - node
      - C:/Projects/jenerator/tools/youtube-browser-upload/upload.mjs
      - --user-data-dir
      - C:/Projects/jenerator/data/youtube-profile
```

The browser helper opens YouTube Studio with a persistent local profile, fills title/description/tags/privacy, applies Made for Kids and synthetic-media disclosure choices when the matching YouTube Studio controls are available, waits for manual review, then clicks Save or Publish.
