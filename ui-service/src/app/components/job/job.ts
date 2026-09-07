import {
  Component,
  ViewChild,
  OnInit,
  OnDestroy,
  TemplateRef,
  ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSliderModule } from '@angular/material/slider';
import { FormsModule } from '@angular/forms';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatRadioModule } from '@angular/material/radio';
import { MatMenuModule } from '@angular/material/menu';

import { JobService } from '../../services/job.service';
import { PresetService } from '../../services/preset.service';
import { EncodeSet, EncodeSetService } from '../../services/encode-set.service';
import { Preset } from '../preset/preset';
import { Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';

export interface Job {
  id: string;
  inputFileName: string;
  outputFileName?: string;
  status: string;
  progress: number;
  preset?: { name: string; width?: number; height?: number };
  videoId?: string;
  createdAt?: string;
  batchId?: string | null;
  encodeSetId?: string | null;
  subtitleMode?: string;
  subtitleVttFileName?: string | null;
  subtitleLanguage?: string | null;
  subtitleLabel?: string | null;
}

@Component({
  selector: 'app-job',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatSortModule,
    MatIconModule,
    MatProgressBarModule,
    MatDialogModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatSliderModule,
    FormsModule,
    MatTooltipModule,
    MatCardModule,
    MatPaginatorModule,
    MatChipsModule,
    MatButtonToggleModule,
    MatRadioModule,
    MatMenuModule,
  ],
  templateUrl: './job.html',
  styleUrl: './job.css',
})
export class JobComponent implements OnInit, OnDestroy {
  displayedColumns: string[] = [
    'id',
    'inputFileName',
    'batch',
    'presetName',
    'status',
    'progress',
    'actions',
  ];
  dataSource = new MatTableDataSource<Job>([]);
  presets: Preset[] = [];

  selectedFile: File | null = null;
  selectedPresetId: string = '';
  // 'single' = tek şablon, 'set' = paket (setteki her preset için ayrı iş)
  jobMode: 'single' | 'set' = 'single';
  selectedEncodeSetId: string = '';
  encodeSets: EncodeSet[] = [];
  selectedSubtitle: File | null = null;
  selectedDubbing: File | null = null;
  // 'SIDECAR' = ayrı .vtt dosyası (oynatıcıdan kapatılabilir), 'BURN' = görüntüye yak
  subtitleMode: 'SIDECAR' | 'BURN' = 'SIDECAR';
  subtitleLanguage = 'tr';

  readonly subtitleLanguages = [
    { code: 'tr', label: 'Türkçe' },
    { code: 'en', label: 'İngilizce' },
    { code: 'de', label: 'Almanca' },
    { code: 'ar', label: 'Arapça' },
  ];

  // Oynatıcıya verilecek altyazı izi (SIDECAR modunda dolu olur)
  currentSubtitleUrl = '';
  currentSubtitleLabel = '';
  currentSubtitleLang = '';

  // --- SMIL oynatıcı durumu ---
  smilBatchId = '';
  smilBatchName = '';
  smilError = '';
  smilQualities: { src: string; label: string; width: number; height: number; bitrate: number }[] = [];
  smilCurrentQuality = 0;
  smilSubtitle: { src: string; lang: string; label: string } | null = null;
  smilSubtitleOn = false;

  private pollingSubscription?: Subscription;

  // Yükleme arka planda; kullanıcı sitede gezmeye devam edebilir
  isBackgroundUploading = false;
  backgroundUploadName = '';

  currentVideoUrl: string = '';
  currentVideoName: string = '';

  topSearch = { id: '', inputFileName: '', presetName: '', status: '' };

  // Klip paneli
  showClipPanel = false;
  clipVideoUrl = '';
  clipVideoName = '';
  videoDuration = 60;

  videoClipRequest = { videoId: '', startSeconds: 0, endSeconds: 10 };
  isVideoClipping = false;

  clipStartStr = '00:00:00';
  clipEndStr = '00:00:10';

  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild('uploadDialog') uploadDialog!: TemplateRef<any>;
  @ViewChild('videoDialog') videoDialog!: TemplateRef<any>;
  @ViewChild('smilDialog') smilDialog!: TemplateRef<any>;

  constructor(
    private jobService: JobService,
    private presetService: PresetService,
    private encodeSetService: EncodeSetService,
    public dialog: MatDialog,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    this.loadPresets();
    this.loadEncodeSets();
    this.setupFilterPredicate();

    this.pollingSubscription = timer(0, 3000)
      .pipe(switchMap(() => this.jobService.getAllJobs()))
      .subscribe({
        next: (data) => this.updateTableData(data),
        error: (err) => console.error('İşler çekilirken hata oluştu', err),
      });
  }

  ngOnDestroy() {
    if (this.pollingSubscription) this.pollingSubscription.unsubscribe();
  }

  setupFilterPredicate() {
    this.dataSource.filterPredicate = (data: Job, filter: string) => {
      const search = JSON.parse(filter);
      const presetName = data.preset?.name || '';

      const matchId = data.id.toLowerCase().includes((search.id || '').toLowerCase());
      const matchName = data.inputFileName
        .toLowerCase()
        .includes((search.inputFileName || '').toLowerCase());
      const matchPreset = presetName
        .toLowerCase()
        .includes((search.presetName || '').toLowerCase());
      const matchStatus = search.status === '' || data.status === search.status;

      return matchId && matchName && matchPreset && matchStatus;
    };
  }

  applyTopFilters() {
    this.dataSource.filter = JSON.stringify(this.topSearch);
  }

  clearTopFilters() {
    this.topSearch = { id: '', inputFileName: '', presetName: '', status: '' };
    this.dataSource.filter = JSON.stringify(this.topSearch);
  }

  private updateTableData(data: Job[]) {
    // Aynı batch'ten doğan işler tabloda alt alta dursun diye önce batch'e,
    // sonra çözünürlüğe (büyükten küçüğe) göre sıralanıyor.
    const stableData = [...data].sort((a, b) => {
      const groupA = a.batchId || a.id;
      const groupB = b.batchId || b.id;
      if (groupA !== groupB) return groupA.localeCompare(groupB);

      const areaA = (a.preset?.width ?? 0) * (a.preset?.height ?? 0);
      const areaB = (b.preset?.width ?? 0) * (b.preset?.height ?? 0);
      if (areaA !== areaB) return areaB - areaA;

      return a.id.localeCompare(b.id);
    });
    this.dataSource.data = stableData;

    if (!this.dataSource.sort && this.sort) this.dataSource.sort = this.sort;
    if (!this.dataSource.paginator && this.paginator) {
      this.dataSource.paginator = this.paginator;
      this.paginator.pageSizeOptions = [10, 20, 30];
      this.paginator.pageSize = 10;
    }
    this.cdr.detectChanges();
  }

  loadJobs() {
    this.jobService.getAllJobs().subscribe({
      next: (data) => this.updateTableData(data),
      error: (err) => console.error('İşler çekilirken hata oluştu', err),
    });
  }

  loadPresets() {
    this.presetService.getAllPresets().subscribe({
      next: (data) => (this.presets = data),
      error: (err) => console.error('Şablonlar çekilirken hata oluştu', err),
    });
  }

  loadEncodeSets() {
    this.encodeSetService.getAllSets().subscribe({
      next: (data) => (this.encodeSets = data),
      error: (err) => console.error('Paketler çekilirken hata oluştu', err),
    });
  }

  // Tabloda paket adını göstermek için: job yalnızca encodeSetId taşır
  encodeSetName(job: Job): string {
    const set = this.encodeSets.find((s) => s.id === job.encodeSetId);
    return set ? set.name : 'Paket';
  }

  // Aynı batch'teki iş sayısı - tabloda "3 çıktıdan biri" bilgisini vermek için
  batchSize(job: Job): number {
    if (!job.batchId) return 1;
    return this.dataSource.data.filter((j) => j.batchId === job.batchId).length;
  }

  openUploadDialog() {
    this.selectedFile = null;
    this.selectedPresetId = '';
    this.selectedEncodeSetId = '';
    this.jobMode = 'single';
    this.selectedSubtitle = null;
    this.selectedDubbing = null;
    this.subtitleMode = 'SIDECAR';
    this.subtitleLanguage = 'tr';
    this.loadEncodeSets();
    this.dialog.open(this.uploadDialog, { width: '450px' });
  }

  // Mod değişince diğer modun seçimi temizlenir; backend ikisini birden kabul etmiyor
  onJobModeChange() {
    if (this.jobMode === 'single') {
      this.selectedEncodeSetId = '';
    } else {
      this.selectedPresetId = '';
    }
  }

  get isTargetSelected(): boolean {
    return this.jobMode === 'single' ? !!this.selectedPresetId : !!this.selectedEncodeSetId;
  }

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
  }
  onSubtitleSelected(event: any) {
    this.selectedSubtitle = event.target.files[0];
  }
  onDubbingSelected(event: any) {
    this.selectedDubbing = event.target.files[0];
  }

  // YENİ: Pencereyi hemen kapatır, yükleme arka planda devam eder
  startUploadAndJob() {
    if (!this.selectedFile || !this.isTargetSelected) {
      alert(
        this.jobMode === 'single'
          ? 'Lütfen bir video ve uygulanacak şablonu seçin.'
          : 'Lütfen bir video ve uygulanacak paketi seçin.',
      );
      return;
    }

    const file = this.selectedFile;
    const mode = this.jobMode;
    const presetId = this.selectedPresetId;
    const encodeSetId = this.selectedEncodeSetId;
    const subtitle = this.selectedSubtitle;
    const dubbing = this.selectedDubbing;
    const subMode = this.subtitleMode;
    const subLang = this.subtitleLanguage;
    const subLabel = this.subtitleLanguages.find((l) => l.code === subLang)?.label ?? 'Türkçe';

    // Pencereyi anında kapat, kullanıcı sitede gezmeye devam etsin
    this.dialog.closeAll();
    this.isBackgroundUploading = true;
    this.backgroundUploadName = file.name;

    this.jobService.uploadVideo(file).subscribe({
      next: (videoResponse) => {
        const formData = new FormData();
        formData.append('videoId', videoResponse.id);
        // Backend ya presetId ya encodeSetId bekler, ikisi birden gönderilmez
        if (mode === 'single') {
          formData.append('presetId', presetId);
        } else {
          formData.append('encodeSetId', encodeSetId);
        }
        if (subtitle) {
          formData.append('subtitleFile', subtitle);
          // BURN: görüntüye yakılır, kapatılamaz. SIDECAR: ayrı .vtt, oynatıcıdan seçilebilir.
          formData.append('subtitleMode', subMode);
          formData.append('subtitleLanguage', subLang);
          formData.append('subtitleLabel', subLabel);
        }
        if (dubbing) formData.append('dubbingFile', dubbing);

        this.jobService.createJob(formData).subscribe({
          next: () => {
            this.isBackgroundUploading = false;
            this.backgroundUploadName = '';
            this.loadJobs();
            this.cdr.detectChanges();
          },
          error: (err) => {
            console.error('Job oluşturma hatası:', err);
            this.isBackgroundUploading = false;
            this.backgroundUploadName = '';
            this.cdr.detectChanges();
            alert('İşlem oluşturulamadı.');
          },
        });
      },
      error: (err) => {
        console.error('Video yükleme hatası:', err);
        this.isBackgroundUploading = false;
        this.backgroundUploadName = '';
        this.cdr.detectChanges();
        alert('Video yüklenemedi. İşlem başarısız oldu.');
      },
    });
  }

  /**
   * Çıktı dosyasının servis URL'i.
   * Paket işlerinde outputFileName "<batchId>/720p_....mp4" gibi alt klasör içerir;
   * bu yüzden her segment ayrı ayrı encode ediliyor, "/" olduğu gibi kalıyor.
   */
  private outputUrl(job: Job): string {
    const targetFileName = job.outputFileName ? job.outputFileName : job.inputFileName;
    const encoded = targetFileName.split('/').map(encodeURIComponent).join('/');
    return `http://localhost:8081/api/videos/play/${encoded}`;
  }

  private outputBaseName(job: Job): string {
    const targetFileName = job.outputFileName ? job.outputFileName : job.inputFileName;
    const parts = targetFileName.split('/');
    return parts[parts.length - 1];
  }

  // SIDECAR altyazının servis URL'i (yoksa boş string)
  private subtitleUrl(job: Job): string {
    if (!job.subtitleVttFileName) return '';
    const encoded = job.subtitleVttFileName.split('/').map(encodeURIComponent).join('/');
    return `http://localhost:8081/api/videos/play/${encoded}`;
  }

  hasSelectableSubtitle(job: Job): boolean {
    return job.subtitleMode === 'SIDECAR' && !!job.subtitleVttFileName;
  }

  playVideo(job: Job) {
    this.currentVideoName = this.outputBaseName(job);
    this.currentVideoUrl = this.outputUrl(job);
    this.currentSubtitleUrl = this.subtitleUrl(job);
    this.currentSubtitleLabel = job.subtitleLabel || 'Altyazı';
    this.currentSubtitleLang = job.subtitleLanguage || 'tr';
    this.dialog.open(this.videoDialog, {
      width: '800px',
      maxWidth: '90vw',
      panelClass: 'video-dialog-container',
    });
  }

  downloadVideo(job: Job) {
    const link = document.createElement('a');
    link.href = this.outputUrl(job);
    link.download = this.outputBaseName(job);
    link.target = '_blank';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  // ============ SMIL OYNATICI (paket çıktıları için) ============

  /**
   * Paketin playlist.smil dosyasını okuyup oynatıcıyı kurar.
   *
   * SMIL bir video değil, bir tarif dosyası: hangi mp4'ün hangi kalite olduğunu
   * ve altyazının nerede olduğunu söyler. Tarayıcı bunu kendi başına oynatamadığı
   * için XML'i burada ayrıştırıp <video> kaynağını biz yönetiyoruz.
   * Aynı dosya, ileride Wowza kurulursa orada da doğrudan çalışır.
   */
  openSmilPlayer(job: Job) {
    if (!job.batchId) return;

    this.smilError = '';
    this.smilQualities = [];
    this.smilSubtitle = null;
    this.smilCurrentQuality = 0;
    this.smilSubtitleOn = false;
    this.smilBatchId = job.batchId;
    this.smilBatchName = job.inputFileName;

    this.dialog.open(this.smilDialog, {
      width: '900px',
      maxWidth: '95vw',
      panelClass: 'video-dialog-container',
    });

    this.jobService.getBatchSmil(job.batchId).subscribe({
      next: (xml) => {
        this.parseSmil(xml);
        if (this.smilQualities.length === 0) {
          this.smilError = 'SMIL dosyasında oynatılabilir kalite bulunamadı.';
        }
        this.cdr.detectChanges();
        setTimeout(() => this.applySubtitleMode(), 300);
      },
      error: () => {
        this.smilError = 'SMIL manifesti okunamadı. Paketin tüm işleri tamamlandı mı?';
        this.cdr.detectChanges();
      },
    });
  }

  /** <video> ve <textstream> düğümlerini okur. Kaliteler büyükten küçüğe sıralanır. */
  private parseSmil(xml: string) {
    const doc = new DOMParser().parseFromString(xml, 'application/xml');
    if (doc.querySelector('parsererror')) {
      this.smilError = 'SMIL dosyası ayrıştırılamadı.';
      return;
    }

    this.smilQualities = [...doc.querySelectorAll('video')]
      .map((el) => {
        const width = Number(el.getAttribute('width')) || 0;
        const height = Number(el.getAttribute('height')) || 0;
        return {
          src: el.getAttribute('src') || '',
          width,
          height,
          bitrate: Number(el.getAttribute('system-bitrate')) || 0,
          label: height ? `${height}p` : el.getAttribute('src') || 'Bilinmeyen',
        };
      })
      .filter((q) => q.src)
      .sort((a, b) => b.width * b.height - a.width * a.height);

    const text = doc.querySelector('textstream');
    if (text && text.getAttribute('src')) {
      this.smilSubtitle = {
        src: text.getAttribute('src')!,
        lang: text.getAttribute('system-language') || 'tr',
        label: text.getAttribute('title') || 'Altyazı',
      };
    }
  }

  // SMIL'deki yollar dosyaya göre göreli; paket klasörüyle birleştiriyoruz
  private smilFileUrl(relative: string): string {
    const encoded = `${this.smilBatchId}/${relative}`.split('/').map(encodeURIComponent).join('/');
    return `http://localhost:8081/api/videos/play/${encoded}`;
  }

  get smilVideoUrl(): string {
    const q = this.smilQualities[this.smilCurrentQuality];
    return q ? this.smilFileUrl(q.src) : '';
  }

  get smilSubtitleUrl(): string {
    return this.smilSubtitle ? this.smilFileUrl(this.smilSubtitle.src) : '';
  }

  get smilQualityLabel(): string {
    return this.smilQualities[this.smilCurrentQuality]?.label ?? 'Kalite';
  }

  /**
   * Kalite değiştirme: mp4 kaynağı değişince tarayıcı videoyu baştan yükler,
   * bu yüzden bulunduğumuz saniyeyi ve oynatma durumunu elle geri veriyoruz.
   */
  selectSmilQuality(index: number) {
    const video = document.getElementById('smil-video') as HTMLVideoElement | null;
    const position = video?.currentTime ?? 0;
    const wasPlaying = video ? !video.paused : false;

    this.smilCurrentQuality = index;
    this.cdr.detectChanges();

    if (!video) return;
    video.addEventListener(
      'loadedmetadata',
      () => {
        video.currentTime = position;
        this.applySubtitleMode();
        if (wasPlaying) video.play().catch(() => undefined);
      },
      { once: true },
    );
    video.load();
  }

  toggleSmilSubtitle(on: boolean) {
    this.smilSubtitleOn = on;
    this.applySubtitleMode();
  }

  // Kaynak her değiştiğinde altyazı izi sıfırlandığı için tekrar uygulanmalı
  private applySubtitleMode() {
    const video = document.getElementById('smil-video') as HTMLVideoElement | null;
    if (!video || video.textTracks.length === 0) return;
    video.textTracks[0].mode = this.smilSubtitleOn ? 'showing' : 'disabled';
  }

  openSmil(job: Job) {
    if (!job.batchId) return;
    window.open(this.jobService.getBatchSmilUrl(job.batchId), '_blank');
  }

  deleteJob(id: string) {
    if (confirm('Bu işlemi iptal etmek/silmek istediğinize emin misiniz?')) {
      this.jobService.deleteJob(id).subscribe({
        next: () => this.loadJobs(),
        error: (err) => console.error('Silme hatası', err),
      });
    }
  }

  openClipPanel(job: any) {
    const actualVideoId = job.video?.id || job.videoId || job.id;

    this.clipVideoName = this.outputBaseName(job);
    this.clipVideoUrl = this.outputUrl(job);
    this.videoClipRequest = { videoId: actualVideoId, startSeconds: 0, endSeconds: 10 };
    this.clipStartStr = this.formatTime(0);
    this.clipEndStr = this.formatTime(10);
    this.videoDuration = 60;
    this.isVideoClipping = false;
    this.showClipPanel = true;

    // Panel açıldıktan sonra iki oynatıcıyı da hazırla
    setTimeout(() => {
      const startVideo = document.getElementById('job-clip-video-start') as HTMLVideoElement;
      const endVideo = document.getElementById('job-clip-video-end') as HTMLVideoElement;

      if (startVideo) {
        startVideo.addEventListener('loadedmetadata', () => {
          if (startVideo.duration && isFinite(startVideo.duration)) {
            this.videoDuration = Math.floor(startVideo.duration);
            if (this.videoClipRequest.endSeconds > this.videoDuration) {
              this.videoClipRequest.endSeconds = this.videoDuration;
              this.clipEndStr = this.formatTime(this.videoDuration);
            }
            this.cdr.detectChanges();
          }
          startVideo.currentTime = this.videoClipRequest.startSeconds;
        });

        // Sol oynatıcı seçilen aralığın dışına çıkmaz, kendi içinde döner
        startVideo.addEventListener('timeupdate', () => {
          if (startVideo.currentTime >= this.videoClipRequest.endSeconds) {
            startVideo.currentTime = this.videoClipRequest.startSeconds;
          }
        });
      }

      if (endVideo) {
        endVideo.addEventListener('loadedmetadata', () => {
          endVideo.currentTime = this.videoClipRequest.endSeconds;
        });
      }
    }, 200);
  }

  closeClipPanel() {
    this.showClipPanel = false;
  }

  formatTime = (seconds: number): string => {
    const safe = Math.max(0, Math.floor(seconds || 0));
    const h = Math.floor(safe / 3600).toString().padStart(2, '0');
    const m = Math.floor((safe % 3600) / 60).toString().padStart(2, '0');
    const s = (safe % 60).toString().padStart(2, '0');
    return `${h}:${m}:${s}`;
  };

  parseTimeStr(timeStr: string): number {
    const parts = (timeStr || '').split(':');
    let total = 0;
    if (parts.length === 3) {
      total =
        (parseInt(parts[0], 10) || 0) * 3600 +
        (parseInt(parts[1], 10) || 0) * 60 +
        (parseInt(parts[2], 10) || 0);
    } else if (parts.length === 2) {
      total = (parseInt(parts[0], 10) || 0) * 60 + (parseInt(parts[1], 10) || 0);
    } else {
      total = parseInt(parts[0], 10) || 0;
    }
    if (total < 0) total = 0;
    if (total > this.videoDuration) total = this.videoDuration;
    return total;
  }

  // Sol çubuk oynatılınca sol video, sağ çubuk oynatılınca sağ video hareket eder
  onClipSliderChange() {
    this.clipStartStr = this.formatTime(this.videoClipRequest.startSeconds);
    this.clipEndStr = this.formatTime(this.videoClipRequest.endSeconds);
    this.seekStartVideo(this.videoClipRequest.startSeconds);
    this.seekEndVideo(this.videoClipRequest.endSeconds);
  }

  onClipStartStrChange(val: string) {
    this.clipStartStr = val;
    this.videoClipRequest.startSeconds = this.parseTimeStr(val);
    this.seekStartVideo(this.videoClipRequest.startSeconds);
  }

  onClipEndStrChange(val: string) {
    this.clipEndStr = val;
    this.videoClipRequest.endSeconds = this.parseTimeStr(val);
    this.seekEndVideo(this.videoClipRequest.endSeconds);
  }

  seekStartVideo(seconds: number) {
    const video = document.getElementById('job-clip-video-start') as HTMLVideoElement;
    this.seekVideoElement(video, seconds);
  }

  seekEndVideo(seconds: number) {
    const video = document.getElementById('job-clip-video-end') as HTMLVideoElement;
    this.seekVideoElement(video, seconds);
  }

  private seekVideoElement(video: HTMLVideoElement | null, seconds: number) {
    if (!video || isNaN(seconds)) return;
    if (video.duration && seconds > video.duration) {
      video.currentTime = Math.max(0, video.duration - 0.1);
    } else {
      video.currentTime = seconds;
    }
  }

  takeVideoClip() {
    if (this.videoClipRequest.startSeconds >= this.videoClipRequest.endSeconds) {
      alert('Bitiş süresi başlangıç süresinden büyük olmalıdır!');
      return;
    }
    this.isVideoClipping = true;
    const payload = {
      videoId: this.videoClipRequest.videoId,
      startTime: this.formatTime(this.videoClipRequest.startSeconds),
      endTime: this.formatTime(this.videoClipRequest.endSeconds),
    };

    this.jobService.createVideoClip(payload).subscribe({
      next: (res) => {
        this.isVideoClipping = false;
        this.closeClipPanel();
        alert(res);
      },
      error: (err) => {
        console.error('Klip Hatası:', err);
        this.isVideoClipping = false;
        alert('Klip oluşturulurken hata oluştu!');
      },
    });
  }

  trackById(index: number, item: Job): string {
    return item.id;
  }
}
