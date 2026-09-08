import {
  Component,
  OnInit,
  OnDestroy,
  ViewChild,
  TemplateRef,
  ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSliderModule } from '@angular/material/slider';
import { FormsModule } from '@angular/forms';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCardModule } from '@angular/material/card';
import { Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LiveStreamService, LiveStream } from '../../services/live-stream.service';
import { PresetService } from '../../services/preset.service';
import { Preset } from '../preset/preset';
import Hls from 'hls.js';

@Component({
  selector: 'app-live-stream',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSliderModule,
    FormsModule,
    MatTooltipModule,
    MatCardModule,
  ],
  templateUrl: './live-stream.html',
  styleUrls: ['./live-stream.css'],
})
export class LiveStreamComponent implements OnInit, OnDestroy {
  displayedColumns: string[] = ['streamName', 'status', 'actions'];
  dataSource = new MatTableDataSource<LiveStream>([]);
  presets: Preset[] = [];

  private pollingSubscription?: Subscription;
  isStartingStream = false;
  liveStreamRequest = { streamName: '', inputUrl: '' };

  currentWatchUrl = '';
  currentStreamName = '';

  // İzleme penceresi durumu
  watchElapsedStr = '00:00:00';
  isWatchLive = true;
  isWatchPlaying = false;
  isWatchMuted = true;
  isBehindLive = false;

  // Manuel seek çubuğu için
  watchSliderSeconds = 0;
  watchSeekRange = 0;
  isUserSeeking = false;
  private watchSeekableStart = 0;

  private watchTimer?: any;
  private watchHls?: Hls;

  // Klip paneli durumu
  showClipPanel = false;
  clipRequest = { streamId: '', presetId: '', startSeconds: 0, endSeconds: 15, streamStartTime: 0 };
  isClipping = false;
  maxStreamDuration = 60;
  // İki ayrı oynatıcı: biri başlangıç, biri bitiş anını gösterir
  private clipHlsStart?: Hls;
  private clipHlsEnd?: Hls;

  startTimeRealStr = '00:00:00';
  endTimeRealStr = '00:00:00';

  @ViewChild('liveStreamDialog') liveStreamDialog!: TemplateRef<any>;
  @ViewChild('watchDialog') watchDialog!: TemplateRef<any>;

  constructor(
    private liveService: LiveStreamService,
    private presetService: PresetService,
    public dialog: MatDialog,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    this.presetService.getAllPresets().subscribe((data) => (this.presets = data));

    this.pollingSubscription = timer(0, 2000)
      .pipe(switchMap(() => this.liveService.getAllStreams()))
      .subscribe({
        next: (data) => {
          this.dataSource.data = data;
          this.cdr.detectChanges();
        },
        error: (err) => console.error('Yayınlar çekilemedi', err),
      });
  }

  ngOnDestroy() {
    if (this.pollingSubscription) this.pollingSubscription.unsubscribe();
    this.cleanupWatch();
    this.cleanupClip();
  }

  openStartDialog() {
    this.liveStreamRequest = { streamName: '', inputUrl: '' };
    this.isStartingStream = false;
    this.dialog.open(this.liveStreamDialog, { width: '450px' });
  }

  startStream() {
    this.isStartingStream = true;
    this.liveService.startStream(this.liveStreamRequest).subscribe({
      next: () => {
        this.dialog.closeAll();
        this.isStartingStream = false;
      },
      error: (err) => {
        console.error(err);
        this.isStartingStream = false;
        alert('Yayın başlatılamadı!');
      },
    });
  }

  stopStream(id: string) {
    if (confirm('Bu canlı yayını durdurmak istediğinize emin misiniz?')) {
      this.liveService.stopStream(id).subscribe({
        next: (res) => console.log(res),
        error: (err) => console.error('Durdurma hatası:', err),
      });
    }
  }

  deleteStream(id: string) {
    if (confirm('Bu yayın kaydını silmek istediğinize emin misiniz?')) {
      this.liveService.deleteStream(id).subscribe({
        next: (res) => console.log(res),
        error: (err) => {
          console.error('Silme hatası:', err);
          alert('Yayın silinemedi!');
        },
      });
    }
  }

  // ---------------- CANLI İZLEME ----------------

  watchStream(stream: LiveStream) {
    this.currentStreamName = stream.streamName;
    this.currentWatchUrl = `http://localhost:8081/api/live/play/${stream.id}/index.m3u8`;

    const startedAt = stream.streamStartTime || Date.now();
    const endedAt = stream.streamEndTime;

    this.isWatchLive = !endedAt;
    this.isWatchPlaying = false;
    this.isWatchMuted = true;
    this.isBehindLive = false;
    this.watchElapsedStr = '00:00:00';
    this.watchSliderSeconds = 0;
    this.watchSeekRange = 0;
    this.isUserSeeking = false;

    const dialogRef = this.dialog.open(this.watchDialog, {
      width: '860px',
      maxWidth: '92vw',
      panelClass: 'video-dialog-container',
    });

    dialogRef.afterClosed().subscribe(() => this.cleanupWatch());

    dialogRef.afterOpened().subscribe(() => {
      const video = document.getElementById('live-video') as HTMLVideoElement;
      if (!video) return;

      video.muted = true;

      video.addEventListener('play', () => {
        this.isWatchPlaying = true;
        this.cdr.detectChanges();
      });
      video.addEventListener('pause', () => {
        this.isWatchPlaying = false;
        this.cdr.detectChanges();
      });
      video.addEventListener('ended', () => {
        this.jumpToLiveEdge(video);
        video.play().catch(() => {});
      });

      this.attachLivePlayer(video);

      const tick = () => {
        const now = endedAt ? endedAt : Date.now();
        const secs = Math.max(0, Math.floor((now - startedAt) / 1000));
        this.watchElapsedStr = this.formatDuration(secs);

        if (video.seekable.length > 0) {
          this.watchSeekableStart = video.seekable.start(0);
        }
        const edge = this.getLiveEdge(video);
        if (edge != null) {
          this.watchSeekRange = Math.max(0, edge - this.watchSeekableStart);
        }

        if (!this.isUserSeeking) {
          this.watchSliderSeconds = Math.max(0, video.currentTime - this.watchSeekableStart);
        }

        this.isBehindLive = this.isWatchLive && edge != null && edge - video.currentTime > 8;

        this.cdr.detectChanges();
      };
      tick();
      this.watchTimer = setInterval(tick, 500);
    });
  }

  private attachLivePlayer(video: HTMLVideoElement) {
    if (Hls.isSupported()) {
      const hls = new Hls({ startPosition: -1, liveSyncDurationCount: 3 });
      this.watchHls = hls;
      hls.loadSource(this.currentWatchUrl);
      hls.attachMedia(video);

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        this.jumpToLiveEdge(video);
        video.play().catch(() => {});
      });

      hls.on(Hls.Events.LEVEL_UPDATED, () => {
        if (video.ended) {
          this.jumpToLiveEdge(video);
          video.play().catch(() => {});
        }
      });
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = this.currentWatchUrl;
      video.addEventListener('loadedmetadata', () => {
        this.jumpToLiveEdge(video);
        video.play().catch(() => {});
      });
    }
  }

  private getLiveEdge(video: HTMLVideoElement): number | null {
    const hls = this.watchHls;
    if (hls && hls.liveSyncPosition != null && isFinite(hls.liveSyncPosition)) {
      return hls.liveSyncPosition;
    }
    if (video.seekable.length > 0) {
      const end = video.seekable.end(video.seekable.length - 1);
      if (isFinite(end)) return Math.max(0, end - 0.5);
    }
    return null;
  }

  private jumpToLiveEdge(video: HTMLVideoElement) {
    const edge = this.getLiveEdge(video);
    if (edge != null) video.currentTime = edge;
  }

  onWatchSeekInput(event: Event) {
    const video = document.getElementById('live-video') as HTMLVideoElement;
    if (!video) return;
    const value = Number((event.target as HTMLInputElement).value);
    this.watchSliderSeconds = value;
    video.currentTime = this.watchSeekableStart + value;
  }

  onWatchSeekStart() {
    this.isUserSeeking = true;
  }

  onWatchSeekEnd() {
    this.isUserSeeking = false;
    const video = document.getElementById('live-video') as HTMLVideoElement;
    if (video) video.play().catch(() => {});
  }

  toggleWatchPlay() {
    const video = document.getElementById('live-video') as HTMLVideoElement;
    if (!video) return;
    if (video.paused) {
      video.play().catch(() => {});
    } else {
      video.pause();
    }
  }

  toggleWatchMute() {
    const video = document.getElementById('live-video') as HTMLVideoElement;
    if (!video) return;
    video.muted = !video.muted;
    this.isWatchMuted = video.muted;
  }

  goToLive() {
    const video = document.getElementById('live-video') as HTMLVideoElement;
    if (!video) return;
    this.jumpToLiveEdge(video);
    video.play().catch(() => {});
    this.isBehindLive = false;
  }

  toggleWatchFullscreen() {
    const wrapper = document.getElementById('live-video-wrapper');
    if (!wrapper) return;
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => {});
    } else {
      wrapper.requestFullscreen().catch(() => {});
    }
  }

  private cleanupWatch() {
    if (this.watchTimer) {
      clearInterval(this.watchTimer);
      this.watchTimer = undefined;
    }
    if (this.watchHls) {
      this.watchHls.destroy();
      this.watchHls = undefined;
    }
    this.isWatchPlaying = false;
  }

  private formatDuration(totalSeconds: number): string {
    const safe = Math.max(0, Math.floor(totalSeconds || 0));
    const h = Math.floor(safe / 3600).toString().padStart(2, '0');
    const m = Math.floor((safe % 3600) / 60).toString().padStart(2, '0');
    const s = (safe % 60).toString().padStart(2, '0');
    return `${h}:${m}:${s}`;
  }

  // ---------------- KLİP PANELİ (İKİ OYNATICILI) ----------------

  openClipPanel(stream: LiveStream) {
    const endMoment = stream.streamEndTime ? stream.streamEndTime : Date.now();
    const runningSeconds = stream.streamStartTime
      ? Math.floor((endMoment - stream.streamStartTime) / 1000)
      : 60;
    this.maxStreamDuration = runningSeconds > 0 ? runningSeconds : 60;

    let defaultStart = this.maxStreamDuration > 15 ? this.maxStreamDuration - 15 : 0;

    this.clipRequest = {
      streamId: stream.id,
      presetId: '',
      startSeconds: defaultStart,
      endSeconds: this.maxStreamDuration,
      streamStartTime: stream.streamStartTime || Date.now(),
    };

    this.startTimeRealStr = this.formatRealTimeLabel(defaultStart);
    this.endTimeRealStr = this.formatRealTimeLabel(this.maxStreamDuration);

    this.isClipping = false;
    this.showClipPanel = true;

    const streamUrl = `http://localhost:8081/api/live/play/${stream.id}/index.m3u8`;

    setTimeout(() => {
      const startVideo = document.getElementById('clip-video-start') as HTMLVideoElement;
      const endVideo = document.getElementById('clip-video-end') as HTMLVideoElement;

      // BAŞLANGIÇ oynatıcısı: seçilen aralıkta döngüye girer
      if (startVideo) {
        startVideo.addEventListener('timeupdate', () => {
          if (startVideo.currentTime >= this.clipRequest.endSeconds) {
            startVideo.currentTime = this.clipRequest.startSeconds;
          }
        });

        if (Hls.isSupported()) {
          const hls = new Hls();
          this.clipHlsStart = hls;
          hls.loadSource(streamUrl);
          hls.attachMedia(startVideo);
          hls.on(Hls.Events.MANIFEST_PARSED, () => {
            this.applyAvailableDuration(startVideo);
            startVideo.currentTime = this.clipRequest.startSeconds;
            startVideo.play().catch(() => {});
          });
          hls.on(Hls.Events.LEVEL_UPDATED, () => this.applyAvailableDuration(startVideo));
        } else if (startVideo.canPlayType('application/vnd.apple.mpegurl')) {
          startVideo.src = streamUrl;
          startVideo.addEventListener('loadedmetadata', () => {
            this.applyAvailableDuration(startVideo);
            startVideo.currentTime = this.clipRequest.startSeconds;
          });
        }
      }

      // BİTİŞ oynatıcısı: sadece bitiş anını gösterir
      if (endVideo) {
        if (Hls.isSupported()) {
          const hls = new Hls();
          this.clipHlsEnd = hls;
          hls.loadSource(streamUrl);
          hls.attachMedia(endVideo);
          hls.on(Hls.Events.MANIFEST_PARSED, () => {
            endVideo.currentTime = this.clipRequest.endSeconds;
          });
        } else if (endVideo.canPlayType('application/vnd.apple.mpegurl')) {
          endVideo.src = streamUrl;
          endVideo.addEventListener('loadedmetadata', () => {
            endVideo.currentTime = this.clipRequest.endSeconds;
          });
        }
      }
    }, 200);
  }

  private applyAvailableDuration(video: HTMLVideoElement) {
    let available = 0;
    if (video.seekable.length > 0) {
      const end = video.seekable.end(video.seekable.length - 1);
      if (isFinite(end)) available = Math.floor(end);
    }
    if (!available && isFinite(video.duration)) {
      available = Math.floor(video.duration);
    }
    if (!available) return;

    this.maxStreamDuration = available;

    if (this.clipRequest.endSeconds > available) {
      this.clipRequest.endSeconds = available;
      this.endTimeRealStr = this.formatRealTimeLabel(available);
    }
    if (this.clipRequest.startSeconds > available) {
      this.clipRequest.startSeconds = Math.max(0, available - 15);
      this.startTimeRealStr = this.formatRealTimeLabel(this.clipRequest.startSeconds);
    }
    this.cdr.detectChanges();
  }

  closeClipPanel() {
    this.showClipPanel = false;
    this.cleanupClip();
  }

  private cleanupClip() {
    if (this.clipHlsStart) {
      this.clipHlsStart.destroy();
      this.clipHlsStart = undefined;
    }
    if (this.clipHlsEnd) {
      this.clipHlsEnd.destroy();
      this.clipHlsEnd = undefined;
    }
  }

  formatRealTimeLabel = (secondsOffset: number): string => {
    const date = new Date(this.clipRequest.streamStartTime + secondsOffset * 1000);
    return date.toLocaleTimeString('tr-TR', { hour12: false });
  };

  // Sol tutamak sol videoyu, sağ tutamak sağ videoyu hareket ettirir
  onSliderChange() {
    this.startTimeRealStr = this.formatRealTimeLabel(this.clipRequest.startSeconds);
    this.endTimeRealStr = this.formatRealTimeLabel(this.clipRequest.endSeconds);
    this.seekClipStart(this.clipRequest.startSeconds);
    this.seekClipEnd(this.clipRequest.endSeconds);
  }

  parseRealTimeStr(timeStr: string): number {
    const parts = timeStr.split(':');
    if (parts.length >= 2) {
      const streamDate = new Date(this.clipRequest.streamStartTime);
      const targetDate = new Date(streamDate.getTime());
      targetDate.setHours(parseInt(parts[0], 10) || 0);
      targetDate.setMinutes(parseInt(parts[1], 10) || 0);
      targetDate.setSeconds(parseInt(parts[2], 10) || 0);

      let diffSecs = Math.floor((targetDate.getTime() - streamDate.getTime()) / 1000);
      if (diffSecs < 0) diffSecs = 0;
      if (diffSecs > this.maxStreamDuration) diffSecs = this.maxStreamDuration;
      return diffSecs;
    }
    return 0;
  }

  onStartTimeStrChange(val: string) {
    this.startTimeRealStr = val;
    this.clipRequest.startSeconds = this.parseRealTimeStr(val);
    this.seekClipStart(this.clipRequest.startSeconds);
  }

  onEndTimeStrChange(val: string) {
    this.endTimeRealStr = val;
    this.clipRequest.endSeconds = this.parseRealTimeStr(val);
    this.seekClipEnd(this.clipRequest.endSeconds);
  }

  seekClipStart(seconds: number) {
    const video = document.getElementById('clip-video-start') as HTMLVideoElement;
    this.seekClipElement(video, seconds);
  }

  seekClipEnd(seconds: number) {
    const video = document.getElementById('clip-video-end') as HTMLVideoElement;
    this.seekClipElement(video, seconds);
  }

  private seekClipElement(video: HTMLVideoElement | null, seconds: number) {
    if (!video || isNaN(seconds)) return;
    if (video.duration && seconds > video.duration) {
      video.currentTime = Math.max(0, video.duration - 0.1);
    } else {
      video.currentTime = seconds;
    }
  }

  takeClip() {
    if (!this.clipRequest.presetId) {
      alert('Lütfen bir şablon seçin.');
      return;
    }
    if (this.clipRequest.startSeconds >= this.clipRequest.endSeconds) {
      alert('Bitiş süresi, başlangıç süresinden büyük olmalıdır!');
      return;
    }

    this.isClipping = true;
    const startTimeMs = this.clipRequest.streamStartTime + this.clipRequest.startSeconds * 1000;
    const endTimeMs = this.clipRequest.streamStartTime + this.clipRequest.endSeconds * 1000;

    const payload = {
      streamId: this.clipRequest.streamId,
      presetId: this.clipRequest.presetId,
      startTime: startTimeMs,
      endTime: endTimeMs,
    };

    this.liveService.createLiveClip(payload).subscribe({
      next: (res) => {
        this.isClipping = false;
        this.closeClipPanel();
        alert('Klip alma işlemi başarıyla tamamlandı!\nOluşan dosya: ' + res);
      },
      error: (err) => {
        console.error('Klip hatası', err);
        this.isClipping = false;
        // Backend hatanın sebebini metin olarak dönüyor; sabit mesajla değiştirmek
        // yerine olduğu gibi gösteriyoruz, yoksa sorun logları açmadan görünmüyor.
        const detail = typeof err?.error === 'string' ? err.error : (err?.message ?? '');
        alert('Klip alınırken hata oluştu!' + (detail ? '\n\n' + detail : ''));
      },
    });
  }
}
