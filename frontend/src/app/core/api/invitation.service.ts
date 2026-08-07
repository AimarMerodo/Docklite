import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { API_BASE_URL } from './api.config';
import { PageResponse } from './dashboard.service';

export interface InvitationDto {
  id: number;
  /** Null for the DEMO role — the backend masks the secret token/URL. */
  token: string | null;
  url: string | null;
  maxUses: number;
  usesRemaining: number;
  expiresAt: string;
  cancelled: boolean;
  expired: boolean;
  exhausted: boolean;
  active: boolean;
  createdBy: number;
  createdAt: string;
}

export interface CreateInvitationRequest {
  maxUses?: number;
  expiresInDays?: number;
}

@Injectable({ providedIn: 'root' })
export class InvitationService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  list(page = 0, size = 50): Observable<InvitationDto[]> {
    return this.http
      .get<PageResponse<InvitationDto>>(`${this.base}/admin/invitations`, {
        params: { page: String(page), size: String(size) },
      })
      .pipe(map((r) => r.content ?? []));
  }

  create(payload: CreateInvitationRequest): Observable<InvitationDto> {
    return this.http.post<InvitationDto>(`${this.base}/admin/invitations`, payload);
  }

  cancel(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/invitations/${id}`);
  }
}
