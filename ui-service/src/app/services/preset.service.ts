import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Preset } from '../components/preset/preset'; // Tablodaki interface'imiz

@Injectable({
  providedIn: 'root',
})
export class PresetService {
  // Spring Boot Main Servisimizin adresi (Controller'daki ismin bu olduğunu varsayıyoruz)
  private apiUrl = 'http://localhost:8081/api/presets';

  constructor(private http: HttpClient) {}

  // 1. Tüm şablonları backend'den getir (GET)
  getAllPresets(): Observable<Preset[]> {
    return this.http.get<Preset[]>(this.apiUrl);
  }

  // 2. Yeni şablon oluştur (POST)
  createPreset(preset: Preset): Observable<Preset> {
    return this.http.post<Preset>(this.apiUrl, preset);
  }

  // 3. Şablon güncelle (PUT)
  updatePreset(id: number, preset: Preset): Observable<Preset> {
    return this.http.put<Preset>(`${this.apiUrl}/${id}`, preset);
  }

  // 4. Şablon sil (DELETE)
  deletePreset(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
