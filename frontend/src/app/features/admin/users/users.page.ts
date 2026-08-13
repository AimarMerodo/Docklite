import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';

import { AuthService } from '@core/auth/auth.service';
import { PasswordResetResponse, UserDto, UserService } from '@core/api/user.service';
import { ApiError } from '@core/http/error.interceptor';
import { ConfirmService } from '@core/ui/confirm.service';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';

@Component({
  selector: 'app-admin-users-page',
  standalone: true,
  imports: [PageHeaderComponent],
  templateUrl: './users.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminUsersPage implements OnInit {
  protected readonly skeletonRows = [0, 1, 2, 3, 4];

  private readonly api = inject(UserService);
  private readonly auth = inject(AuthService);
  private readonly confirm = inject(ConfirmService);

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly users = signal<UserDto[]>([]);
  protected readonly search = signal('');

  protected readonly resetResult = signal<PasswordResetResponse | null>(null);

  protected readonly currentUsername = this.auth.username;
  protected readonly isDemo = this.auth.isDemo;

  protected readonly filtered = computed<UserDto[]>(() => {
    const q = this.search().trim().toLowerCase();
    const list = this.users();
    if (!q) return list;
    return list.filter(
      (u) =>
        u.username.toLowerCase().includes(q) ||
        u.email.toLowerCase().includes(q),
    );
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.list(0, 100).subscribe({
      next: (list) => {
        this.users.set(list);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          err instanceof ApiError ? err.message : 'No se pudieron cargar los usuarios.',
        );
      },
    });
  }

  protected onSearchInput(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected formatDate(iso: string): string {
    if (!iso) return '—';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    return d.toLocaleDateString('es-ES', {
      year: 'numeric',
      month: 'short',
      day: '2-digit',
    });
  }

  protected isSelf(u: UserDto): boolean {
    return u.username === this.currentUsername();
  }

  protected async confirmResetPassword(u: UserDto): Promise<void> {
    if (this.isSelf(u)) return;
    const ok = await this.confirm.ask({
      title: 'Resetear contraseña',
      message: `Generará una nueva contraseña temporal para "${u.username}". El usuario será deslogueado de todos sus dispositivos. Tendrás que comunicársela manualmente.`,
      variant: 'danger',
      confirmText: 'Resetear',
    });
    if (!ok) return;

    this.api.resetPassword(u.id).subscribe({
      next: (r) => this.resetResult.set(r),
      error: (err: unknown) => {
        this.errorMessage.set(
          err instanceof ApiError ? err.message : 'No se pudo resetear la contraseña.',
        );
      },
    });
  }

  protected closeResetResult(): void {
    this.resetResult.set(null);
  }

  protected async toggleEnabled(u: UserDto): Promise<void> {
    if (this.isSelf(u)) return;
    const action = u.enabled ? 'deshabilitar' : 'habilitar';
    const ok = await this.confirm.ask({
      title: u.enabled ? 'Deshabilitar usuario' : 'Habilitar usuario',
      message: u.enabled
        ? `Deshabilitar a "${u.username}" cerrará sus sesiones activas y no podrá volver a entrar hasta que lo habilites de nuevo.`
        : `Habilitar a "${u.username}" le permitirá volver a iniciar sesión.`,
      variant: u.enabled ? 'danger' : 'default',
      confirmText: u.enabled ? 'Deshabilitar' : 'Habilitar',
    });
    if (!ok) return;

    const op = u.enabled ? this.api.disable(u.id) : this.api.enable(u.id);
    op.subscribe({
      next: () => this.load(),
      error: (err: unknown) => {
        this.errorMessage.set(
          err instanceof ApiError ? err.message : `No se pudo ${action} el usuario.`,
        );
      },
    });
  }
}
