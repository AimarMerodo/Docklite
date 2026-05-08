import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { API_BASE_URL } from './api.config';
import { PageResponse } from './dashboard.service';

export interface ImageSummary {
  id: string;
  tags: string[];
  size: number;
  /** Unix epoch seconds, as string (raw Docker payload). */
  created: string;
  /** Usernames of users who pulled this image. Admins see all owners;
   *  regular users only see themselves. May be empty for images pulled
   *  outside the app (e.g. via the daemon directly). */
  owners?: string[];
  /** Names of containers (running or stopped) referencing this image. */
  usedBy?: string[];
}

export interface ImageExposedPort {
  port: number;
  protocol: 'tcp' | 'udp' | string;
}

export interface ImageDetail extends ImageSummary {
  exposedPorts: ImageExposedPort[];
}

@Injectable({ providedIn: 'root' })
export class ImageService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  list(): Observable<ImageSummary[]> {
    return this.http
      .get<PageResponse<ImageSummary> | ImageSummary[]>(`${this.base}/images`)
      .pipe(map((data) => (Array.isArray(data) ? data : (data?.content ?? []))));
  }

  get(id: string): Observable<ImageDetail> {
    return this.http.get<ImageDetail>(`${this.base}/images/${id}`);
  }

  /**
   * Fetches just the metadata of a remote image (registry manifest),
   * without pulling its layers. Used as a fallback when the image isn't
   * present locally yet — e.g. to drive the wizard's port autocomplete.
   */
  inspectRemote(ref: string): Observable<{ ref: string; exposedPorts: ImageExposedPort[] }> {
    const params = new HttpParams().set('ref', ref);
    return this.http.get<{ ref: string; exposedPorts: ImageExposedPort[] }>(
      `${this.base}/images/inspect-remote`,
      { params },
    );
  }

  pull(image: string, tag?: string): Observable<ImageSummary> {
    return this.http.post<ImageSummary>(`${this.base}/images/pull`, {
      image,
      tag: tag || undefined,
    });
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/images/${id}`);
  }

  search(query: string, limit = 25): Observable<DockerHubSearchResult[]> {
    const params = new HttpParams().set('q', query).set('limit', String(limit));
    return this.http.get<DockerHubSearchResult[]>(`${this.base}/images/search`, { params });
  }
}

export interface DockerHubSearchResult {
  name: string;
  description: string;
  stars: number;
  official: boolean;
}
