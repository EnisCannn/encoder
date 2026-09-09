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
  /**
   * Kaynak dosyanin olculeri. Ayni dosya her yuklendiginde yeni bir videoId
   * aliyor; gruplama bu alanlara gore yapiliyor ki ayni dosyanin uzerinde
   * farkli zamanlarda yapilan taramalar tek egride birlessin.
   */
  sourceSize: number | null;
  sourceWidth: number | null;
  sourceHeight: number | null;
  sourceDuration: number | null;
}

@Injectable({ providedIn: 'root' })
export class QualityService {
  private apiUrl = 'http://localhost:8081/api/quality';

  constructor(private http: HttpClient) {}

  getCurvePoints(): Observable<QualityCurvePoint[]> {
    const timestamp = new Date().getTime();
    return this.http.get<QualityCurvePoint[]>(`${this.apiUrl}/curves?t=${timestamp}`);
  }

}
