import { CommonModule } from '@angular/common';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { interval } from 'rxjs';

type VideoType = 'SUMMARY' | 'RECAP' | 'TOP_LIST' | 'RECOMMENDATION';
type Orientation = 'PORTRAIT_9_16' | 'LANDSCAPE_16_9';
type DurationPreset = 'ONE_MINUTE' | 'AUTO_SHORT';
type PublishTarget = 'YOUTUBE_SHORT' | 'YOUTUBE_VIDEO';
type VoiceProvider = 'OPENAI_TTS' | 'QWEN_LOCAL';
type MediaStyle = 'CINEMATIC_RECAP' | 'FAST_NEWS' | 'CLEAN_RECOMMENDATION' | 'TOP_LIST_COUNTDOWN';
type VisualSource = 'SOURCE_SCREENSHOTS' | 'AI_GENERATED_IMAGES' | 'UPLOADED_ASSETS';
type JobStatus = 'QUEUED' | 'CLAIMED' | 'RUNNING' | 'WAITING_FOR_APPROVAL' | 'APPROVED_FOR_UPLOAD' | 'UPLOADING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

interface CreateJobRequest {
  prompt: string;
  videoType: VideoType;
  orientation: Orientation;
  durationPreset: DurationPreset;
  publishTarget: PublishTarget;
  voiceProvider: VoiceProvider;
  mediaStyle: MediaStyle;
  visualSource: VisualSource;
  sourceUrl: string | null;
  sourceTitle: string | null;
  seasonNumber: number | null;
  episodeNumber: number | null;
  musicAssetId: string | null;
  visualAssetIds: string[];
  assetIds: string[];
  targetMetadata: Record<string, string>;
}

interface JobStep {
  name: string;
  status: string;
  message: string;
  updatedAt: string;
}

interface JobArtifact {
  id: string;
  type: string;
  filename: string;
  url: string;
  sizeBytes: number;
  createdAt: string;
}

interface JobResponse extends CreateJobRequest {
  id: string;
  status: JobStatus;
  width: number;
  height: number;
  targetSeconds: number;
  title: string | null;
  description: string | null;
  tags: string[];
  citations: string[];
  madeForKids: boolean;
  containsSyntheticMedia: boolean;
  error: string | null;
  createdAt: string;
  updatedAt: string;
  steps: JobStep[];
  artifacts: JobArtifact[];
}

interface AssetResponse {
  id: string;
  filename: string;
  contentType: string;
}

interface SettingsResponse {
  youtubeUploadWarning: string;
}

@Component({
  selector: 'app-root',
  imports: [
    CommonModule,
    HttpClientModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatCheckboxModule,
    MatDividerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatListModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSnackBarModule,
    MatToolbarModule,
    MatTooltipModule
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  private static readonly ownerKeyStorageKey = 'jenerator.ownerKey';

  private readonly http = inject(HttpClient);
  private readonly formBuilder = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly ownerKey = signal(localStorage.getItem(AppComponent.ownerKeyStorageKey) ?? '');
  readonly jobs = signal<JobResponse[]>([]);
  readonly loading = signal(false);
  readonly creating = signal(false);
  readonly uploadedMusicName = signal('');
  readonly uploadedVisualAssets = signal<AssetResponse[]>([]);
  readonly uploadWarning = signal('By clicking upload, you certify that the content complies with YouTube Terms of Service and does not violate copyright or privacy rights.');

  readonly jobForm = this.formBuilder.nonNullable.group({
    prompt: ['', [Validators.required, Validators.maxLength(4000)]],
    videoType: ['RECAP' as VideoType, Validators.required],
    orientation: ['PORTRAIT_9_16' as Orientation, Validators.required],
    durationPreset: ['ONE_MINUTE' as DurationPreset, Validators.required],
    publishTarget: ['YOUTUBE_SHORT' as PublishTarget, Validators.required],
    voiceProvider: ['OPENAI_TTS' as VoiceProvider, Validators.required],
    mediaStyle: ['CINEMATIC_RECAP' as MediaStyle, Validators.required],
    visualSource: ['SOURCE_SCREENSHOTS' as VisualSource, Validators.required],
    sourceUrl: [''],
    sourceTitle: [''],
    seasonNumber: [''],
    episodeNumber: [''],
    musicAssetId: ['']
  });

  readonly configurationWarning = signal<string | null>(null);

  constructor() {
    this.jobForm.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.configurationWarning.set(this.formatWarning()));

    interval(10_000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadJobs());

    this.loadSettings();
    this.loadJobs();
    this.configurationWarning.set(this.formatWarning());
  }

  createJob(): void {
    if (!this.ensureOwnerKey() || this.jobForm.invalid || this.configurationWarning()) {
      return;
    }
    this.creating.set(true);
    const raw = this.jobForm.getRawValue();
    const visualAssetIds = this.uploadedVisualAssets().map((asset) => asset.id);
    const musicAssetIds = raw.musicAssetId ? [raw.musicAssetId] : [];
    const request: CreateJobRequest = {
      ...raw,
      sourceUrl: raw.sourceUrl || null,
      sourceTitle: raw.sourceTitle || null,
      seasonNumber: this.optionalNumber(raw.seasonNumber),
      episodeNumber: this.optionalNumber(raw.episodeNumber),
      musicAssetId: raw.musicAssetId || null,
      visualAssetIds,
      assetIds: [...musicAssetIds, ...visualAssetIds],
      targetMetadata: {}
    };

    this.http.post<JobResponse>('/api/jobs', request, this.authOptions()).subscribe({
      next: (job) => {
        this.jobs.update((jobs) => [job, ...jobs]);
        this.jobForm.controls.prompt.reset('');
        this.creating.set(false);
        this.snackBar.open('Job created', 'Close', { duration: 2200 });
      },
      error: (error) => {
        this.creating.set(false);
        this.snackBar.open(this.errorMessage(error), 'Close', { duration: 5000 });
      }
    });
  }

  loadJobs(): void {
    if (!this.ownerKey().trim()) {
      this.jobs.set([]);
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.http.get<JobResponse[]>('/api/jobs', this.authOptions()).subscribe({
      next: (jobs) => {
        this.jobs.set(jobs);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  loadSettings(): void {
    this.http.get<SettingsResponse>('/api/settings').subscribe({
      next: (settings) => this.uploadWarning.set(settings.youtubeUploadWarning),
      error: () => undefined
    });
  }

  uploadMusic(event: Event): void {
    if (!this.ensureOwnerKey()) {
      return;
    }
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    const data = new FormData();
    data.append('file', file);
    this.http.post<AssetResponse>('/api/assets', data, this.authOptions()).subscribe({
      next: (asset) => {
        this.jobForm.controls.musicAssetId.setValue(asset.id);
        this.uploadedMusicName.set(asset.filename);
        this.snackBar.open('Music uploaded', 'Close', { duration: 2200 });
      },
      error: (error) => this.snackBar.open(this.errorMessage(error), 'Close', { duration: 5000 })
    });
  }

  uploadVisuals(event: Event): void {
    if (!this.ensureOwnerKey()) {
      return;
    }
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    if (!files.length) {
      return;
    }
    for (const file of files) {
      const data = new FormData();
      data.append('file', file);
      this.http.post<AssetResponse>('/api/assets', data, this.authOptions()).subscribe({
        next: (asset) => {
          this.uploadedVisualAssets.update((assets) => [...assets, asset]);
          this.configurationWarning.set(this.formatWarning());
          this.snackBar.open('Visual asset uploaded', 'Close', { duration: 1600 });
        },
        error: (error) => this.snackBar.open(this.errorMessage(error), 'Close', { duration: 5000 })
      });
    }
    input.value = '';
  }

  removeVisualAsset(assetId: string): void {
    this.uploadedVisualAssets.update((assets) => assets.filter((asset) => asset.id !== assetId));
    this.configurationWarning.set(this.formatWarning());
  }

  approveUpload(jobId: string): void {
    if (!this.ensureOwnerKey()) {
      return;
    }
    this.http.post<JobResponse>(`/api/jobs/${jobId}/approve-upload`, {}, this.authOptions()).subscribe({
      next: (job) => {
        this.jobs.update((jobs) => jobs.map((current) => current.id === job.id ? job : current));
        this.snackBar.open('Upload approved', 'Close', { duration: 2200 });
      },
      error: (error) => this.snackBar.open(this.errorMessage(error), 'Close', { duration: 5000 })
    });
  }

  saveReview(
    job: JobResponse,
    title: string,
    description: string,
    tags: string,
    citations: string,
    madeForKids: boolean,
    containsSyntheticMedia: boolean
  ): void {
    if (!this.ensureOwnerKey()) {
      return;
    }
    const request = {
      title: title.trim(),
      description: description.trim(),
      tags: this.splitList(tags),
      citations: this.splitLines(citations),
      madeForKids,
      containsSyntheticMedia
    };
    this.http.patch<JobResponse>(`/api/jobs/${job.id}/review`, request, this.authOptions()).subscribe({
      next: (updated) => {
        this.jobs.update((jobs) => jobs.map((current) => current.id === updated.id ? updated : current));
        this.snackBar.open('Review saved', 'Close', { duration: 2200 });
      },
      error: (error) => this.snackBar.open(this.errorMessage(error), 'Close', { duration: 5000 })
    });
  }

  applyFormatSuggestion(): void {
    const target = this.jobForm.controls.publishTarget.value;
    this.jobForm.controls.orientation.setValue(target === 'YOUTUBE_SHORT' ? 'PORTRAIT_9_16' : 'LANDSCAPE_16_9');
  }

  formatSuggestionAvailable(): boolean {
    const warning = this.configurationWarning();
    return !!warning && (warning.includes('9:16') || warning.includes('16:9'));
  }

  saveOwnerKey(value: string): void {
    const normalized = value.trim();
    this.ownerKey.set(normalized);
    if (normalized) {
      localStorage.setItem(AppComponent.ownerKeyStorageKey, normalized);
      this.snackBar.open('Owner key saved', 'Close', { duration: 2200 });
      this.loadJobs();
      return;
    }
    localStorage.removeItem(AppComponent.ownerKeyStorageKey);
    this.jobs.set([]);
    this.snackBar.open('Owner key cleared', 'Close', { duration: 2200 });
  }

  stepIcon(status: string): string {
    if (status === 'DONE') {
      return 'check_circle';
    }
    if (status === 'ERROR') {
      return 'error';
    }
    return 'pending';
  }

  promptLength(): number {
    return this.jobForm.controls.prompt.value.length;
  }

  previewArtifact(job: JobResponse): JobArtifact | null {
    return job.artifacts.find((artifact) => artifact.type === 'PREVIEW_VIDEO') ?? null;
  }

  listText(values: string[] | null | undefined): string {
    return values?.join(', ') ?? '';
  }

  lineText(values: string[] | null | undefined): string {
    return values?.join('\n') ?? '';
  }

  private formatWarning(): string | null {
    const orientation = this.jobForm.controls.orientation.value;
    const target = this.jobForm.controls.publishTarget.value;
    if (target === 'YOUTUBE_SHORT' && orientation !== 'PORTRAIT_9_16') {
      return 'YouTube Shorts need 9:16 portrait. Landscape exports are normal videos.';
    }
    if (target === 'YOUTUBE_VIDEO' && orientation !== 'LANDSCAPE_16_9') {
      return 'Normal YouTube videos should use 16:9 landscape. Portrait exports under three minutes may become Shorts.';
    }
    if (this.jobForm.controls.visualSource.value === 'SOURCE_SCREENSHOTS'
      && !this.jobForm.controls.sourceUrl.value.trim()
      && this.uploadedVisualAssets().length === 0) {
      return 'Source screenshots need a streaming/page URL or uploaded screenshots.';
    }
    if (this.jobForm.controls.visualSource.value === 'UPLOADED_ASSETS'
      && this.uploadedVisualAssets().length === 0) {
      return 'Uploaded visuals mode needs at least one uploaded image or video.';
    }
    return null;
  }

  private ensureOwnerKey(): boolean {
    if (this.ownerKey().trim()) {
      return true;
    }
    this.snackBar.open('Enter the owner key before using remote job controls.', 'Close', { duration: 5000 });
    return false;
  }

  private authOptions(): { headers: Record<string, string> } {
    return { headers: { 'X-Jenerator-Owner-Key': this.ownerKey().trim() } };
  }

  private splitList(value: string): string[] {
    return value.split(',')
      .map((item) => item.trim())
      .filter(Boolean);
  }

  private splitLines(value: string): string[] {
    return value.split(/\r?\n/)
      .map((item) => item.trim())
      .filter(Boolean);
  }

  private optionalNumber(value: string): number | null {
    const trimmed = value.trim();
    if (!trimmed) {
      return null;
    }
    const parsed = Number.parseInt(trimmed, 10);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private errorMessage(error: unknown): string {
    const maybeHttpError = error as { error?: { messages?: string[]; message?: string } };
    if (maybeHttpError.error?.messages?.length) {
      return maybeHttpError.error.messages.join(' ');
    }
    return maybeHttpError.error?.message ?? 'Request failed';
  }
}
