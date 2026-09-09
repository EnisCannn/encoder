import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, MatIconModule],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  /** Sol menu. Tek yerden yonetiliyor; sablonda dongu ile basiliyor. */
  readonly navItems = [
    { path: '/presets', icon: 'style', label: 'Şablonlar' },
    { path: '/encode-sets', icon: 'layers', label: 'Paketler' },
    { path: '/jobs', icon: 'list_alt', label: 'İşlemler' },
    { path: '/live', icon: 'live_tv', label: 'Canlı Yayınlar' },
    { path: '/quality', icon: 'insights', label: 'Kalite Analizi' },
  ];
}
