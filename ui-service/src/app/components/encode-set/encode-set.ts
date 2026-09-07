import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';

import { EncodeSet, EncodeSetService } from '../../services/encode-set.service';
import { PresetService } from '../../services/preset.service';
import { Preset } from '../preset/preset';

@Component({
  selector: 'app-encode-set',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatChipsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
  ],
  templateUrl: './encode-set.html',
  styleUrl: './encode-set.css',
})
export class EncodeSetComponent implements OnInit {
  displayedColumns: string[] = ['name', 'description', 'presetCount', 'presets', 'actions'];
  dataSource = new MatTableDataSource<EncodeSet>([]);

  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild('dialogTemplate') dialogTemplate!: TemplateRef<any>;

  availablePresets: Preset[] = [];

  isEditMode = false;
  editingSetId: string | null = null;

  formData = {
    name: '',
    description: '',
    presetIds: [] as string[],
  };

  search = { name: '', preset: '' };

  constructor(
    public dialog: MatDialog,
    private encodeSetService: EncodeSetService,
    private presetService: PresetService,
  ) {}

  ngOnInit() {
    this.loadPresets();
    this.loadSets();
  }

  loadPresets() {
    this.presetService.getAllPresets().subscribe({
      next: (data) => (this.availablePresets = data),
      error: (err) => console.error('Sablonlar cekilirken hata olustu:', err),
    });
  }

  loadSets() {
    this.encodeSetService.getAllSets().subscribe({
      next: (data) => {
        this.dataSource.data = data;
        if (!this.dataSource.sort && this.sort) this.dataSource.sort = this.sort;
        if (!this.dataSource.paginator && this.paginator) this.dataSource.paginator = this.paginator;
        this.setupFilterPredicate();
      },
      error: (err) => console.error('Paketler cekilirken hata olustu:', err),
    });
  }

  setupFilterPredicate() {
    this.dataSource.filterPredicate = (data: EncodeSet, filter: string) => {
      const s = JSON.parse(filter);
      const matchName = data.name.toLowerCase().includes((s.name || '').toLowerCase());
      const presetNames = (data.presets || []).map((p) => p.name).join(' ').toLowerCase();
      const matchPreset = presetNames.includes((s.preset || '').toLowerCase());
      return matchName && matchPreset;
    };
  }

  applyFilters() {
    this.dataSource.filter = JSON.stringify(this.search);
  }

  clearFilters() {
    this.search = { name: '', preset: '' };
    this.applyFilters();
  }

  // Cozunurluk etiketi: 1920x1080 -> "1080p"
  resolutionLabel(preset: Preset): string {
    return preset.height ? `${preset.height}p` : `${preset.width}x${preset.height}`;
  }

  addNewSet() {
    this.isEditMode = false;
    this.editingSetId = null;
    this.formData = { name: '', description: '', presetIds: [] };
    this.dialog.open(this.dialogTemplate, { width: '600px' });
  }

  editSet(set: EncodeSet) {
    this.isEditMode = true;
    this.editingSetId = set.id ?? null;
    this.formData = {
      name: set.name,
      description: set.description ?? '',
      presetIds: (set.presets || []).map((p) => p.id!).filter(Boolean),
    };
    this.dialog.open(this.dialogTemplate, { width: '600px' });
  }

  /**
   * Secilen presetleri cozunurluge gore buyukten kucuge siralar.
   * Backend sirayi oldugu gibi saklar; ilerideki HLS master playlist ve SMIL
   * dosyasinda varyantlar bu sirayla yazilacagi icin sira onemli.
   */
  private sortedPresetIds(): string[] {
    const selected = this.availablePresets.filter((p) => this.formData.presetIds.includes(p.id!));
    selected.sort((a, b) => b.width * b.height - a.width * a.height);
    return selected.map((p) => p.id!);
  }

  saveSet() {
    if (!this.formData.name.trim()) {
      alert('Lutfen paket adini girin!');
      return;
    }
    if (this.formData.presetIds.length === 0) {
      alert('Pakette en az bir sablon secilmeli!');
      return;
    }

    const request = {
      name: this.formData.name.trim(),
      description: this.formData.description?.trim() || undefined,
      presetIds: this.sortedPresetIds(),
    };

    const done = {
      next: () => {
        this.dialog.closeAll();
        this.loadSets();
      },
      error: (err: any) => alert('Backend Hatasi: ' + (err?.error?.hata ?? 'Bilinmeyen hata')),
    };

    if (this.isEditMode && this.editingSetId) {
      this.encodeSetService.updateSet(this.editingSetId, request).subscribe(done);
    } else {
      this.encodeSetService.createSet(request).subscribe(done);
    }
  }

  deleteSet(id: string) {
    if (confirm('Bu paketi kalici olarak silmek istediginize emin misiniz?')) {
      this.encodeSetService.deleteSet(id).subscribe({
        next: () => this.loadSets(),
        error: (err) => console.error('Silme hatasi', err),
      });
    }
  }
}
