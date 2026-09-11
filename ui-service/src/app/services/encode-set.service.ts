import { Injectable } from '@angular/core';
import { environment } from '../environment';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Preset } from '../components/preset/preset';

// Backend'deki EncodeSetResponse ile birebir ayni
export interface EncodeSet {
  id?: string;
  name: string;
  description?: string;
  presets: Preset[];
  isActive?: boolean;
  createdAt?: string;
}

// Backend'deki CreateEncodeSetRequest ile birebir ayni
export interface EncodeSetRequest {
  name: string;
  description?: string;
  presetIds: string[];
}

@Injectable({
  providedIn: 'root',
})
export class EncodeSetService {
  private apiUrl = `${environment.apiBaseUrl}/api/encode-sets`;

  constructor(private http: HttpClient) {}

  // 1. Tum paketleri getir (GET)
  getAllSets(): Observable<EncodeSet[]> {
    return this.http.get<EncodeSet[]>(this.apiUrl);
  }

  // 2. Yeni paket olustur (POST)
  createSet(request: EncodeSetRequest): Observable<EncodeSet> {
    return this.http.post<EncodeSet>(this.apiUrl, request);
  }

  // 3. Paket guncelle (PUT)
  updateSet(id: string, request: EncodeSetRequest): Observable<EncodeSet> {
    return this.http.put<EncodeSet>(`${this.apiUrl}/${id}`, request);
  }

  // 4. Paket sil (DELETE)
  deleteSet(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
