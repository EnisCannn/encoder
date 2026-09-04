import { Routes } from '@angular/router';
import { PresetComponent } from './components/preset/preset';
import { JobComponent } from './components/job/job';
import { LiveStreamComponent } from './components/live-stream/live-stream';

export const routes: Routes = [
  { path: 'presets', component: PresetComponent },
  { path: 'jobs', component: JobComponent },
  { path: 'live', component: LiveStreamComponent },
  { path: '', redirectTo: '/presets', pathMatch: 'full' },
  { path: '**', redirectTo: '/presets' },
];
