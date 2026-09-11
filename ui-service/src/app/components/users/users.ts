import { Component, OnInit, TemplateRef, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AdminUser, AuthService } from '../../services/auth.service';

/**
 * Yonetici paneli: kullanici listesi, parola sifirlama, banlama ve silme.
 * Yalnizca ADMIN gorur (adminGuard); sunucu da her istekte rolu ayrica kontrol
 * ediyor, bu yuzden arayuzdeki gizleme yalnizca kolaylik.
 */
@Component({
  selector: 'app-users',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatSortModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    MatDialogModule,
    MatProgressBarModule,
  ],
  templateUrl: './users.html',
  styleUrl: './users.css',
})
export class UsersComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild('passwordDialog') passwordDialog!: TemplateRef<unknown>;

  readonly displayedColumns = ['username', 'role', 'status', 'createdAt', 'actions'];
  readonly dataSource = new MatTableDataSource<AdminUser>([]);

  readonly currentUser = this.auth.currentUser;
  readonly roleLabels: Record<string, string> = {
    ADMIN: 'Yönetici',
    USER: 'Kullanıcı',
  };

  search = '';
  loading = signal(false);
  error = signal('');
  success = signal('');

  /** Parola sifirlama diyalogunun durumu */
  target: AdminUser | null = null;
  newPassword = '';
  newPasswordRepeat = '';
  dialogError = signal('');
  dialogBusy = signal(false);

  ngOnInit() {
    this.dataSource.filterPredicate = (row, filter) =>
      row.username.toLowerCase().includes(filter);
    this.load();
  }

  get bannedCount() {
    return this.dataSource.data.filter((u) => !u.enabled).length;
  }

  isSelf(user: AdminUser) {
    return user.username === this.currentUser()?.username;
  }

  load() {
    this.loading.set(true);
    this.auth.listUsers().subscribe({
      next: (users) => {
        this.dataSource.data = users;
        this.dataSource.sort = this.sort;
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(this.messageOf(err, 'Kullanıcılar yüklenemedi'));
      },
    });
  }

  applyFilter() {
    this.dataSource.filter = this.search.trim().toLowerCase();
  }

  // --- Parola sifirlama ---

  openPasswordDialog(user: AdminUser) {
    this.target = user;
    this.newPassword = '';
    this.newPasswordRepeat = '';
    this.dialogError.set('');
    this.dialog.open(this.passwordDialog, { width: '420px' });
  }

  savePassword() {
    if (this.dialogBusy() || !this.target) return;
    this.dialogError.set('');

    if (this.newPassword.length < 6) {
      this.dialogError.set('Yeni parola en az 6 karakter olmalı');
      return;
    }
    if (this.newPassword !== this.newPasswordRepeat) {
      this.dialogError.set('Parolalar birbiriyle uyuşmuyor');
      return;
    }

    const user = this.target;
    this.dialogBusy.set(true);
    this.auth.adminSetPassword(user.id, this.newPassword).subscribe({
      next: () => {
        this.dialogBusy.set(false);
        this.dialog.closeAll();
        this.flash(`${user.username} kullanıcısının parolası sıfırlandı.`);
      },
      error: (err: HttpErrorResponse) => {
        this.dialogBusy.set(false);
        this.dialogError.set(this.messageOf(err, 'Parola değiştirilemedi'));
      },
    });
  }

  // --- Ban / ban kaldirma ---

  toggleBan(user: AdminUser) {
    const enable = !user.enabled;
    if (!enable && !confirm(`${user.username} banlanacak; tekrar giriş yapamayacak. Emin misiniz?`)) {
      return;
    }

    this.auth.adminSetEnabled(user.id, enable).subscribe({
      next: (updated) => {
        // Yeniden yuklemek yerine satiri yerinde guncelle; siralama bozulmasin
        this.dataSource.data = this.dataSource.data.map((u) => (u.id === updated.id ? updated : u));
        this.flash(enable ? `${user.username} banı kaldırıldı.` : `${user.username} banlandı.`);
      },
      error: (err: HttpErrorResponse) => this.fail(this.messageOf(err, 'İşlem başarısız')),
    });
  }

  // --- Silme ---

  deleteUser(user: AdminUser) {
    if (!confirm(`${user.username} kalıcı olarak silinecek. Emin misiniz?`)) return;

    this.auth.adminDeleteUser(user.id).subscribe({
      next: () => {
        this.dataSource.data = this.dataSource.data.filter((u) => u.id !== user.id);
        this.flash(`${user.username} silindi.`);
      },
      error: (err: HttpErrorResponse) => this.fail(this.messageOf(err, 'Kullanıcı silinemedi')),
    });
  }

  private flash(message: string) {
    this.error.set('');
    this.success.set(message);
  }

  private fail(message: string) {
    this.success.set('');
    this.error.set(message);
  }

  private messageOf(err: HttpErrorResponse, fallback: string): string {
    if (err.status === 0) return 'Sunucuya ulaşılamıyor.';
    return err.error?.message || fallback;
  }
}
