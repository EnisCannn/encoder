import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface LiveStream {
  streamStartTime: number;
  streamEndTime: number; // YENİ: Yayının durdurulduğu an
  id: string;
  streamName: string;
  inputUrl: string;
  status: string; // LIVE, STOPPED, FAILED vb.
}

@Injectable({
  providedIn: 'root',
})
export class LiveStreamService {
  private apiUrl = 'http://localhost:8081/api/live';

  constructor(private http: HttpClient) {}

  getAllStreams(): Observable<LiveStream[]> {
    const timestamp = new Date().getTime();
    return this.http.get<LiveStream[]>(`${this.apiUrl}?t=${timestamp}`);
  }

  startStream(request: { streamName: string; inputUrl: string }): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/start`, request);
  }

  // Spring Boot'tan String (düz metin) döndüğü için responseType 'text' olarak ayarlandı
  stopStream(id: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/stop/${id}`, {}, { responseType: 'text' });
  }

  deleteStream(id: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${id}`, { responseType: 'text' });
  }

  // Canlı yayından klip alma isteği
  createLiveClip(request: {
    streamId: string;
    presetId: string;
    startTime: number;
    endTime: number;
  }): Observable<any> {
    return this.http.post(`${this.apiUrl}/clip`, request, { responseType: 'text' });
  }
}
