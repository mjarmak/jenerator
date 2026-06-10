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

## Local Source Capture

Uploaded screenshots work without extra setup. For source URLs, configure the worker with a local capture command. A Playwright helper is included at `tools/source-capture-playwright`.

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
```

The helper can use a persistent browser profile with `--user-data-dir` if you need a signed-in local browser session. DRM-protected streams may still render black screenshots; in that case upload screenshots manually from your own playback source.

## YouTube Upload

Recommended default: keep `JENERATOR_YOUTUBE_MODE=DRY_RUN` while testing renders. For unattended uploads, the most reliable framework is the YouTube Data API from the local worker with a refresh token. If you specifically want to use your signed-in browser instead, use Playwright through `BROWSER` mode.

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

The browser helper opens YouTube Studio with a persistent local profile, fills title/description/tags/privacy, waits for manual review, then clicks Save or Publish.
