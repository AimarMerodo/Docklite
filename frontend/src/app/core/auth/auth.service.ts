import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { EMPTY, firstValueFrom, from, Observable, tap } from 'rxjs';
import { map } from 'rxjs/operators';

import { API_BASE_URL } from '@core/api/api.config';
import {
  AcceptInviteRequest,
  AuthResponseDto,
  AuthSession,
  DemoStatusDto,
  InvitationAcceptResponseDto,
  InvitationSummary,
  LoginRequest,
  UserRole,
} from './auth.models';
import { TokenStorageService } from './token-storage.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly storage = inject(TokenStorageService);
  private readonly router = inject(Router);
  private readonly base = inject(API_BASE_URL);

  private readonly _session = signal<AuthSession | null>(null);
  readonly session = this._session.asReadonly();
  readonly isAuthenticated = computed(() => this._session() !== null);
  readonly username = computed(() => this._session()?.username ?? null);
  readonly role = computed<UserRole | null>(() => this._session()?.role ?? null);
  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  readonly isDemo = computed(() => this.role() === 'DEMO');

  /**
   * In-flight refresh shared between concurrent 401-driven retries.
   * Promise-based so multiple awaiters get the same eventual result without
   * re-firing the HTTP call (avoids rotating the refresh token twice).
   */
  private refreshPromise: Promise<AuthSession> | null = null;

  bootstrapFromStorage(): void {
    const stored = this.storage.read();
    if (stored) this._session.set(stored);
  }

  accessToken(): string | null {
    return this._session()?.accessToken ?? null;
  }

  login(payload: LoginRequest): Observable<AuthSession> {
    return this.http
      .post<AuthResponseDto>(`${this.base}/auth/login`, payload)
      .pipe(
        map((dto) => this.fromAuthDto(dto)),
        tap((session) => this.persist(session)),
      );
  }

  /** Whether this deployment has demo mode enabled (drives the login button). */
  demoStatus(): Observable<DemoStatusDto> {
    return this.http.get<DemoStatusDto>(`${this.base}/auth/demo`);
  }

  /** Passwordless login as the read-only demo user. */
  loginDemo(): Observable<AuthSession> {
    return this.http
      .post<AuthResponseDto>(`${this.base}/auth/demo`, {})
      .pipe(
        map((dto) => this.fromAuthDto(dto)),
        tap((session) => this.persist(session)),
      );
  }

  validateInvite(token: string): Observable<InvitationSummary> {
    return this.http.get<InvitationSummary>(`${this.base}/invitations/${token}`);
  }

  acceptInvite(token: string, payload: AcceptInviteRequest): Observable<AuthSession> {
    return this.http
      .post<InvitationAcceptResponseDto>(
        `${this.base}/invitations/${token}/accept`,
        payload,
      )
      .pipe(
        map((dto) => this.fromInviteDto(dto)),
        tap((session) => this.persist(session)),
      );
  }

  refresh(): Observable<AuthSession> {
    if (this.refreshPromise) return from(this.refreshPromise);

    const current = this._session();
    if (!current?.refreshToken) {
      this.logoutLocal();
      return EMPTY;
    }

    this.refreshPromise = firstValueFrom(
      this.http.post<AuthResponseDto>(`${this.base}/auth/refresh`, {
        refreshToken: current.refreshToken,
      }),
    )
      .then((dto) => {
        const session = this.fromAuthDto(dto);
        this.persist(session);
        return session;
      })
      .catch((err) => {
        this.logoutLocal();
        throw err;
      })
      .finally(() => {
        this.refreshPromise = null;
      });

    return from(this.refreshPromise);
  }

  async logout(): Promise<void> {
    const current = this._session();
    if (current?.refreshToken) {
      try {
        await firstValueFrom(
          this.http.post(`${this.base}/auth/logout`, {
            refreshToken: current.refreshToken,
          }),
        );
      } catch {
        // Logout endpoint failure shouldn't block local cleanup.
      }
    }
    this.logoutLocal();
  }

  logoutLocal(): void {
    this.storage.clear();
    this._session.set(null);
    void this.router.navigate(['/login']);
  }

  private persist(session: AuthSession): void {
    this._session.set(session);
    this.storage.write(session);
  }

  private fromAuthDto(dto: AuthResponseDto): AuthSession {
    return {
      accessToken: dto.accessToken,
      refreshToken: dto.refreshToken,
      expiresAt: Date.now() + dto.expiresInSeconds * 1000,
      username: dto.username,
      role: dto.role,
    };
  }

  private fromInviteDto(dto: InvitationAcceptResponseDto): AuthSession {
    const access = dto.accessToken ?? dto.token ?? '';
    const refresh = dto.refreshToken ?? '';
    const ttl = dto.expiresInSeconds ?? 900;
    return {
      accessToken: access,
      refreshToken: refresh,
      expiresAt: Date.now() + ttl * 1000,
      username: dto.username,
      role: dto.role,
    };
  }
}
