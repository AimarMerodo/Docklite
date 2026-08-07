export type UserRole = 'USER' | 'ADMIN' | 'DEMO';

/** Response of GET /auth/demo — whether this deployment has demo mode on. */
export interface DemoStatusDto {
  enabled: boolean;
}

export interface AuthSession {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  username: string;
  role: UserRole;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponseDto {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  username: string;
  role: UserRole;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface AcceptInviteRequest {
  username: string;
  email: string;
  password: string;
}

export interface InvitationSummary {
  usesRemaining: number;
  expiresAt: string;
}

/**
 * Legacy DTO returned by `POST /invitations/{token}/accept` — older spec
 * still emits a single `token` instead of an access/refresh pair.
 */
export interface InvitationAcceptResponseDto {
  token?: string;
  accessToken?: string;
  refreshToken?: string;
  expiresInSeconds?: number;
  username: string;
  role: UserRole;
}
