import { PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './services/auth.service';

/**
 * Korumali sayfalar icin. Prerender sirasinda (sunucu) localStorage olmadigi
 * icin herkes cikis yapmis gorunur; o yuzden kontrol yalnizca tarayicida
 * yapiliyor, aksi halde tum sayfalar login olarak prerender edilirdi.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const platformId = inject(PLATFORM_ID);
  if (!isPlatformBrowser(platformId)) return true;

  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn()) return true;

  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Yalnizca ADMIN rolu; digerleri ana sayfaya donuyor. authGuard'dan sonra calisir. */
export const adminGuard: CanActivateFn = () => {
  const platformId = inject(PLATFORM_ID);
  if (!isPlatformBrowser(platformId)) return true;

  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.isAdmin() ? true : router.createUrlTree(['/presets']);
};
