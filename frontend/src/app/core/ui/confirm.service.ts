import { Injectable, signal } from '@angular/core';

export type ConfirmVariant = 'default' | 'danger' | 'soft-danger';
export type ConfirmChoice = 'primary' | 'secondary' | 'cancel';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  variant?: ConfirmVariant;
  /** When set, a third button is rendered between cancel and primary. */
  secondaryConfirmText?: string;
  secondaryVariant?: ConfirmVariant;
  /** Hide the cancel button — turns the dialog into a single-button alert. */
  hideCancel?: boolean;
}

interface ActiveConfirm {
  options: ConfirmOptions;
  resolve: (choice: ConfirmChoice) => void;
}

@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly _state = signal<ActiveConfirm | null>(null);
  readonly state = this._state.asReadonly();

  /** Boolean confirm. true = primary, false = cancel. Secondary becomes false. */
  ask(options: ConfirmOptions): Promise<boolean> {
    return this.askChoice(options).then((c) => c === 'primary');
  }

  /** Tri-state confirm exposing the secondary action. */
  askChoice(options: ConfirmOptions): Promise<ConfirmChoice> {
    return new Promise((resolve) => {
      this._state.set({ options, resolve });
    });
  }

  accept(): void {
    const s = this._state();
    if (!s) return;
    s.resolve('primary');
    this._state.set(null);
  }

  acceptSecondary(): void {
    const s = this._state();
    if (!s) return;
    s.resolve('secondary');
    this._state.set(null);
  }

  cancel(): void {
    const s = this._state();
    if (!s) return;
    s.resolve('cancel');
    this._state.set(null);
  }
}
