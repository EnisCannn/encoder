import { Injectable } from '@angular/core';
import { environment } from '../environment';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

// Job arayüzünü (interface) temiz bir şekilde buraya tanımlıyoruz
export interface Job {
  id: string;
  inputFileName: string;
  outputFileName?: string;
  status: string;
  progress: number;
  // Rozette kalite etiketi bitrate ve fps'i de yazdigi icin bu alanlar gerekli
  preset?: {
    name: string;
    width?: number;
    height?: number;
    videoBitrate?: number;
    frameRate?: number | string | null;
  };
  videoId?: string;
  createdAt?: string;
  // Paket modunda dolu olur: aynı yüklemeden doğan işleri birbirine bağlar
  batchId?: string | null;
  encodeSetId?: string | null;
  // Altyazı: NONE | BURN | SIDECAR
  subtitleMode?: string;
  subtitleVttFileName?: string | null;
  subtitleLanguage?: string | null;
  subtitleLabel?: string | null;
  // Son VMAF olcumunun ortalamasi (0-100); henuz olculmediyse null
  vmafScore?: number | null;
}

@Injectable({
  providedIn: 'root',
})
export class JobService {
  private jobApiUrl = `${environment.apiBaseUrl}/api/encoding-jobs`;
  private videoApiUrl = `${environment.apiBaseUrl}/api/videos/upload`;
  private liveStreamApiUrl = `${environment.apiBaseUrl}/api/live`;
  private videoToolsApiUrl = `${environment.apiBaseUrl}/api/video-tools`;
  private qualityApiUrl = `${environment.apiBaseUrl}/api/quality`;

  constructor(private http: HttpClient) {}

  /** Secilen isler icin VMAF olcumunu kuyruga alir; paket satirinda hepsi birden gonderilir. */
  measureQuality(jobIds: string[]): Observable<any> {
    return this.http.post(`${this.qualityApiUrl}/measure`, { jobIds });
  }

  getAllJobs(): Observable<Job[]> {
    const timestamp = new Date().getTime();
    return this.http.get<Job[]>(`${this.jobApiUrl}?t=${timestamp}`);
  }

  uploadVideo(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<any>(this.videoApiUrl, formData);
  }

  // Tekli modda tek elemanlı, paket modunda setteki preset sayısı kadar iş döner
  createJob(formData: FormData): Observable<Job[]> {
    return this.http.post<Job[]>(this.jobApiUrl, formData);
  }

  getJobsByBatch(batchId: string): Observable<Job[]> {
    return this.http.get<Job[]>(`${this.jobApiUrl}/batch/${batchId}`);
  }

  // Paketin HLS master playlist adresi ve hazir olup olmadigi
  getBatchHls(batchId: string): Observable<{ masterPath: string; ready: string }> {
    return this.http.get<{ masterPath: string; ready: string }>(`${this.jobApiUrl}/batch/${batchId}/hls`);
  }

  getBatchSmilUrl(batchId: string): string {
    return `${this.jobApiUrl}/batch/${batchId}/smil`;
  }

  // SMIL manifestinin ham XML'i; oynatici kalite ve altyazi listesini bundan kurar
  getBatchSmil(batchId: string): Observable<string> {
    return this.http.get(`${this.jobApiUrl}/batch/${batchId}/smil`, { responseType: 'text' });
  }

  deleteJob(id: string): Observable<void> {
    return this.http.delete<void>(`${this.jobApiUrl}/${id}`);
  }

  startLiveStream(request: { streamName: string; inputUrl: string }): Observable<any> {
    return this.http.post<any>(`${this.liveStreamApiUrl}/start`, request);
  }

  createVideoClip(request: {
    videoId: string;
    startTime: string;
    endTime: string;
  }): Observable<any> {
    return this.http.post(`${this.videoToolsApiUrl}/cut`, request, { responseType: 'text' });
  }
}
