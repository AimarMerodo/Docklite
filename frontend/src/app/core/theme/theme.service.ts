import { Injectable, signal } from '@angular/core';

export type ThemeMode = 'dark' | 'light';

const STORAGE_KEY = 'docklite.theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly _mode = signal<ThemeMode>('dark');
  readonly mode = this._mode.asReadonly();

  init(): void {
    const stored = (localStorage.getItem(STORAGE_KEY) as ThemeMode | null) ?? 'dark';
    this.set(stored);
  }

  set(mode: ThemeMode): void {
    this._mode.set(mode);
    document.documentElement.classList.toggle('light', mode === 'light');
    localStorage.setItem(STORAGE_KEY, mode);
  }

  toggle(): void {
    this.set(this._mode() === 'dark' ? 'light' : 'dark');
  }
}
