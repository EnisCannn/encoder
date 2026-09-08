import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';

import {
  QualityCurvePoint,
  QualityService,
  SourceVideo,
} from '../../services/quality.service';

/** Grafikte çizilen tek nokta; ekran koordinatları önceden hesaplanıyor. */
interface PlotPoint {
  bitrate: number;
  vmaf: number;
  x: number;
  y: number;
  presetName: string;
  frameRate: number | null;
  vmafMin: number | null;
  isWinner: boolean;
  tooltip: string;
}

/** Bir çözünürlüğün eğrisi. */
interface PlotSeries {
  height: number;
  label: string;
  color: string;
  points: PlotPoint[];
  path: string;
}

interface AxisTick {
  value: number;
  pos: number;
  label: string;
}

/** Tablo satırı: bir bitrate seviyesinde çözünürlüklerin karşılaştırması. */
interface ComparisonRow {
  bitrate: number;
  scores: (number | null)[];
  winnerHeight: number | null;
}

@Component({
  selector: 'app-quality',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatTooltipModule,
    MatButtonModule,
    MatInputModule,
  ],
  templateUrl: './quality.html',
  styleUrl: './quality.css',
})
export class QualityComponent implements OnInit {
  // --- Çizim alanı ölçüleri (viewBox koordinatları) ---
  readonly chartWidth = 900;
  readonly chartHeight = 460;
  readonly margin = { top: 24, right: 140, bottom: 56, left: 56 };

  allPoints: QualityCurvePoint[] = [];
  videos: { id: string; name: string; count: number }[] = [];
  selectedVideoKey = '';

  // Kare hizi da seciliyor: farkli fps'teki ciktilar birbiriyle karsilastirilamaz.
  // 25 fps kaynaktan 24 fps'e dusen bir cikti kare atladigi icin ayni bitrate'te
  // ~13 puan dusuk aliyor; ayni egriye konursa egri zikzak yapar ve yaniltir.
  //
  // Gruplama sablondaki nominal degere degil, ciktidan ffprobe ile okunan
  // GERCEK kare hizina gore yapiliyor: sablonda alan bos birakildiginda
  // (onerilen kullanim) nominal deger yok ama cikti yine belli bir hizda.
  frameRates: { value: number; count: number }[] = [];
  selectedFrameRate: number | null = null;

  series: PlotSeries[] = [];
  xTicks: AxisTick[] = [];
  yTicks: AxisTick[] = [];
  comparisonRows: ComparisonRow[] = [];
  seriesHeights: number[] = [];

  isLoading = true;
  loadError = '';

  // --- Tarama formu ---
  showSweepForm = false;
  sourceVideos: SourceVideo[] = [];
  isStartingSweep = false;
  sweepMessage = '';
  sweepForm = {
    videoId: '',
    resolution: '1280x720',
    // Bos = kaynagin kare hizi korunur. Olcumlerimiz gosterdi ki fps'i
    // degistirmek ayni bitrate'te 13 puana kadar kayiplara yol aciyor.
    frameRate: '' as string,
    bitrates: '400, 600, 800, 1200, 1800',
  };

  readonly resolutionOptions = [
    { label: '480p (854x480)', value: '854x480' },
    { label: '720p (1280x720)', value: '1280x720' },
    { label: '1080p (1920x1080)', value: '1920x1080' },
    { label: '1440p (2560x1440)', value: '2560x1440' },
    { label: '2160p (3840x2160)', value: '3840x2160' },
  ];

  /** VMAF eşikleri: 90 üstü kaynaktan ayırt edilemez kabul ediliyor. */
  readonly thresholds = [
    { value: 90, label: 'Mükemmel', color: '#2e7d32' },
    { value: 80, label: 'Çok iyi', color: '#9e9d24' },
    { value: 70, label: 'Kabul edilebilir', color: '#ef6c00' },
  ];

  private readonly palette = ['#1565c0', '#00838f', '#6a1b9a', '#ad1457', '#4e342e'];

  private xMin = 0;
  private xMax = 1;
  private yMin = 40;
  private yMax = 100;

  constructor(
    private qualityService: QualityService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    this.load();
    this.loadSourceVideos();
  }

  private loadSourceVideos() {
    this.qualityService.getSourceVideos().subscribe({
      next: (data) => {
        this.sourceVideos = data;
        if (!this.sweepForm.videoId && data.length) {
          this.sweepForm.videoId = data[0].id;
        }
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Videolar çekilemedi', err),
    });
  }

  toggleSweepForm() {
    this.showSweepForm = !this.showSweepForm;
    this.sweepMessage = '';
  }

  /** "400, 600, 800" metnini sayi listesine cevirir. */
  private parseBitrates(): number[] {
    return this.sweepForm.bitrates
      .split(/[,\s]+/)
      .map((t) => parseInt(t, 10))
      .filter((n) => !isNaN(n) && n > 0);
  }

  get parsedBitrateCount(): number {
    return this.parseBitrates().length;
  }

  startSweep() {
    const bitrates = this.parseBitrates();
    if (!this.sweepForm.videoId) {
      this.sweepMessage = 'Lütfen bir kaynak video seçin.';
      return;
    }
    if (bitrates.length === 0) {
      this.sweepMessage = 'En az bir geçerli bitrate girin.';
      return;
    }

    const [width, height] = this.sweepForm.resolution.split('x').map(Number);
    const fps = this.sweepForm.frameRate.trim();

    this.isStartingSweep = true;
    this.sweepMessage = '';

    this.qualityService
      .startSweep({
        videoId: this.sweepForm.videoId,
        width,
        height,
        frameRate: fps ? Number(fps) : null,
        bitrates,
      })
      .subscribe({
        next: (res) => {
          this.isStartingSweep = false;
          this.sweepMessage =
            `${res?.olusturulanIs ?? bitrates.length} iş kuyruğa alındı. ` +
            'Encode ve ölçüm bitince "Yenile" ile grafiğe düşecek.';
          this.cdr.detectChanges();
        },
        error: (err) => {
          this.isStartingSweep = false;
          this.sweepMessage = 'Tarama başlatılamadı: ' + (err?.error?.hata ?? 'Bilinmeyen hata');
          this.cdr.detectChanges();
        },
      });
  }

  load() {
    this.isLoading = true;
    this.loadError = '';
    this.qualityService.getCurvePoints().subscribe({
      next: (data) => {
        this.allPoints = data;
        this.buildVideoList();
        this.buildFrameRateList();
        this.rebuild();
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Kalite verisi çekilemedi', err);
        this.loadError = 'Ölçüm verisi alınamadı.';
        this.isLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  /**
   * VMAF farklı kaynak videolar arasında karşılaştırılabilir değil; aynı bitrate
   * sakin bir videoda 90, hareketli bir videoda 65 verir. Bu yüzden grafik her
   * zaman TEK bir video için çiziliyor ve seçici zorunlu.
   */
  private buildVideoList() {
    const map = new Map<string, { id: string; name: string; count: number }>();
    for (const p of this.allPoints) {
      const key = p.videoId ?? p.videoName;
      const existing = map.get(key);
      if (existing) {
        existing.count++;
      } else {
        map.set(key, { id: key, name: p.videoName, count: 1 });
      }
    }
    // En çok ölçümü olan video başta: eğri çıkma ihtimali en yüksek olan o
    this.videos = [...map.values()].sort((a, b) => b.count - a.count);

    if (!this.videos.some((v) => v.id === this.selectedVideoKey)) {
      this.selectedVideoKey = this.videos.length ? this.videos[0].id : '';
    }
  }

  /** Secili videonun olculmus kare hizlari; en cok olcume sahip olan varsayilan. */
  private buildFrameRateList() {
    const counts = new Map<number, number>();
    for (const p of this.allPoints) {
      if ((p.videoId ?? p.videoName) !== this.selectedVideoKey) continue;
      const fps = this.effectiveFps(p);
      counts.set(fps, (counts.get(fps) ?? 0) + 1);
    }

    this.frameRates = [...counts.entries()]
      .map(([value, count]) => ({ value, count }))
      .sort((a, b) => b.count - a.count || b.value - a.value);

    if (!this.frameRates.some((f) => f.value === this.selectedFrameRate)) {
      this.selectedFrameRate = this.frameRates.length ? this.frameRates[0].value : null;
    }
  }

  onVideoChange() {
    this.buildFrameRateList();
    this.rebuild();
  }

  onFrameRateChange() {
    this.rebuild();
  }

  /**
   * Bir noktanin karsilastirma icin gecerli kare hizi: once olculen gercek
   * deger, o yoksa sablondaki nominal deger.
   */
  private effectiveFps(p: QualityCurvePoint): number {
    return p.outputFrameRate ?? p.frameRate ?? 0;
  }

  formatFps(value: number): string {
    if (!value) {
      return 'Bilinmiyor';
    }
    return Number.isInteger(value) ? `${value} fps` : `${value.toFixed(2)} fps`;
  }

  get selectedVideoName(): string {
    return this.videos.find((v) => v.id === this.selectedVideoKey)?.name ?? '';
  }

  get hasData(): boolean {
    return this.series.length > 0;
  }

  get plotWidth(): number {
    return this.chartWidth - this.margin.left - this.margin.right;
  }

  get plotHeight(): number {
    return this.chartHeight - this.margin.top - this.margin.bottom;
  }

  // ---------- Grafik kurulumu ----------

  private rebuild() {
    const points = this.dedupe(
      this.allPoints.filter(
        (p) =>
          (p.videoId ?? p.videoName) === this.selectedVideoKey &&
          this.effectiveFps(p) === this.selectedFrameRate,
      ),
    );

    this.series = [];
    this.xTicks = [];
    this.yTicks = [];
    this.comparisonRows = [];
    this.seriesHeights = [];

    if (points.length === 0) {
      return;
    }

    const bitrates = points.map((p) => p.videoBitrate);
    const scores = points.map((p) => p.vmafScore);

    // Bitrate ekseni logaritmik: 400-8000 arası 20 kat, doğrusal eksende
    // ilginç olan düşük bant sol kenara sıkışıyor.
    this.xMin = Math.log10(Math.min(...bitrates)) - 0.05;
    this.xMax = Math.log10(Math.max(...bitrates)) + 0.05;

    // Y sıfırdan başlamıyor: iş 55-95 bandında geçiyor, sıfırdan başlarsa
    // bütün eğriler üst kenarda birbirine yapışır.
    this.yMin = Math.max(0, Math.floor((Math.min(...scores) - 6) / 5) * 5);
    this.yMax = Math.min(100, Math.ceil((Math.max(...scores) + 4) / 5) * 5);
    if (this.yMax - this.yMin < 20) {
      this.yMax = Math.min(100, this.yMin + 20);
    }

    const winners = this.computeWinners(points);
    this.buildSeries(points, winners);
    this.buildTicks(bitrates);
    this.buildComparison(points, winners);
  }

  /**
   * Ayni cozunurluk + ayni bitrate birden fazla kez olculmus olabilir (is tekrar
   * calistirildiysa ya da olcum yontemi degistiyse). En son olcumu tutuyoruz;
   * aksi halde egri ayni x degerinde iki noktaya sahip olur ve geri doner.
   */
  private dedupe(points: QualityCurvePoint[]): QualityCurvePoint[] {
    const best = new Map<string, QualityCurvePoint>();
    for (const p of points) {
      const key = `${p.height}|${p.videoBitrate}`;
      const current = best.get(key);
      if (!current || (p.measuredAt ?? '') > (current.measuredAt ?? '')) {
        best.set(key, p);
      }
    }
    return [...best.values()];
  }

  /** Her bitrate seviyesinde en yüksek skoru alan çözünürlük (üst zarf). */
  private computeWinners(points: QualityCurvePoint[]): Map<number, number> {
    const best = new Map<number, { height: number; vmaf: number }>();
    for (const p of points) {
      const current = best.get(p.videoBitrate);
      if (!current || p.vmafScore > current.vmaf) {
        best.set(p.videoBitrate, { height: p.height, vmaf: p.vmafScore });
      }
    }
    const winners = new Map<number, number>();
    best.forEach((v, k) => winners.set(k, v.height));
    return winners;
  }

  private buildSeries(points: QualityCurvePoint[], winners: Map<number, number>) {
    const byHeight = new Map<number, QualityCurvePoint[]>();
    for (const p of points) {
      const list = byHeight.get(p.height);
      if (list) list.push(p);
      else byHeight.set(p.height, [p]);
    }

    const heights = [...byHeight.keys()].sort((a, b) => b - a);
    this.seriesHeights = heights;

    this.series = heights.map((height, index) => {
      const raw = byHeight.get(height)!.slice().sort((a, b) => a.videoBitrate - b.videoBitrate);

      const plotPoints: PlotPoint[] = raw.map((p) => ({
        bitrate: p.videoBitrate,
        vmaf: p.vmafScore,
        x: this.xScale(p.videoBitrate),
        y: this.yScale(p.vmafScore),
        presetName: p.presetName,
        frameRate: p.frameRate,
        vmafMin: p.vmafMin,
        isWinner: winners.get(p.videoBitrate) === p.height && byHeight.size > 1,
        tooltip:
          `${p.presetName}\n${p.width}x${p.height} • ${p.videoBitrate} kbps` +
          ` • ${this.formatFps(this.effectiveFps(p))}` +
          `\nVMAF ${p.vmafScore.toFixed(2)}` +
          (p.vmafMin != null ? ` (en düşük kare ${p.vmafMin.toFixed(1)})` : ''),
      }));

      return {
        height,
        label: `${height}p`,
        color: this.palette[index % this.palette.length],
        points: plotPoints,
        // Tek nokta varsa çizgi çizilmiyor: çizgi ara değerleri ölçülmüş gibi gösterir
        path:
          plotPoints.length > 1
            ? plotPoints.map((pt, i) => `${i === 0 ? 'M' : 'L'}${pt.x},${pt.y}`).join(' ')
            : '',
      };
    });
  }

  private buildTicks(bitrates: number[]) {
    const unique = [...new Set(bitrates)].sort((a, b) => a - b);
    this.xTicks = unique.map((b) => ({
      value: b,
      pos: this.xScale(b),
      label: b >= 1000 ? `${(b / 1000).toFixed(b % 1000 === 0 ? 0 : 1)}k` : `${b}`,
    }));

    this.yTicks = [];
    for (let v = this.yMin; v <= this.yMax; v += 5) {
      this.yTicks.push({ value: v, pos: this.yScale(v), label: `${v}` });
    }
  }

  private buildComparison(points: QualityCurvePoint[], winners: Map<number, number>) {
    const bitrates = [...new Set(points.map((p) => p.videoBitrate))].sort((a, b) => a - b);
    this.comparisonRows = bitrates.map((bitrate) => ({
      bitrate,
      scores: this.seriesHeights.map((h) => {
        const hit = points.find((p) => p.videoBitrate === bitrate && p.height === h);
        return hit ? hit.vmafScore : null;
      }),
      winnerHeight: winners.get(bitrate) ?? null,
    }));
  }

  // ---------- Ölçekler ----------

  xScale(bitrate: number): number {
    const ratio = (Math.log10(bitrate) - this.xMin) / (this.xMax - this.xMin);
    return this.margin.left + ratio * this.plotWidth;
  }

  yScale(vmaf: number): number {
    const ratio = (vmaf - this.yMin) / (this.yMax - this.yMin);
    return this.margin.top + (1 - ratio) * this.plotHeight;
  }

  /** Eşik çizgisi ancak görünür aralıktaysa çizilir. */
  thresholdVisible(value: number): boolean {
    return value > this.yMin && value < this.yMax;
  }

  legendY(index: number): number {
    return this.margin.top + 16 + index * 22;
  }

  get legendX(): number {
    return this.chartWidth - this.margin.right + 20;
  }

  vmafColor(score: number): string {
    if (score >= 90) return '#2e7d32';
    if (score >= 80) return '#558b2f';
    if (score >= 70) return '#ef6c00';
    return '#c62828';
  }
}
