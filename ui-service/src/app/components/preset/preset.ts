import { Component, ViewChild, OnInit, TemplateRef } from '@angular/core';
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
    MatTooltipModule,
    MatCardModule,
    MatPaginatorModule,
  ],
  templateUrl: './preset.html',
  styleUrl: './preset.css',
})
export class PresetComponent implements OnInit {
  displayedColumns: string[] = [
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
    videoBitrate: '', audioCodec: '', audioBitrate: '', frameRate: ''
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
        this.dataSource.data = data;
        if (!this.dataSource.sort && this.sort) this.dataSource.sort = this.sort;
        if (!this.dataSource.paginator && this.paginator) {
          this.dataSource.paginator = this.paginator;
          this.paginator.pageSizeOptions = [10, 20, 30];
          this.paginator.pageSize = 10;
        }
        this.setupFilterPredicate();
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

      return matchId && matchName && matchFormat && matchVCodec && matchRes &&
        matchVBitrate && matchACodec && matchABitrate && matchFps;
    };
  }

  applyTopFilters() {
    this.dataSource.filter = JSON.stringify(this.topSearch);
  }

  clearTopFilters() {
    this.topSearch = {
      id: '', name: '', format: '', videoCodec: '', resolution: '',
      videoBitrate: '', audioCodec: '', audioBitrate: '', frameRate: ''
    };
    this.applyTopFilters();
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
        error: (err) => alert('Backend Hatası!'),
      });
    } else {
      this.presetService.createPreset(this.newPresetData).subscribe({
        next: () => { this.dialog.closeAll(); this.loadPresets(); },
        error: (err) => alert('Backend Hatası!'),
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
