import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Bir encode çıktısının bitrate'i ve aldığı VMAF skoru: grafiğin tek noktası. */
export interface QualityCurvePoint {
  jobId: string;
  videoId: string | null;
  videoName: string;
  presetName: string;
  width: number;
  height: number;
  videoBitrate: number;
  frameRate: number | null;
  /** Ciktinin gercek kare hizi (ffprobe ile olculdu); gruplama buna gore yapilir. */
  outputFrameRate: number | null;
  vmafScore: number;
  vmafMin: number | null;
  vmafHarmonicMean: number | null;
  sampledSeconds: number | null;
  measuredAt: string | null;
}

/** Yüklenmiş kaynak video: tarama formundaki seçici için. */
export interface SourceVideo {
  id: string;
  originalFileName: string;
  width: number | null;
  height: number | null;
  duration: number | null;
}

/** Bir kalibrasyon taraması isteği. */
export interface SweepRequest {
  videoId: string;
  width: number;
  height: number;
  frameRate: number | null;
  bitrates: number[];
}

@Injectable({ providedIn: 'root' })
export class QualityService {
  private apiUrl = 'http://localhost:8081/api/quality';

  constructor(private http: HttpClient) {}

  getCurvePoints(): Observable<QualityCurvePoint[]> {
    const timestamp = new Date().getTime();
    return this.http.get<QualityCurvePoint[]>(`${this.apiUrl}/curves?t=${timestamp}`);
  }

  getSourceVideos(): Observable<SourceVideo[]> {
    const timestamp = new Date().getTime();
    return this.http.get<SourceVideo[]>(`http://localhost:8081/api/videos?t=${timestamp}`);
  }

  startSweep(request: SweepRequest): Observable<any> {
    return this.http.post(`${this.apiUrl}/sweep`, request);
  }
}
