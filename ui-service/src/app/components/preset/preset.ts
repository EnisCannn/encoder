import { Component, ViewChild, OnInit, TemplateRef } from '@angular/core';
import { forkJoin } from 'rxjs';
import { CommonModule } from '@angular/common';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSelectModule } from '@angular/material/select';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { PresetService } from '../../services/preset.service';

export interface Preset {
  id?: string;
  name: string;
  videoCodec: string;
  audioCodec: string;
  width: number;
  height: number;
  videoBitrate: number;
  audioBitrate: number;
  frameRate: number;
  format: string;
  createdAt?: string;
  /** Kalibrasyon taramasinin urettigi sablon mu? Listede rozetle ayirt ediliyor. */
  calibration?: boolean;
}

@Component({
  selector: 'app-preset',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatSortModule,
    MatFormFieldModule,
    MatInputModule,
    MatDialogModule,
    MatSelectModule,
    FormsModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    MatCardModule,
    MatPaginatorModule,
  ],
  templateUrl: './preset.html',
  styleUrl: './preset.css',
})
export class PresetComponent implements OnInit {
  displayedColumns: string[] = [
    'select',
    'id', 'name', 'format', 'videoCodec', 'resolution', 'videoBitrate', 'audioCodec', 'audioBitrate', 'frameRate', 'actions',
  ];
  dataSource = new MatTableDataSource<Preset>([]);

  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild('dialogTemplate') dialogTemplate!: TemplateRef<any>;

  formResolution: string = '1920x1080';
  isEditMode: boolean = false;

  newPresetData: Preset = {
    name: '', videoCodec: 'H264', audioCodec: 'AAC', width: 1920, height: 1080,
    videoBitrate: 5000, audioBitrate: 128, frameRate: 30, format: 'MP4',
  };

  // Tüm sütunlar için filtre değişkenleri
  topSearch = {
    id: '', name: '', format: '', videoCodec: '', resolution: '',
    videoBitrate: '', audioCodec: '', audioBitrate: '', frameRate: '',
    // Kalibrasyon sablonlari da listede; varsayilan olarak yalnizca kendi
    // sablonlarini gosteriyoruz, 150 otomatik kayit listeyi bogmasin diye.
    tur: 'normal'
  };

  constructor(
    public dialog: MatDialog,
    private presetService: PresetService,
  ) {}

  ngOnInit() {
    this.loadPresets();
  }

  loadPresets() {
    this.presetService.getAllPresets().subscribe({
      next: (data) => {
        // En son olusturulan en ustte; API sirasi rastgeleydi ve yeni sablon
        // sayfalar arasinda kayboluyordu.
        this.dataSource.data = [...data].sort((a, b) =>
          (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
        if (!this.dataSource.sort && this.sort) this.dataSource.sort = this.sort;
        if (!this.dataSource.paginator && this.paginator) {
          this.dataSource.paginator = this.paginator;
          this.paginator.pageSizeOptions = [10, 20, 30];
          this.paginator.pageSize = 10;
        }
        this.setupFilterPredicate();
        // Varsayilan "tur" filtresi normal sablonlar; yuklemede de uygulanmali,
        // yoksa dataSource.filter bos kalir ve predicate hic calismaz.
        this.applyTopFilters();
      },
      error: (err) => console.error('Veriler çekilirken hata oluştu:', err),
    });
  }

  setupFilterPredicate() {
    this.dataSource.filterPredicate = (data: Preset, filter: string) => {
      const search = JSON.parse(filter);
      const resString = `${data.width}x${data.height}`;

      const matchId = (data.id || '').toLowerCase().includes((search.id || '').toLowerCase());
      const matchName = data.name.toLowerCase().includes((search.name || '').toLowerCase());
      const matchFormat = search.format === '' || data.format === search.format;
      const matchVCodec = search.videoCodec === '' || data.videoCodec === search.videoCodec;
      const matchRes = resString.toLowerCase().includes((search.resolution || '').toLowerCase());

      const matchVBitrate = search.videoBitrate === '' || String(data.videoBitrate).includes(search.videoBitrate);
      const matchACodec = search.audioCodec === '' || data.audioCodec === search.audioCodec;
      const matchABitrate = search.audioBitrate === '' || String(data.audioBitrate) === String(search.audioBitrate);
      const matchFps = search.frameRate === '' || String(data.frameRate) === String(search.frameRate);

      const kalibrasyon = data.calibration === true;
      const matchTur =
        search.tur === '' ? true : search.tur === 'kalibrasyon' ? kalibrasyon : !kalibrasyon;

      return matchTur && matchId && matchName && matchFormat && matchVCodec && matchRes &&
        matchVBitrate && matchACodec && matchABitrate && matchFps;
    };
  }

  /** Filtre paneli acik mi. Arama alani her zaman gorunur, gerisi katlanir. */
  showFilters = false;

  /** Filtre alanlarinin ekranda gorunen adlari; rozetlerde kullaniliyor. */
  private readonly filterLabels: Record<string, string> = {
    id: 'ID', name: 'Ad', format: 'Format', videoCodec: 'V. Codec',
    resolution: 'Çözünürlük', videoBitrate: 'V. Bitrate',
    audioCodec: 'A. Codec', audioBitrate: 'A. Bitrate', frameRate: 'FPS',
    tur: 'Tür',
  };

  toggleFilters() {
    this.showFilters = !this.showFilters;
  }

  /** Baslikta "14 sablon" yerine gorunen/toplam ayrimini gosterebilmek icin. */
  get calibrationCount(): number {
    return this.dataSource.data.filter((p) => p.calibration === true).length;
  }

  /** Dolu olan filtreler; rozet olarak gosterilip tek tek kaldirilabiliyor. */
  get activeFilters(): { key: string; label: string; value: string }[] {
    return Object.entries(this.topSearch)
      .filter(([, v]) => v !== '' && v != null)
      .map(([key, value]) => ({
        key,
        label: this.filterLabels[key] ?? key,
        value: String(value),
      }));
  }

  removeFilter(key: string) {
    (this.topSearch as Record<string, string>)[key] = '';
    this.applyTopFilters();
  }

  /**
   * Filtreler artik her degisiklikte aninda uygulaniyor; "Filtrele" dugmesine
   * basmak gerekmiyor. Dugme kaldirilinca filtre cubugu iki satirdan bire indi.
   */
  applyTopFilters() {
    this.dataSource.filter = JSON.stringify(this.topSearch);
  }

  clearTopFilters() {
    this.topSearch = {
      id: '', name: '', format: '', videoCodec: '', resolution: '',
      videoBitrate: '', audioCodec: '', audioBitrate: '', frameRate: '', tur: 'normal'
    };
    this.applyTopFilters();
  }


  // ---------- Coklu secim ve toplu silme ----------
  // Satir eylemleri uc nokta menusune tasindi. Ayni islemi cok satirda yapmak
  // her satir icin menu acmak demek olurdu; bunun yerine secim kutulari ve
  // secim yapilinca beliren bir toplu islem seridi var.

  selectedIds = new Set<string>();
  isBulkBusy = false;

  private get visibleRows(): any[] {
    return this.dataSource.filteredData;
  }

  isSelected(row: any): boolean {
    return this.selectedIds.has(row.id);
  }

  toggleRow(row: any) {
    if (this.selectedIds.has(row.id)) {
      this.selectedIds.delete(row.id);
    } else {
      this.selectedIds.add(row.id);
    }
  }

  get allVisibleSelected(): boolean {
    const rows = this.visibleRows;
    return rows.length > 0 && rows.every((r) => this.selectedIds.has(r.id));
  }

  /** Bazisi secili: baslik kutusu belirsiz gorunsun. */
  get someVisibleSelected(): boolean {
    const rows = this.visibleRows;
    return rows.some((r) => this.selectedIds.has(r.id)) && !this.allVisibleSelected;
  }

  toggleAll() {
    if (this.allVisibleSelected) {
      this.visibleRows.forEach((r) => this.selectedIds.delete(r.id));
    } else {
      this.visibleRows.forEach((r) => this.selectedIds.add(r.id));
    }
  }

  clearSelection() {
    this.selectedIds.clear();
  }

  get selectedCount(): number {
    return this.visibleRows.filter((r) => this.selectedIds.has(r.id)).length;
  }

  /** Silme ucu tekil; secilen her kayit icin ayri istek atilip hepsi beklenir. */
  bulkDelete() {
    const ids = this.visibleRows.filter((r) => this.selectedIds.has(r.id)).map((r) => r.id);
    if (ids.length === 0) return;
    if (!confirm(ids.length + ' şablon silinecek. Emin misiniz?')) return;

    this.isBulkBusy = true;
    forkJoin(ids.map((id) => this.presetService.deletePreset(id as any))).subscribe({
      next: () => this.bulkDone(),
      error: (err) => {
        console.error('Toplu silme hatası', err);
        this.bulkDone();
      },
    });
  }

  private bulkDone() {
    this.isBulkBusy = false;
    this.clearSelection();
    this.loadPresets();
  }

  addNewPreset() {
    this.isEditMode = false;
    this.newPresetData = { name: '', videoCodec: 'H264', audioCodec: 'AAC', width: 1920, height: 1080, videoBitrate: 5000, audioBitrate: 128, frameRate: 30, format: 'MP4' };
    this.formResolution = '1920x1080';
    this.dialog.open(this.dialogTemplate, { width: '600px' });
  }

  editPreset(preset: Preset) {
    this.isEditMode = true;
    this.newPresetData = { ...preset };
    this.formResolution = `${preset.width}x${preset.height}`;
    this.dialog.open(this.dialogTemplate, { width: '600px' });
  }

  savePreset() {
    if (!this.newPresetData.name || !this.newPresetData.videoBitrate) {
      alert('Lütfen Şablon Adı ve Video Bitrate (Kbps) alanlarını doldurun!');
      return;
    }
    const dims = this.formResolution.split('x');
    this.newPresetData.width = Number(dims[0]);
    this.newPresetData.height = Number(dims[1]);

    if (this.isEditMode && this.newPresetData.id) {
      this.presetService.updatePreset(this.newPresetData.id as any, this.newPresetData).subscribe({
        next: () => { this.dialog.closeAll(); this.loadPresets(); },
        // Backend kural ihlallerini metinle aciklar; onu yutup "Backend Hatasi"
        // demek hatanin sebebini gormeyi imkansiz kiliyordu.
        error: (err) => alert(err.error?.hata || err.error?.message || 'Şablon kaydedilemedi.'),
      });
    } else {
      this.presetService.createPreset(this.newPresetData).subscribe({
        next: () => { this.dialog.closeAll(); this.loadPresets(); },
        // Backend kural ihlallerini metinle aciklar; onu yutup "Backend Hatasi"
        // demek hatanin sebebini gormeyi imkansiz kiliyordu.
        error: (err) => alert(err.error?.hata || err.error?.message || 'Şablon kaydedilemedi.'),
      });
    }
  }

  deletePreset(id: string) {
    if (confirm('Bu şablonu kalıcı olarak silmek istediğinize emin misiniz?')) {
      this.presetService.deletePreset(id as any).subscribe({
        next: () => this.loadPresets(),
        error: (err) => console.error('Silme hatası', err),
      });
    }
  }
}
