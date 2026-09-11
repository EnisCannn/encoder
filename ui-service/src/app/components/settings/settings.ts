import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, MatProgressBarModule],
  templateUrl: './settings.html',
  styleUrl: './settings.css',
})
export class SettingsComponent {
  private readonly auth = inject(AuthService);
  private readonly theme = inject(ThemeService);
  private readonly router = inject(Router);

  readonly currentUser = this.auth.currentUser;
  readonly currentTheme = this.theme.theme;

  readonly roleLabels: Record<string, string> = {
    ADMIN: 'Yönetici',
    USER: 'Kullanıcı',
  };

  currentPassword = '';
  newPassword = '';
  newPasswordRepeat = '';

  busy = signal(false);
  error = signal('');
  success = signal('');

  setTheme(theme: 'dark' | 'light') {
    this.theme.set(theme);
  }

  changePassword() {
    if (this.busy()) return;
    this.error.set('');
    this.success.set('');

    if (!this.currentPassword || !this.newPassword) {
      this.error.set('Mevcut ve yeni parola zorunlu');
      return;
    }
    if (this.newPassword.length < 6) {
      this.error.set('Yeni parola en az 6 karakter olmalı');
      return;
    }
    if (this.newPassword !== this.newPasswordRepeat) {
      this.error.set('Yeni parolalar birbiriyle uyuşmuyor');
      return;
    }

    this.busy.set(true);
    this.auth.changePassword(this.currentPassword, this.newPassword).subscribe({
      next: () => {
        this.busy.set(false);
        this.clearForm();
        this.success.set('Parolanız güncellendi.');
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        // 401 interceptor tarafindan yakalanip cikisa goturulurdu; parola
        // hatasi da 401 donuyor. Bu yuzden mesaji burada gosteriyoruz.
        this.error.set(
          err.status === 0
            ? 'Sunucuya ulaşılamıyor.'
            : err.error?.message || 'Parola değiştirilemedi',
        );
      },
    });
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  private clearForm() {
    this.currentPassword = '';
    this.newPassword = '';
    this.newPasswordRepeat = '';
  }
}
