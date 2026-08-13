import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { NetworkService, NetworkSummary } from '@core/api/network.service';
import { AuthService } from '@core/auth/auth.service';
import { humanizeDeleteError } from '@core/http/error-messages';
import { ApiError } from '@core/http/error.interceptor';
import { ConfirmService } from '@core/ui/confirm.service';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';

@Component({
  selector: 'app-networks-page',
  standalone: true,
  imports: [FormsModule, PageHeaderComponent],
  templateUrl: './networks.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NetworksPage implements OnInit {
  protected readonly skeletonRows = [0, 1, 2];

  private readonly api = inject(NetworkService);
  private readonly confirm = inject(ConfirmService);
  private readonly auth = inject(AuthService);

  protected readonly isDemo = this.auth.isDemo;

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly networks = signal<NetworkSummary[]>([]);
  protected readonly search = signal('');

  protected readonly createOpen = signal(false);
  protected readonly newNetworkName = signal('');
  protected readonly newNetworkDriver = signal('bridge');
  protected readonly creating = signal(false);
  protected readonly createError = signal<string | null>(null);

  protected readonly filtered = computed<NetworkSummary[]>(() => {
    const q = this.search().trim().toLowerCase();
    const list = this.networks();
    if (!q) return list;
    return list.filter(
      (n) => n.name.toLowerCase().includes(q) || n.driver.toLowerCase().includes(q),
    );
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.list().subscribe({
      next: (list) => {
        // Builtins (bridge/host/none) first, then alphabetical.
        const sorted = [...list].sort((a, b) => {
          if (!!a.builtin !== !!b.builtin) return a.builtin ? -1 : 1;
          return a.name.localeCompare(b.name);
        });
        this.networks.set(sorted);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          err instanceof ApiError ? err.message : 'No se pudieron cargar las redes.',
        );
      },
    });
  }

  protected onSearchInput(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected openCreate(): void {
    this.createOpen.set(true);
    this.newNetworkName.set('');
    this.newNetworkDriver.set('bridge');
    this.createError.set(null);
  }

  protected closeCreate(): void {
    this.createOpen.set(false);
    this.newNetworkName.set('');
    this.createError.set(null);
  }

  protected doCreate(): void {
    const name = this.newNetworkName().trim();
    if (!name) {
      this.createError.set('Indica un nombre para la red.');
      return;
    }
    this.creating.set(true);
    this.createError.set(null);
    this.api.create(name, this.newNetworkDriver()).subscribe({
      next: () => {
        this.creating.set(false);
        this.closeCreate();
        this.load();
      },
      error: (err: unknown) => {
        this.creating.set(false);
        this.createError.set(
          err instanceof ApiError ? err.message : 'No se pudo crear la red.',
        );
      },
    });
  }

  protected async confirmRemove(net: NetworkSummary): Promise<void> {
    if (net.builtin) return;
    const ok = await this.confirm.ask({
      title: 'Eliminar red',
      message: `¿Seguro que quieres eliminar la red "${net.name}"? Si tiene contenedores conectados, Docker rechazará la operación.`,
      variant: 'danger',
      confirmText: 'Eliminar',
    });
    if (!ok) return;
    this.api.remove(net.id).subscribe({
      next: () => this.load(),
      error: (err: unknown) => {
        void this.confirm.ask({
          title: 'No se pudo eliminar la red',
          message: humanizeDeleteError(err, 'red'),
          variant: 'danger',
          hideCancel: true,
          confirmText: 'Entendido',
        });
      },
    });
  }
}
