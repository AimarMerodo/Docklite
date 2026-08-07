import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { InvitationDto, InvitationService } from '@core/api/invitation.service';
import { AuthService } from '@core/auth/auth.service';
import { ApiError } from '@core/http/error.interceptor';
import { ConfirmService } from '@core/ui/confirm.service';

type StatusFilter = 'all' | 'active' | 'inactive';

@Component({
  selector: 'app-admin-invitations-page',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './invitations.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminInvitationsPage implements OnInit {
  private readonly api = inject(InvitationService);
  private readonly confirm = inject(ConfirmService);
  private readonly auth = inject(AuthService);

  protected readonly isDemo = this.auth.isDemo;

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly invitations = signal<InvitationDto[]>([]);
  protected readonly statusFilter = signal<StatusFilter>('all');

  protected readonly createOpen = signal(false);
  protected readonly newMaxUses = signal<number | null>(1);
  protected readonly newExpiresInDays = signal<number | null>(7);
  protected readonly creating = signal(false);
  protected readonly createError = signal<string | null>(null);

  protected readonly filtered = computed<InvitationDto[]>(() => {
    const filter = this.statusFilter();
    const list = this.invitations();
    if (filter === 'all') return list;
    if (filter === 'active') return list.filter((i) => i.active);
    return list.filter((i) => !i.active);
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.list(0, 100).subscribe({
      next: (list) => {
        // Active first, then by createdAt desc.
        const sorted = [...list].sort((a, b) => {
          if (a.active !== b.active) return a.active ? -1 : 1;
          return b.createdAt.localeCompare(a.createdAt);
        });
        this.invitations.set(sorted);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          err instanceof ApiError ? err.message : 'No se pudieron cargar las invitaciones.',
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
      month: 'short',
      day: '2-digit',
    });
  }

  protected statusOf(inv: InvitationDto): { label: string; tone: string } {
    if (inv.active) return { label: 'activa', tone: 'success' };
    if (inv.cancelled) return { label: 'cancelada', tone: 'danger' };
    if (inv.expired) return { label: 'expirada', tone: 'muted' };
    if (inv.exhausted) return { label: 'agotada', tone: 'muted' };
    return { label: 'inactiva', tone: 'muted' };
  }

  protected openCreate(): void {
    this.createOpen.set(true);
    this.newMaxUses.set(1);
    this.newExpiresInDays.set(7);
    this.createError.set(null);
  }

  protected closeCreate(): void {
    this.createOpen.set(false);
    this.createError.set(null);
  }

  protected doCreate(): void {
    const maxUses = this.newMaxUses();
    const expiresInDays = this.newExpiresInDays();
    if (maxUses != null && maxUses < 1) {
      this.createError.set('"Usos máximos" debe ser al menos 1.');
      return;
    }
    if (expiresInDays != null && expiresInDays < 1) {
      this.createError.set('"Caducidad" debe ser al menos 1 día.');
      return;
    }
    this.creating.set(true);
    this.createError.set(null);
    this.api.create({
      maxUses: maxUses ?? undefined,
      expiresInDays: expiresInDays ?? undefined,
    }).subscribe({
      next: () => {
        this.creating.set(false);
        this.closeCreate();
        this.load();
      },
      error: (err: unknown) => {
        this.creating.set(false);
        this.createError.set(
          err instanceof ApiError ? err.message : 'No se pudo crear la invitación.',
        );
      },
    });
  }

  protected async copyUrl(inv: InvitationDto): Promise<void> {
    if (!inv.url) return; // masked for the demo role
    try {
      await navigator.clipboard.writeText(inv.url);
    } catch {
      // ignore
    }
  }

  protected async confirmCancel(inv: InvitationDto): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Cancelar invitación',
      message: `Quieres cancelar la invitación con token "${inv.token?.slice(0, 8) ?? '•••'}…"? Si alguien ya tiene el enlace, dejará de funcionar.`,
      variant: 'danger',
      confirmText: 'Cancelar invitación',
      cancelText: 'Volver',
    });
    if (!ok) return;
    this.api.cancel(inv.id).subscribe({
      next: () => this.load(),
      error: (err: unknown) => {
        this.errorMessage.set(
          err instanceof ApiError ? err.message : 'No se pudo cancelar la invitación.',
        );
      },
    });
  }
}
