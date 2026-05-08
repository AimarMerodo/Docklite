import { Injectable } from '@angular/core';

import { AuthSession } from './auth.models';

const STORAGE_KEY = 'docklite.session';

@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  read(): AuthSession | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw) as AuthSession;
      if (!parsed?.accessToken || !parsed?.refreshToken) return null;
      return parsed;
    } catch {
      return null;
    }
  }

  write(session: AuthSession): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }

  clear(): void {
    localStorage.removeItem(STORAGE_KEY);
  }
}
