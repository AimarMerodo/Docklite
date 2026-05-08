import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';

import { ContainerService, ContainerSummary } from '@core/api/container.service';
import { ApiError } from '@core/http/error.interceptor';
import { ContainerRowComponent, RowAction } from '@features/containers/components/container-row/container-row.component';
import { CreateContainerWizard } from '@features/containers/components/create-wizard/create-wizard.component';
import { StatCardComponent } from '@features/containers/components/stat-card/stat-card.component';

interface ContainerStats {
  total: number;
  running: number;
  stopped: number;
  unhealthy: number;
}

type SortKey = 'name' | 'status' | 'image' | 'uptime';

interface StatusOption {
  state: string;
  label: string;
}

const FILTERABLE_STATES: StatusOption[] = [
  { state: 'running', label: 'Running' },
  { state: 'exited', label: 'Stopped' },
  { state: 'paused', label: 'Paused' },
  { state: 'restarting', label: 'Restarting' },
  { state: 'created', label: 'Created' },
  { state: 'dead', label: 'Dead' },
];

@Component({
  selector: 'app-containers-page',
  standalone: true,
  imports: [StatCardComponent, ContainerRowComponent, CreateContainerWizard],
  templateUrl: './containers.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainersPage implements OnInit {
  private readonly api = inject(ContainerService);

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly containers = signal<ContainerSummary[]>([]);
  protected readonly search = signal('');
  protected readonly wizardOpen = signal(false);
  protected readonly sortKey = signal<SortKey>('name');
  protected readonly sortOpen = signal(false);
  protected readonly statusFilter = signal<Set<string>>(
    new Set(FILTERABLE_STATES.map((s) => s.state)),
  );
  protected readonly filterOpen = signal(false);
  protected readonly statusOptions = FILTERABLE_STATES;
  protected readonly sortOptions: { key: SortKey; label: string }[] = [
    { key: 'name', label: 'Nombre' },
    { key: 'status', label: 'Estado' },
    { key: 'image', label: 'Imagen' },
    { key: 'uptime', label: 'Uptime' },
  ];
  /** id → action currently in flight, used to disable per-row buttons. */
  protected readonly pending = signal<Record<string, RowAction | undefined>>({});

  readonly stats = computed<ContainerStats>(() => {
    const list = this.containers();
    return {
      total: list.length,
      running: list.filter((c) => c.state === 'running').length,
      stopped: list.filter((c) => c.state === 'exited' || c.state === 'created').length,
      unhealthy: list.filter((c) => /\(unhealthy\)/i.test(c.status)).length,
    };
  });

  readonly filtered = computed<ContainerSummary[]>(() => {
    const q = this.search().trim().toLowerCase();
    const allowed = this.statusFilter();
    const matchesSearch = (c: ContainerSummary) =>
      !q ||
      c.name.toLowerCase().includes(q) ||
      c.image.toLowerCase().includes(q) ||
      c.id.toLowerCase().startsWith(q);
    const matchesStatus = (c: ContainerSummary) => allowed.has(c.state);
    return this.containers()
      .filter((c) => matchesSearch(c) && matchesStatus(c))
      .sort(comparator(this.sortKey()));
  });

  readonly sortLabel = computed(() => `Ordenar: ${SORT_LABELS[this.sortKey()]}`);

  readonly hiddenStatusCount = computed(
    () => FILTERABLE_STATES.length - this.statusFilter().size,
  );

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.list({ all: true }).subscribe({
      next: (cs) => {
        this.containers.set(cs);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          err instanceof ApiError ? err.message : 'No se pudieron cargar los contenedores.',
        );
      },
    });
  }

  protected openWizard(): void {
    this.wizardOpen.set(true);
  }

  protected closeWizard(): void {
    this.wizardOpen.set(false);
  }

  protected onContainerCreated(): void {
    this.wizardOpen.set(false);
    this.reload();
  }

  protected onSearch(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.search.set(value);
  }

  protected toggleFilter(): void {
    this.filterOpen.update((v) => !v);
  }

  protected closeFilter(): void {
    this.filterOpen.set(false);
  }

  protected toggleStatus(state: string): void {
    this.statusFilter.update((set) => {
      const next = new Set(set);
      if (next.has(state)) next.delete(state);
      else next.add(state);
      return next;
    });
  }

  protected isStatusActive(state: string): boolean {
    return this.statusFilter().has(state);
  }

  protected resetStatusFilter(): void {
    this.statusFilter.set(new Set(FILTERABLE_STATES.map((s) => s.state)));
  }

  protected toggleSort(): void {
    this.sortOpen.update((v) => !v);
  }

  protected closeSort(): void {
    this.sortOpen.set(false);
  }

  protected setSort(key: SortKey): void {
    this.sortKey.set(key);
    this.sortOpen.set(false);
  }

  protected onAction(container: ContainerSummary, action: RowAction): void {
    if (action === 'terminal') return; // backend endpoint pending
    this.runAction(container, action);
  }

  private runAction(container: ContainerSummary, action: RowAction): void {
    this.markPending(container.id, action);

    const stream =
      action === 'start'
        ? this.api.start(container.id)
        : action === 'stop'
          ? this.api.stop(container.id)
          : this.api.restart(container.id);

    stream.subscribe({
      next: () => {
        this.clearPending(container.id);
        this.reload();
      },
      error: (err: unknown) => {
        this.clearPending(container.id);
        this.errorMessage.set(
          err instanceof ApiError
            ? err.message
            : `No se pudo ejecutar la acción "${action}".`,
        );
      },
    });
  }

  private markPending(id: string, action: RowAction): void {
    this.pending.update((p) => ({ ...p, [id]: action }));
  }

  private clearPending(id: string): void {
    this.pending.update((p) => {
      const next = { ...p };
      delete next[id];
      return next;
    });
  }
}

const SORT_LABELS: Record<SortKey, string> = {
  name: 'Nombre',
  status: 'Estado',
  image: 'Imagen',
  uptime: 'Uptime',
};

function comparator(key: SortKey) {
  return (a: ContainerSummary, b: ContainerSummary): number => {
    switch (key) {
      case 'status':
        return statePriority(a.state) - statePriority(b.state) || a.name.localeCompare(b.name);
      case 'image':
        return a.image.localeCompare(b.image) || a.name.localeCompare(b.name);
      case 'uptime':
        return statePriority(a.state) - statePriority(b.state) || a.status.localeCompare(b.status);
      case 'name':
      default:
        return a.name.localeCompare(b.name);
    }
  };
}

function statePriority(state: string): number {
  switch (state) {
    case 'running':
      return 0;
    case 'restarting':
      return 1;
    case 'paused':
      return 2;
    case 'created':
      return 3;
    default:
      return 4;
  }
}
