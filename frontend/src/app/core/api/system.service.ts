import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';

/**
 * Subset of docker-java's `Info` object exposed by `GET /system/info`.
 * Keys are PascalCase because the backend forwards the raw Docker JSON.
 */
export interface SystemInfoDto {
  Architecture: string;
  OperatingSystem: string;
  OSType: string;
  KernelVersion: string;
  NCPU: number;
  MemTotal: number;
  ServerVersion: string;
  Name: string;
  Containers: number;
  ContainersRunning: number;
  ContainersStopped: number;
  Images: number;
}

@Injectable({ providedIn: 'root' })
export class SystemService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  info(): Observable<SystemInfoDto> {
    return this.http.get<SystemInfoDto>(`${this.base}/system/info`);
  }
}
