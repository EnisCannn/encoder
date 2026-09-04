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

import { JobService } from '../../services/job.service';
import { PresetService } from '../../services/preset.service';
import { Preset } from '../preset/preset';
import { Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';

export interface Job {
  id: string;
  inputFileName: string;
  outputFileName?: string;
  status: string;
  progress: number;
  preset?: { name: string };
  videoId?: string;
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
  ],
  templateUrl: './job.html',
  styleUrl: './job.css',
})
export class JobComponent implements OnInit, OnDestroy {
  displayedColumns: string[] = [
    'id',
    'inputFileName',
    'presetName',
    'status',
    'progress',
    'actions',
  ];
  dataSource = new MatTableDataSource<Job>([]);
  presets: Preset[] = [];

  selectedFile: File | null = null;
  selectedPresetId: string = '';
  selectedSubtitle: File | null = null;
  selectedDubbing: File | null = null;
  private pollingSubscription?: Subscription;

  // YENİ: Yükleme artık arka planda; kullanıcı sitede gezmeye devam edebilir
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

  constructor(
    private jobService: JobService,
    private presetService: PresetService,
    public dialog: MatDialog,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    this.loadPresets();
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
    const stableData = [...data].sort((a, b) => a.id.localeCompare(b.id));
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

  openUploadDialog() {
    this.selectedFile = null;
    this.selectedPresetId = '';
    this.selectedSubtitle = null;
    this.selectedDubbing = null;
    this.dialog.open(this.uploadDialog, { width: '450px' });
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
    if (!this.selectedFile || !this.selectedPresetId) {
      alert('Lütfen bir video ve uygulanacak şablonu seçin.');
      return;
    }

    const file = this.selectedFile;
    const presetId = this.selectedPresetId;
    const subtitle = this.selectedSubtitle;
    const dubbing = this.selectedDubbing;

    // Pencereyi anında kapat, kullanıcı sitede gezmeye devam etsin
    this.dialog.closeAll();
    this.isBackgroundUploading = true;
    this.backgroundUploadName = file.name;

    this.jobService.uploadVideo(file).subscribe({
      next: (videoResponse) => {
        const formData = new FormData();
        formData.append('videoId', videoResponse.id);
        formData.append('presetId', presetId);
        if (subtitle) formData.append('subtitleFile', subtitle);
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

  playVideo(job: Job) {
    const targetFileName = job.outputFileName ? job.outputFileName : job.inputFileName;
    this.currentVideoName = targetFileName;
    this.currentVideoUrl = `http://localhost:8081/api/videos/play/${targetFileName}`;
    this.dialog.open(this.videoDialog, {
      width: '800px',
      maxWidth: '90vw',
      panelClass: 'video-dialog-container',
    });
  }

  downloadVideo(job: Job) {
    const targetFileName = job.outputFileName ? job.outputFileName : job.inputFileName;
    const downloadUrl = `http://localhost:8081/api/videos/play/${targetFileName}`;
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.download = targetFileName;
    link.target = '_blank';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
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
    const targetFileName = job.outputFileName ? job.outputFileName : job.inputFileName;

    this.clipVideoName = targetFileName;
    this.clipVideoUrl = `http://localhost:8081/api/videos/play/${targetFileName}`;
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
