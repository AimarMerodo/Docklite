import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { API_BASE_URL } from './api.config';
import { PageResponse } from './dashboard.service';

export interface UserDto {
  id: number;
  username: string;
  email: string;
  role: 'USER' | 'ADMIN' | string;
  enabled: boolean;
  createdAt: string;
}

export interface UpdateProfileRequest {
  currentPassword: string;
  newPassword: string;
}

export interface PasswordResetResponse {
  userId: number;
  username: string;
  temporaryPassword: string;
}

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  me(): Observable<UserDto> {
    return this.http.get<UserDto>(`${this.base}/users/me`);
  }

  updatePassword(payload: UpdateProfileRequest): Observable<UserDto> {
    return this.http.put<UserDto>(`${this.base}/users/me`, payload);
  }

  list(page = 0, size = 50): Observable<UserDto[]> {
    return this.http
      .get<PageResponse<UserDto>>(`${this.base}/users`, {
        params: { page: String(page), size: String(size) },
      })
      .pipe(map((r) => r.content ?? []));
  }

  resetPassword(id: number): Observable<PasswordResetResponse> {
    return this.http.post<PasswordResetResponse>(
      `${this.base}/admin/users/${id}/reset-password`,
      {},
    );
  }

  disable(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/users/${id}`);
  }

  enable(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/users/${id}/enable`, {});
  }
}
