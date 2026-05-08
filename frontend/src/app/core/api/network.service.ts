import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { API_BASE_URL } from './api.config';
import { PageResponse } from './dashboard.service';

export interface NetworkSummary {
  id: string;
  name: string;
  driver: string;
  /** Default bridge/host/none networks are visible to everyone. */
  builtin?: boolean;
  /** Containers currently connected to this network. */
  connectedContainers?: ConnectedContainer[];
}

export interface ConnectedContainer {
  id: string;
  name: string;
}

export interface NetworkDetail {
  id: string;
  name: string;
  driver: string;
  scope: string;
  connectedContainers: ConnectedContainer[];
}

@Injectable({ providedIn: 'root' })
export class NetworkService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  list(): Observable<NetworkSummary[]> {
    return this.http
      .get<PageResponse<NetworkSummary> | NetworkSummary[]>(`${this.base}/networks`)
      .pipe(map((data) => (Array.isArray(data) ? data : (data?.content ?? []))));
  }

  get(id: string): Observable<NetworkDetail> {
    return this.http.get<NetworkDetail>(`${this.base}/networks/${id}`);
  }

  create(name: string, driver?: string): Observable<NetworkSummary> {
    return this.http.post<NetworkSummary>(`${this.base}/networks`, {
      name,
      driver: driver ?? 'bridge',
    });
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/networks/${id}`);
  }

  connect(networkId: string, containerId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/networks/${networkId}/connect/${containerId}`,
      {},
    );
  }

  disconnect(networkId: string, containerId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/networks/${networkId}/disconnect/${containerId}`,
      {},
    );
  }
}
