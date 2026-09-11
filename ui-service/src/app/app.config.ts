import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
// HTTP İstekleri için gereken kütüphane eklendi
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { turkishPaginatorIntl } from './paginator-tr';
import { authInterceptor } from './auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideAnimationsAsync(),
    // Interceptor her istege JWT'yi ekliyor, 401'de oturumu dusuruyor
    provideHttpClient(withInterceptors([authInterceptor])),
    // Sayfalayicinin metinleri Turkce olsun
    { provide: MatPaginatorIntl, useFactory: turkishPaginatorIntl },
  ],
};
