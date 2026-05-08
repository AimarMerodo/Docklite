import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api.config';

export interface DashboardSummary {
  totalContainers: number;
  running: number;
  stopped: number;
  totalImages: number;
  totalNetworks: number;
  totalVolumes: number;
}

export type ActivityAction =
  | 'CREATE'
  | 'START'
  | 'STOP'
  | 'RESTART'
  | 'DELETE'
  | 'PULL'
  | 'CONNECT'
  | 'DISCONNECT';

export type ResourceType = 'CONTAINER' | 'IMAGE' | 'NETWORK' | 'VOLUME';

export interface ActivityEntry {
  id: number;
  userId: number;
  resourceId: string;
  resourceType: ResourceType;
  action: ActivityAction;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  getSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.base}/system/dashboard`);
  }

  recentActivity(size = 10): Observable<PageResponse<ActivityEntry>> {
    const params = new HttpParams().set('page', 0).set('size', size).set('sort', 'createdAt,desc');
    return this.http.get<PageResponse<ActivityEntry>>(`${this.base}/activity`, { params });
  }
}
