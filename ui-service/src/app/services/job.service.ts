import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

// Job arayüzünü (interface) temiz bir şekilde buraya tanımlıyoruz
export interface Job {
  id: string;
  inputFileName: string;
  outputFileName?: string;
  status: string;
  progress: number;
  preset?: { name: string };
  videoId?: string;
}

@Injectable({
  providedIn: 'root',
})
export class JobService {
  private jobApiUrl = 'http://localhost:8081/api/encoding-jobs';
  private videoApiUrl = 'http://localhost:8081/api/videos/upload';
  private liveStreamApiUrl = 'http://localhost:8081/api/live';
  private videoToolsApiUrl = 'http://localhost:8081/api/video-tools';

  constructor(private http: HttpClient) {}

  getAllJobs(): Observable<Job[]> {
    const timestamp = new Date().getTime();
    return this.http.get<Job[]>(`${this.jobApiUrl}?t=${timestamp}`);
  }

  uploadVideo(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<any>(this.videoApiUrl, formData);
  }

  createJob(formData: FormData): Observable<Job> {
    return this.http.post<Job>(this.jobApiUrl, formData);
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
