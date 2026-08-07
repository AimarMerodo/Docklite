import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { UserDto, UserService } from '@core/api/user.service';
import { AuthService } from '@core/auth/auth.service';
import { ApiError } from '@core/http/error.interceptor';

@Component({
  selector: 'app-me-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './me.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MePage implements OnInit {
  private readonly api = inject(UserService);
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly isDemo = this.auth.isDemo;

  protected readonly profile = signal<UserDto | null>(null);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly successMessage = signal<string | null>(null);

  protected readonly passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(6)]],
    confirmPassword: ['', Validators.required],
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.me().subscribe({
      next: (u) => {
        this.profile.set(u);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          err instanceof ApiError ? err.message : 'No se pudo cargar el perfil.',
        );
      },
    });
  }

  protected formatDate(iso: string): string {
    if (!iso) return '—';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    return d.toLocaleDateString('es-ES', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  }

  protected submitPassword(): void {
    if (this.passwordForm.invalid || this.saving()) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const { currentPassword, newPassword, confirmPassword } = this.passwordForm.getRawValue();
    if (newPassword !== confirmPassword) {
      this.errorMessage.set('La confirmación no coincide con la nueva contraseña.');
      return;
    }

    this.saving.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.api.updatePassword({ currentPassword, newPassword }).subscribe({
      next: () => {
        this.saving.set(false);
        this.successMessage.set('Contraseña actualizada correctamente.');
        this.passwordForm.reset({
          currentPassword: '',
          newPassword: '',
          confirmPassword: '',
        });
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.errorMessage.set(
          err instanceof ApiError ? err.message : 'No se pudo cambiar la contraseña.',
        );
      },
    });
  }
}
