import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../environment';

export interface AuthResponse {
  token: string;
  username: string;
  role: string;
  expiresInSeconds: number;
}

export interface AuthUser {
  username: string;
  role: string;
}

/** Yonetici panelindeki kullanici satiri. */
export interface AdminUser {
  id: string;
  username: string;
  role: string;
  enabled: boolean;
  createdAt: string;
}

const TOKEN_KEY = 'encoder.token';
const USER_KEY = 'encoder.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly apiUrl = `${environment.apiBaseUrl}/api/auth`;
  private readonly adminUrl = `${this.apiUrl}/admin/users`;

  /** Oturum durumu sinyalde; arayuz buna gore aciliyor. */
  private readonly userSignal = signal<AuthUser | null>(this.readStoredUser());

  readonly currentUser = this.userSignal.asReadonly();
  readonly isLoggedIn = computed(() => this.userSignal() !== null);
  readonly isAdmin = computed(() => this.userSignal()?.role === 'ADMIN');

  login(username: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/login`, { username, password })
      .pipe(tap((response) => this.store(response)));
  }

  register(username: string, password: string): Observable<unknown> {
    return this.http.post(`${this.apiUrl}/register`, { username, password });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<unknown> {
    return this.http.post(`${this.apiUrl}/change-password`, { currentPassword, newPassword });
  }

  /**
   * Acilista oturumun sunucuda hala gecerli oldugunu dogrular. Banlanan ya da
   * silinen kullanicinin token'i suresi dolana kadar gecerli; bu istek 401
   * dondurunce interceptor oturumu dusuruyor.
   */
  verifySession() {
    if (!this.isLoggedIn()) return;
    this.http.get<AuthUser>(`${this.apiUrl}/me`).subscribe({ error: () => {} });
  }

  // --- Yonetici paneli (yalnizca ADMIN; gateway rolu token'dan aliyor) ---

  listUsers(): Observable<AdminUser[]> {
    return this.http.get<AdminUser[]>(this.adminUrl);
  }

  adminSetPassword(id: string, newPassword: string): Observable<unknown> {
    return this.http.put(`${this.adminUrl}/${id}/password`, { newPassword });
  }

  adminSetEnabled(id: string, enabled: boolean): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.adminUrl}/${id}/enabled`, { enabled });
  }

  adminDeleteUser(id: string): Observable<unknown> {
    return this.http.delete(`${this.adminUrl}/${id}`);
  }

  logout() {
    if (this.isBrowser()) {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    }
    this.userSignal.set(null);
  }

  /** Interceptor her istekte bunu okur. */
  get token(): string | null {
    return this.isBrowser() ? localStorage.getItem(TOKEN_KEY) : null;
  }

  private store(response: AuthResponse) {
    const user: AuthUser = { username: response.username, role: response.role };
    if (this.isBrowser()) {
      localStorage.setItem(TOKEN_KEY, response.token);
      localStorage.setItem(USER_KEY, JSON.stringify(user));
    }
    this.userSignal.set(user);
  }

  private readStoredUser(): AuthUser | null {
    // Sunucuda prerender sirasinda localStorage yok; oradan null donuyoruz.
    if (!this.isBrowser()) return null;
    const raw = localStorage.getItem(USER_KEY);
    if (!raw || !localStorage.getItem(TOKEN_KEY)) return null;
    try {
      return JSON.parse(raw) as AuthUser;
    } catch {
      return null;
    }
  }

  private isBrowser(): boolean {
    return isPlatformBrowser(this.platformId);
  }
}
