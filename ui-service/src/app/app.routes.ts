import { Routes } from '@angular/router';
import { PresetComponent } from './components/preset/preset';
import { JobComponent } from './components/job/job';
import { EncodeSetComponent } from './components/encode-set/encode-set';
import { LiveStreamComponent } from './components/live-stream/live-stream';
import { QualityComponent } from './components/quality/quality';
import { LoginComponent } from './components/login/login';
import { SettingsComponent } from './components/settings/settings';
import { UsersComponent } from './components/users/users';
import { authGuard, adminGuard } from './auth.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },

  // Giris yapilmadan hicbir sayfa acilmiyor; guard login'e yonlendiriyor
  { path: 'presets', component: PresetComponent, canActivate: [authGuard] },
  { path: 'encode-sets', component: EncodeSetComponent, canActivate: [authGuard] },
  { path: 'jobs', component: JobComponent, canActivate: [authGuard] },
  { path: 'live', component: LiveStreamComponent, canActivate: [authGuard] },
  { path: 'quality', component: QualityComponent, canActivate: [authGuard] },
  { path: 'settings', component: SettingsComponent, canActivate: [authGuard] },

  // Yonetici paneli: rolu USER olan dogrudan adres yazsa da giremez
  { path: 'users', component: UsersComponent, canActivate: [authGuard, adminGuard] },

  { path: '', redirectTo: '/presets', pathMatch: 'full' },
  { path: '**', redirectTo: '/presets' },
];
