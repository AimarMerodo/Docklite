import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { API_BASE_URL } from './api.config';
import { PageResponse } from './dashboard.service';

export interface VolumeSummary {
  name: string;
  driver: string;
  mountpoint?: string;
  /** Names of containers (running or stopped) currently mounting this volume. */
  usedBy?: string[];
}

@Injectable({ providedIn: 'root' })
export class VolumeService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  list(): Observable<VolumeSummary[]> {
    return this.http
      .get<PageResponse<VolumeSummary> | VolumeSummary[]>(`${this.base}/volumes`)
      .pipe(map((data) => (Array.isArray(data) ? data : (data?.content ?? []))));
  }

  create(name: string, driver?: string): Observable<VolumeSummary> {
    return this.http.post<VolumeSummary>(`${this.base}/volumes`, {
      name,
      driver: driver ?? 'local',
    });
  }

  remove(name: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/volumes/${name}`);
  }
}
