import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '@core/auth/auth.service';
import { ApiError } from '@core/http/error.interceptor';

type LoadState = 'idle' | 'loading' | 'invalid' | 'ready';

@Component({
  selector: 'app-accept-invite-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, SlicePipe],
  templateUrl: './accept-invite.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AcceptInvitePage {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly state = signal<LoadState>('loading');
  protected readonly summary = signal<{ usesRemaining: number; expiresAt: string } | null>(null);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly token = computed(() => this.route.snapshot.paramMap.get('token') ?? '');

  protected readonly form = this.fb.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  constructor() {
    const token = this.token();
    if (!token) {
      this.state.set('invalid');
      return;
    }
    this.auth.validateInvite(token).subscribe({
      next: (s) => {
        this.summary.set(s);
        this.state.set('ready');
      },
      error: () => this.state.set('invalid'),
    });
  }

  submit(): void {
    if (this.form.invalid || this.loading()) return;

    this.loading.set(true);
    this.errorMessage.set(null);

    this.auth.acceptInvite(this.token(), this.form.getRawValue()).subscribe({
      next: () => void this.router.navigateByUrl('/dashboard'),
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(this.messageFor(err));
      },
    });
  }

  private messageFor(err: unknown): string {
    if (err instanceof ApiError) {
      if (err.status === 409) return 'Ese email o usuario ya están registrados.';
      if (err.status === 404) return 'La invitación ha dejado de ser válida.';
      if (err.status === 400 && Object.keys(err.fields).length > 0) {
        return Object.values(err.fields)[0] ?? 'Datos no válidos.';
      }
      return err.message;
    }
    return 'No se pudo crear la cuenta.';
  }
}
