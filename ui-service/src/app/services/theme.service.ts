import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

export type Theme = 'dark' | 'light';

const THEME_KEY = 'encoder.theme';

/**
 * Tema tercihi <html data-theme="..."> uzerinden uygulaniyor; renkler
 * styles.css'teki degisken bloklarindan geliyor. Tercih tarayicida saklaniyor,
 * sunucuya gitmiyor.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly platformId = inject(PLATFORM_ID);

  private readonly themeSignal = signal<Theme>(this.readStored());
  readonly theme = this.themeSignal.asReadonly();

  /** Uygulama acilirken bir kez cagriliyor. */
  init() {
    this.apply(this.themeSignal());
  }

  set(theme: Theme) {
    this.themeSignal.set(theme);
    if (this.isBrowser()) localStorage.setItem(THEME_KEY, theme);
    this.apply(theme);
  }

  toggle() {
    this.set(this.themeSignal() === 'dark' ? 'light' : 'dark');
  }

  private apply(theme: Theme) {
    if (!this.isBrowser()) return;
    // Koyu tema varsayilan oldugu icin nitelik yalnizca aydinlikta yaziliyor
    if (theme === 'light') {
      document.documentElement.setAttribute('data-theme', 'light');
    } else {
      document.documentElement.removeAttribute('data-theme');
    }
  }

  private readStored(): Theme {
    if (!this.isBrowser()) return 'dark';
    return localStorage.getItem(THEME_KEY) === 'light' ? 'light' : 'dark';
  }

  private isBrowser(): boolean {
    return isPlatformBrowser(this.platformId);
  }
}
