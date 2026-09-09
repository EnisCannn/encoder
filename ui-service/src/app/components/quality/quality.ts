import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatButtonModule } from '@angular/material/button';

import { QualityCurvePoint, QualityService } from '../../services/quality.service';

/** Grafikte çizilen tek nokta; ekran koordinatları önceden hesaplanıyor. */
interface PlotPoint {
  vmaf: number;
  x: number;
  y: number;
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
  /** Cizgi her zaman cizilir; yazi sigmiyorsa gizlenir. */
  showLabel: boolean;
}

/** Eşik çizgisi: y konumu grafiğe göre değiştiği için modelde tutuluyor. */
interface ThresholdLine {
  label: string;
  color: string;
  y: number;
}

interface LegendEntry {
  label: string;
  color: string;
  y: number;
}

/**
 * Çizime hazır bir grafik. İki grafik de (bitrate ekseni ve fps ekseni) aynı
 * modeli üretiyor, şablon tek bir ng-template ile ikisini de çiziyor.
 */
interface ChartModel {
  empty: boolean;
  title: string;
  subtitle: string;
  xAxisLabel: string;
  series: PlotSeries[];
  xTicks: AxisTick[];
  yTicks: AxisTick[];
  thresholdLines: ThresholdLine[];
  legend: LegendEntry[];
}

/** Karar matrisinin tek hücresi: bu bitrate + bu fps icin onerilen cozunurluk. */
interface LadderCell {
  empty: boolean;
  label: string;
  color: string;
  background: string;
  /** En yuksek skoru alan degil, esdeger olup daha ucuz olan secildi. */
  tie: boolean;
  tooltip: string;
}

interface LadderRow {
  label: string;
  cells: LadderCell[];
}

/**
 * "Hangi cozunurluk?" tablosu. Iki grafik tek bir dilime bakiyor (bir fps ya da
 * bir bitrate sabit); bu tablo butun dilimlerin kararini tek ekranda veriyor.
 */
interface LadderTable {
  empty: boolean;
  bitrates: number[];
  rows: LadderRow[];
  tieCount: number;
}

/** Tablo satırı: bir x seviyesinde çözünürlüklerin karşılaştırması. */
interface ComparisonRow {
  label: string;
  scores: (number | null)[];
  winnerHeight: number | null;
}

/**
 * Bir grafiğin sayısal karşılığı. İki tablo da (bitrate ve kare hızı) aynı
 * modeli üretiyor; tek fark satırların neye göre açıldığı.
 */
interface ComparisonTable {
  empty: boolean;
  title: string;
  firstColumn: string;
  heights: number[];
  rows: ComparisonRow[];
  note: string;
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
  ],
  templateUrl: './quality.html',
  styleUrl: './quality.css',
})
export class QualityComponent implements OnInit {
  // --- Çizim alanı ölçüleri (viewBox koordinatları) ---
  // Iki grafik yan yana durdugu icin tek grafige gore daha dar; viewBox
  // oldugundan gercek piksel boyutunu kapsayici belirliyor.
  readonly chartWidth = 620;
  readonly chartHeight = 400;
  readonly margin = { top: 20, right: 96, bottom: 50, left: 48 };

  allPoints: QualityCurvePoint[] = [];
  videos: { id: string; name: string; count: number; uploads: number; detail: string }[] = [];
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

  // Ikinci grafik bunun tersini yapiyor: bitrate sabitleniyor, kare hizi
  // eksene cikiyor. Boylece "fps'i degistirmek kaliteye ne yapiyor" sorusu
  // dogrudan okunuyor.
  bitrates: { value: number; count: number; fpsCount: number }[] = [];
  selectedBitrate: number | null = null;

  bitrateChart: ChartModel = this.emptyChart();
  fpsChart: ChartModel = this.emptyChart();

  bitrateTable: ComparisonTable = this.emptyTable();
  fpsTable: ComparisonTable = this.emptyTable();
  ladder: LadderTable = { empty: true, bitrates: [], rows: [], tieCount: 0 };

  /**
   * Bu farkin altinda iki cozunurluk pratikte ayirt edilemiyor; orada daha
   * ucuz olani (dusuk cozunurluk) onermek dogru cevap. Olcumlerde 1080p ile
   * 720p'nin 0.1 puanla ayrildigi hucreler var: "kazanan 1080p" demek
   * yaniltici olurdu.
   */
  readonly tieThreshold = 2;

  isLoading = true;
  loadError = '';

  /** VMAF eşikleri: 90 üstü kaynaktan ayırt edilemez kabul ediliyor. */
  readonly thresholds = [
    { value: 90, label: 'Mükemmel', color: '#3fb950' },
    { value: 80, label: 'Çok iyi', color: '#d29922' },
    { value: 70, label: 'Kabul edilebilir', color: '#db6d28' },
  ];

  // Koyu zeminde okunan, birbirinden ayrilan seri renkleri.
  private readonly palette = ['#4493f8', '#3fb9a0', '#bc8cff', '#f778ba', '#e3b341'];

  /** Cozunurluk -> renk; iki grafikte de ayni. rebuild() dolduruyor. */
  private heightColors = new Map<number, string>();

  constructor(
    private qualityService: QualityService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    this.load();
  }

  load() {
    this.isLoading = true;
    this.loadError = '';
    this.qualityService.getCurvePoints().subscribe({
      next: (data) => {
        this.allPoints = data;
        this.buildVideoList();
        this.buildFrameRateList();
        this.buildBitrateList();
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
    const map = new Map<
      string,
      { id: string; name: string; count: number; uploadIds: Set<string>; detail: string }
    >();

    for (const p of this.allPoints) {
      const key = this.sourceKey(p);
      const existing = map.get(key);
      if (existing) {
        existing.count++;
        if (p.videoId) existing.uploadIds.add(p.videoId);
      } else {
        map.set(key, {
          id: key,
          name: p.videoName,
          count: 1,
          uploadIds: new Set(p.videoId ? [p.videoId] : []),
          detail:
            p.sourceWidth && p.sourceHeight
              ? `${p.sourceWidth}x${p.sourceHeight}`
              : '',
        });
      }
    }

    // En çok ölçümü olan video başta: eğri çıkma ihtimali en yüksek olan o
    this.videos = [...map.values()]
      .map((v) => ({
        id: v.id,
        name: v.name,
        count: v.count,
        uploads: v.uploadIds.size,
        detail: v.detail,
      }))
      .sort((a, b) => b.count - a.count);

    if (!this.videos.some((v) => v.id === this.selectedVideoKey)) {
      this.selectedVideoKey = this.videos.length ? this.videos[0].id : '';
    }
  }

  /**
   * Bir noktanin ait oldugu KAYNAK dosya kimligi.
   *
   * videoId kullanilamiyor: ayni dosya her yuklendiginde yeni bir videoId
   * aliyor, boylece bir taramanin noktalari ile ayni dosya uzerinde sonradan
   * yapilan taramalarin noktalari ayri gruplara dusuyordu. Listede ikisi de
   * ayni adla gorundugu icin yeni olcumler "hic gelmemis" gibi duruyordu.
   *
   * Icerik ayni oldugu surece VMAF skorlari karsilastirilabilir; dosya adi +
   * boyut + cozunurluk + sure ayni ise ayni kaynak kabul ediliyor. Kaynak
   * bilgisi eksik olan eski kayitlarda videoId'ye geri donuluyor.
   */
  private sourceKey(p: QualityCurvePoint): string {
    if (p.sourceSize == null) {
      return p.videoId ?? p.videoName;
    }
    return [
      p.videoName,
      p.sourceSize,
      p.sourceWidth ?? '?',
      p.sourceHeight ?? '?',
      p.sourceDuration ?? '?',
    ].join('|');
  }

  /** Secili videonun noktalari; iki liste de iki grafik de bunun uzerinden. */
  private pointsForVideo(): QualityCurvePoint[] {
    return this.allPoints.filter((p) => this.sourceKey(p) === this.selectedVideoKey);
  }

  /** Secili videonun olculmus kare hizlari; en cok olcume sahip olan varsayilan. */
  private buildFrameRateList() {
    const counts = new Map<number, number>();
    for (const p of this.pointsForVideo()) {
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

  /**
   * Ikinci grafigin secicisi. Liste bitrate sirasinda gosteriliyor ama
   * varsayilan olarak EN COK KARE HIZI olculmus bitrate seciliyor: tek fps'i
   * olan bir bitrate'te grafik tek noktaya duser ve hicbir sey anlatmaz.
   */
  private buildBitrateList() {
    const map = new Map<number, { count: number; fps: Set<number> }>();
    for (const p of this.pointsForVideo()) {
      const entry = map.get(p.videoBitrate);
      if (entry) {
        entry.count++;
        entry.fps.add(this.effectiveFps(p));
      } else {
        map.set(p.videoBitrate, { count: 1, fps: new Set([this.effectiveFps(p)]) });
      }
    }

    this.bitrates = [...map.entries()]
      .map(([value, v]) => ({ value, count: v.count, fpsCount: v.fps.size }))
      .sort((a, b) => a.value - b.value);

    if (!this.bitrates.some((b) => b.value === this.selectedBitrate)) {
      const best = [...this.bitrates].sort(
        (a, b) => b.fpsCount - a.fpsCount || b.count - a.count || b.value - a.value,
      )[0];
      this.selectedBitrate = best ? best.value : null;
    }
  }

  onVideoChange() {
    this.buildFrameRateList();
    this.buildBitrateList();
    this.rebuild();
  }

  onFrameRateChange() {
    this.rebuild();
  }

  onBitrateChange() {
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

  formatBitrate(value: number): string {
    return `${value} kbps`;
  }

  get selectedVideoName(): string {
    return this.videos.find((v) => v.id === this.selectedVideoKey)?.name ?? '';
  }

  get hasData(): boolean {
    return !this.bitrateChart.empty || !this.fpsChart.empty;
  }

  get plotWidth(): number {
    return this.chartWidth - this.margin.left - this.margin.right;
  }

  get plotHeight(): number {
    return this.chartHeight - this.margin.top - this.margin.bottom;
  }

  get legendX(): number {
    return this.chartWidth - this.margin.right + 16;
  }

  // ---------- Grafik kurulumu ----------

  private rebuild() {
    const videoPoints = this.pointsForVideo();

    // Renk cozunurluge sabitleniyor, grafik icindeki siraya degil. Aksi halde
    // sagdaki grafikte tek seri kalinca 720p mavi, soldakinde turkuaz oluyor
    // ve iki grafik yan yana okunamiyor.
    this.heightColors = new Map(
      [...new Set(videoPoints.map((p) => p.height))]
        .sort((a, b) => b - a)
        .map((h, i) => [h, this.palette[i % this.palette.length]]),
    );

    // 1. grafik: kare hizi sabit, bitrate eksende.
    const byFps = this.dedupe(
      videoPoints.filter((p) => this.effectiveFps(p) === this.selectedFrameRate),
      (p) => p.videoBitrate,
    );
    this.bitrateChart = this.buildChart(byFps, {
      title: 'Bitrate – Kalite Eğrisi',
      subtitle:
        this.selectedFrameRate != null
          ? `${this.formatFps(this.selectedFrameRate)} · bitrate artırmanın nerede doyduğunu gösterir`
          : '',
      xAxisLabel: 'Video bitrate (kbps, logaritmik)',
      log: true,
      xOf: (p) => p.videoBitrate,
      xLabel: (b) => (b >= 1000 ? `${(b / 1000).toFixed(b % 1000 === 0 ? 0 : 1)}k` : `${b}`),
    });

    // 2. grafik: bitrate sabit, kare hizi eksende.
    const byBitrate = this.dedupe(
      videoPoints.filter((p) => p.videoBitrate === this.selectedBitrate),
      (p) => this.effectiveFps(p),
    );
    this.fpsChart = this.buildChart(byBitrate, {
      title: 'Kare Hızı – Kalite Eğrisi',
      subtitle:
        this.selectedBitrate != null
          ? `${this.formatBitrate(this.selectedBitrate)} · aynı bant genişliğinde fps'in bedelini gösterir`
          : '',
      xAxisLabel: 'Çıktının kare hızı (fps)',
      log: false,
      xOf: (p) => this.effectiveFps(p),
      xLabel: (f) => (Number.isInteger(f) ? `${f}` : f.toFixed(2)),
    });

    // Her tablo ustundeki grafigin sayisal karsiligi; ayni noktalardan,
    // ayni dedupe'tan geciyorlar ki tablo ile grafik birbirini tutsun.
    this.bitrateTable = this.buildComparison(byFps, {
      title: 'Bitrate Karşılaştırması',
      firstColumn: 'Bitrate',
      valueOf: (p) => p.videoBitrate,
      label: (v) => `${v} kbps`,
      note:
        "Ölçüm her iki videoyu da 1920x1080'e getirip karşılaştırır; düşük çözünürlüklü " +
        'çıktılar büyütüldüğü için doğal bir tavana çarpar. Bu, 1080p ekranda izleyen ' +
        'birinin gördüğüdür — küçük ekranda algılanan kalite bundan iyidir.',
    });

    this.fpsTable = this.buildComparison(byBitrate, {
      title: 'Kare Hızı Karşılaştırması',
      firstColumn: 'Kare Hızı',
      valueOf: (p) => this.effectiveFps(p),
      label: (v) => this.formatFps(v),
      note:
        'Kaynağın kare hızından farklı bir fps’e çevrilen çıktı kare atlar ya da kare ' +
        'tekrarlar; ikisi de VMAF’ı düşürür. Aynı bitrate’te en yüksek skoru genelde ' +
        'kaynağın kendi hızı alır.',
    });

    // Karar matrisi secicilerden bagimsiz: butun olcumleri kullaniyor.
    this.ladder = this.buildLadder(videoPoints);
  }

  /**
   * "Hangi cozunurluk?" matrisi: satirlar kare hizi, sutunlar bitrate, hucre
   * o kombinasyonda onerilen cozunurluk.
   *
   * Oneri her zaman en yuksek skor DEGIL: fark tieThreshold'un altindaysa
   * esdeger kabul edilip daha dusuk cozunurluk oneriliyor. Ayni kaliteyi daha
   * az encode/decode maliyetiyle veriyor.
   */
  private buildLadder(points: QualityCurvePoint[]): LadderTable {
    const table: LadderTable = { empty: true, bitrates: [], rows: [], tieCount: 0 };
    if (points.length === 0) {
      return table;
    }
    table.empty = false;

    // Ayni (fps, bitrate, cozunurluk) birden fazla olculmusse en sonuncusu
    const best = new Map<string, QualityCurvePoint>();
    for (const p of points) {
      const key = `${this.effectiveFps(p)}|${p.videoBitrate}|${p.height}`;
      const current = best.get(key);
      if (!current || (p.measuredAt ?? '') > (current.measuredAt ?? '')) {
        best.set(key, p);
      }
    }
    const clean = [...best.values()];

    table.bitrates = [...new Set(clean.map((p) => p.videoBitrate))].sort((a, b) => a - b);
    const fpsValues = [...new Set(clean.map((p) => this.effectiveFps(p)))].sort((a, b) => a - b);

    table.rows = fpsValues.map((fps) => ({
      label: this.formatFps(fps),
      cells: table.bitrates.map((bitrate) => {
        const adaylar = clean
          .filter((p) => this.effectiveFps(p) === fps && p.videoBitrate === bitrate)
          .sort((a, b) => b.vmafScore - a.vmafScore);

        if (adaylar.length === 0) {
          return {
            empty: true, label: '—', color: '#bdbdbd', background: 'transparent',
            tie: false, tooltip: 'Bu kombinasyon ölçülmedi.',
          };
        }

        const enIyi = adaylar[0];
        // Esdeger olanlar arasindan EN DUSUK cozunurluk: ayni kaliteyi ucuza verir
        const esdeger = adaylar.filter((p) => enIyi.vmafScore - p.vmafScore < this.tieThreshold);
        const onerilen = esdeger.reduce((a, b) => (a.height <= b.height ? a : b));
        const tie = onerilen.height !== enIyi.height;
        if (tie) table.tieCount++;

        const color = this.heightColors.get(onerilen.height) ?? '#37474f';
        return {
          empty: false,
          label: `${onerilen.height}p`,
          color,
          background: color + '1f',
          tie,
          tooltip:
            adaylar.map((p) => `${p.height}p — VMAF ${p.vmafScore.toFixed(1)}`).join('\n') +
            (tie
              ? `\n\n${onerilen.height}p önerildi: ${enIyi.height}p ile arasındaki fark ` +
                `${(enIyi.vmafScore - onerilen.vmafScore).toFixed(1)} puan (${this.tieThreshold} puanın altı), ` +
                'aynı kaliteyi daha ucuza veriyor.'
              : ''),
        };
      }),
    }));

    return table;
  }

  /**
   * Ayni cozunurluk + ayni x degeri birden fazla kez olculmus olabilir (is
   * tekrar calistirildiysa, olcum yontemi degistiyse ya da ayni dosya tekrar
   * yuklendiyse). En son olcumu tutuyoruz; aksi halde egri ayni x degerinde
   * iki noktaya sahip olur ve geri doner.
   */
  private dedupe(
    points: QualityCurvePoint[],
    xOf: (p: QualityCurvePoint) => number,
  ): QualityCurvePoint[] {
    const best = new Map<string, QualityCurvePoint>();
    for (const p of points) {
      const key = `${p.height}|${xOf(p)}`;
      const current = best.get(key);
      if (!current || (p.measuredAt ?? '') > (current.measuredAt ?? '')) {
        best.set(key, p);
      }
    }
    return [...best.values()];
  }

  private emptyChart(): ChartModel {
    return {
      empty: true,
      title: '',
      subtitle: '',
      xAxisLabel: '',
      series: [],
      xTicks: [],
      yTicks: [],
      thresholdLines: [],
      legend: [],
    };
  }

  /**
   * Iki grafigi de ureten ortak kurulum. Fark sadece x ekseninde: birinde
   * logaritmik bitrate, digerinde dogrusal kare hizi. Y ekseni ikisinde de
   * VMAF, boylece yan yana bakarken ayni dille okunuyorlar.
   */
  private buildChart(
    points: QualityCurvePoint[],
    opts: {
      title: string;
      subtitle: string;
      xAxisLabel: string;
      log: boolean;
      xOf: (p: QualityCurvePoint) => number;
      xLabel: (value: number) => string;
    },
  ): ChartModel {
    const model = this.emptyChart();
    model.title = opts.title;
    model.subtitle = opts.subtitle;
    model.xAxisLabel = opts.xAxisLabel;

    if (points.length === 0) {
      return model;
    }
    model.empty = false;

    const xValues = points.map(opts.xOf);
    const scores = points.map((p) => p.vmafScore);

    // Bitrate ekseni logaritmik: 400-8000 arasi 20 kat, dogrusal eksende
    // ilginc olan dusuk bant sol kenara sikisiyor. Kare hizi ekseni ise dar
    // bir aralik (24-60) oldugu icin dogrusal kaliyor.
    let xMin: number;
    let xMax: number;
    if (opts.log) {
      xMin = Math.log10(Math.min(...xValues)) - 0.05;
      xMax = Math.log10(Math.max(...xValues)) + 0.05;
    } else {
      const lo = Math.min(...xValues);
      const hi = Math.max(...xValues);
      // Tek deger varsa aralik sifir olur ve nokta sol kenara yapisir.
      const pad = hi > lo ? (hi - lo) * 0.12 : Math.max(1, lo * 0.05);
      xMin = lo - pad;
      xMax = hi + pad;
    }

    // Y sifirdan baslamiyor: is 55-95 bandinda geciyor, sifirdan baslarsa
    // butun egriler ust kenarda birbirine yapisir.
    let yMin = Math.max(0, Math.floor((Math.min(...scores) - 6) / 5) * 5);
    let yMax = Math.min(100, Math.ceil((Math.max(...scores) + 4) / 5) * 5);
    if (yMax - yMin < 20) {
      yMax = Math.min(100, yMin + 20);
    }

    const xScale = (v: number) => {
      const raw = opts.log ? Math.log10(v) : v;
      return this.margin.left + ((raw - xMin) / (xMax - xMin)) * this.plotWidth;
    };
    const yScale = (v: number) =>
      this.margin.top + (1 - (v - yMin) / (yMax - yMin)) * this.plotHeight;

    // Her x seviyesinde en yuksek skoru alan cozunurluk (ust zarf).
    const bestAtX = new Map<number, { height: number; vmaf: number }>();
    for (const p of points) {
      const x = opts.xOf(p);
      const current = bestAtX.get(x);
      if (!current || p.vmafScore > current.vmaf) {
        bestAtX.set(x, { height: p.height, vmaf: p.vmafScore });
      }
    }

    const byHeight = new Map<number, QualityCurvePoint[]>();
    for (const p of points) {
      const list = byHeight.get(p.height);
      if (list) list.push(p);
      else byHeight.set(p.height, [p]);
    }
    const heights = [...byHeight.keys()].sort((a, b) => b - a);

    model.series = heights.map((height, index) => {
      const raw = byHeight
        .get(height)!
        .slice()
        .sort((a, b) => opts.xOf(a) - opts.xOf(b));

      const plotPoints: PlotPoint[] = raw.map((p) => ({
        vmaf: p.vmafScore,
        x: xScale(opts.xOf(p)),
        y: yScale(p.vmafScore),
        isWinner: bestAtX.get(opts.xOf(p))?.height === p.height && byHeight.size > 1,
        tooltip:
          `${p.presetName}\n${p.width}x${p.height} • ${p.videoBitrate} kbps` +
          ` • ${this.formatFps(this.effectiveFps(p))}` +
          `\nVMAF ${p.vmafScore.toFixed(2)}` +
          (p.vmafMin != null ? ` (en düşük kare ${p.vmafMin.toFixed(1)})` : ''),
      }));

      return {
        height,
        label: `${height}p`,
        color: this.heightColors.get(height) ?? this.palette[index % this.palette.length],
        points: plotPoints,
        // Tek nokta varsa cizgi cizilmiyor: cizgi ara degerleri olculmus gibi gosterir
        path:
          plotPoints.length > 1
            ? plotPoints.map((pt, i) => `${i === 0 ? 'M' : 'L'}${pt.x},${pt.y}`).join(' ')
            : '',
      };
    });

    model.xTicks = this.thinLabels(
      [...new Set(xValues)]
        .sort((a, b) => a - b)
        .map((v) => ({ value: v, pos: xScale(v), label: opts.xLabel(v), showLabel: true })),
    );

    for (let v = yMin; v <= yMax; v += 5) {
      model.yTicks.push({ value: v, pos: yScale(v), label: `${v}`, showLabel: true });
    }

    // Esik cizgisi ancak gorunur aralikta cizilir.
    model.thresholdLines = this.thresholds
      .filter((t) => t.value > yMin && t.value < yMax)
      .map((t) => ({ label: `${t.value} · ${t.label}`, color: t.color, y: yScale(t.value) }));

    model.legend = model.series.map((s, i) => ({
      label: s.label,
      color: s.color,
      y: this.margin.top + 14 + i * 20,
    }));

    return model;
  }

  /**
   * Cakisan eksen yazilarini gizler. Logaritmik eksende yuksek bitrate'ler
   * birbirine yaklasiyor; grafik daraldiginda "3.5k 4k 4.5k 5k" ust uste
   * biniyordu. Cizgiler kaliyor, sadece yazi eleniyor.
   *
   * Soldan saga ilerleyip araligi tutmayani atliyoruz; son deger her zaman
   * yaziliyor (eksenin nereye kadar gittigi okunabilsin diye), gerekirse
   * ondan onceki yazi feda ediliyor.
   */
  private thinLabels(ticks: AxisTick[]): AxisTick[] {
    const minGap = 30;
    if (ticks.length < 2) {
      return ticks;
    }

    let lastLabeled = 0;
    for (let i = 1; i < ticks.length; i++) {
      if (ticks[i].pos - ticks[lastLabeled].pos < minGap) {
        ticks[i].showLabel = false;
      } else {
        lastLabeled = i;
      }
    }

    const last = ticks.length - 1;
    if (!ticks[last].showLabel) {
      ticks[last].showLabel = true;
      if (ticks[last].pos - ticks[lastLabeled].pos < minGap && lastLabeled !== 0) {
        ticks[lastLabeled].showLabel = false;
      }
    }
    return ticks;
  }

  private emptyTable(): ComparisonTable {
    return { empty: true, title: '', firstColumn: '', heights: [], rows: [], note: '' };
  }

  /**
   * Grafigin sayisal karsiligi. Satirlar valueOf'a gore aciliyor (bitrate ya
   * da kare hizi), sutunlar cozunurlukler. "Kazanan" o satirdaki en yuksek
   * skoru alan cozunurluk: grafikteki dolu halkanin tablo karsiligi.
   */
  private buildComparison(
    points: QualityCurvePoint[],
    opts: {
      title: string;
      firstColumn: string;
      valueOf: (p: QualityCurvePoint) => number;
      label: (value: number) => string;
      note: string;
    },
  ): ComparisonTable {
    const table = this.emptyTable();
    table.title = opts.title;
    table.firstColumn = opts.firstColumn;
    table.note = opts.note;

    if (points.length === 0) {
      return table;
    }
    table.empty = false;
    table.heights = [...new Set(points.map((p) => p.height))].sort((a, b) => b - a);

    const best = new Map<number, { height: number; vmaf: number }>();
    for (const p of points) {
      const v = opts.valueOf(p);
      const current = best.get(v);
      if (!current || p.vmafScore > current.vmaf) {
        best.set(v, { height: p.height, vmaf: p.vmafScore });
      }
    }

    const values = [...new Set(points.map(opts.valueOf))].sort((a, b) => a - b);
    table.rows = values.map((value) => ({
      label: opts.label(value),
      scores: table.heights.map((h) => {
        const hit = points.find((p) => opts.valueOf(p) === value && p.height === h);
        return hit ? hit.vmafScore : null;
      }),
      winnerHeight: best.get(value)?.height ?? null,
    }));

    return table;
  }

  vmafColor(score: number): string {
    if (score >= 90) return '#3fb950';
    if (score >= 80) return '#8fc250';
    if (score >= 70) return '#d29922';
    return '#f85149';
  }
}
