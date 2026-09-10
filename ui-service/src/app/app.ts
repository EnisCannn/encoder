import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from './services/auth.service';
import { ThemeService } from './services/theme.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, MatIconModule, MatTooltipModule],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly themeService = inject(ThemeService);

  constructor() {
    // Kayitli tema tercihini <html> uzerine uygula
    this.themeService.init();
  }

  /** Kabuk (sol menu) yalnizca giris yapilmisken cizilir; login sayfasi tam ekran. */
  readonly isLoggedIn = this.auth.isLoggedIn;
  readonly currentUser = this.auth.currentUser;

  /** Sol menu. Tek yerden yonetiliyor; sablonda dongu ile basiliyor. */
  readonly navItems = [
    { path: '/presets', icon: 'style', label: 'Şablonlar' },
    { path: '/encode-sets', icon: 'layers', label: 'Paketler' },
    { path: '/jobs', icon: 'list_alt', label: 'İşlemler' },
    { path: '/live', icon: 'live_tv', label: 'Canlı Yayınlar' },
    { path: '/quality', icon: 'insights', label: 'Kalite Analizi' },
    { path: '/settings', icon: 'settings', label: 'Ayarlar' },
  ];

  readonly roleLabels: Record<string, string> = {
    ADMIN: 'Yönetici',
    USER: 'Kullanıcı',
  };

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
