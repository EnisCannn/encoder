import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, MatProgressBarModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** 'login' = giris, 'register' = yeni hesap. Tek ekranda ikisi de var. */
  mode = signal<'login' | 'register'>('login');

  username = '';
  password = '';

  busy = signal(false);
  error = signal('');
  info = signal('');

  toggleMode() {
    this.mode.set(this.mode() === 'login' ? 'register' : 'login');
    this.error.set('');
    this.info.set('');
  }

  submit() {
    if (this.busy()) return;
    this.error.set('');
    this.info.set('');

    if (!this.username.trim() || !this.password) {
      this.error.set('Kullanıcı adı ve parola zorunlu');
      return;
    }

    this.busy.set(true);
    if (this.mode() === 'login') {
      this.auth.login(this.username.trim(), this.password).subscribe({
        next: () => {
          this.busy.set(false);
          // Guard nereden yonlendirdiyse oraya don, yoksa ana sayfaya
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/presets';
          this.router.navigateByUrl(returnUrl);
        },
        error: (err: HttpErrorResponse) => {
          this.busy.set(false);
          this.error.set(this.messageOf(err, 'Giriş yapılamadı'));
        },
      });
      return;
    }

    this.auth.register(this.username.trim(), this.password).subscribe({
      next: () => {
        this.busy.set(false);
        this.mode.set('login');
        this.info.set('Hesap oluşturuldu, şimdi giriş yapabilirsiniz.');
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(this.messageOf(err, 'Kayıt yapılamadı'));
      },
    });
  }

  /** Backend mesaji varsa onu goster; sunucuya hic ulasilamadiysa bunu soyle. */
  private messageOf(err: HttpErrorResponse, fallback: string): string {
    if (err.status === 0) return 'Sunucuya ulaşılamıyor. Servisler ayakta mı?';
    return err.error?.message || fallback;
  }
}
