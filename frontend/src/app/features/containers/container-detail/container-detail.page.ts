import { ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, OnDestroy, OnInit, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { Observable } from 'rxjs';

import {
  ContainerDetail,
  ContainerMount,
  ContainerPort,
  ContainerService,
  ContainerStats,
  EnvVarInput,
  HealthcheckInput,
  PortInUse,
  PortMappingInput,
  PortProtocol,
  RestartPolicy,
  VolumeMountInput,
} from '@core/api/container.service';
import { NetworkService, NetworkSummary } from '@core/api/network.service';
import { VolumeService, VolumeSummary } from '@core/api/volume.service';
import { ApiError } from '@core/http/error.interceptor';
import { ConfirmService } from '@core/ui/confirm.service';
import { StatusPillComponent } from '@shared/ui/status-pill/status-pill.component';
import { InfoCellComponent } from '@features/containers/components/info-cell/info-cell.component';

type Tab = 'general' | 'terminal' | 'volumes' | 'network' | 'settings' | 'logs';
type RowAction = 'start' | 'stop' | 'restart';

const STATS_POLL_INTERVAL_MS = 2000;
const STATS_HISTORY_LENGTH = 30;
const SPARKLINE_WIDTH = 60;
const SPARKLINE_HEIGHT = 18;

function trimHistory(arr: number[]): number[] {
  return arr.length > STATS_HISTORY_LENGTH ? arr.slice(-STATS_HISTORY_LENGTH) : arr;
}

@Component({
  selector: 'app-container-detail-page',
  standalone: true,
  imports: [RouterLink, FormsModule, StatusPillComponent, InfoCellComponent],
  templateUrl: './container-detail.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainerDetailPage implements OnInit, OnDestroy {
  private readonly api = inject(ContainerService);
  private readonly titleService = inject(Title);
  private readonly volumeApi = inject(VolumeService);
  private readonly networkApi = inject(NetworkService);
  private readonly confirm = inject(ConfirmService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly tabs: { id: Tab; label: string }[] = [
    { id: 'general', label: 'General' },
    { id: 'terminal', label: 'Terminal' },
    { id: 'volumes', label: 'Volúmenes' },
    { id: 'network', label: 'Red' },
    { id: 'logs', label: 'Logs' },
    { id: 'settings', label: 'Settings' },
  ];

  protected readonly tab = signal<Tab>('general');
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly container = signal<ContainerDetail | null>(null);
  protected readonly actionPending = signal(false);

  protected readonly logs = signal<string | null>(null);
  protected readonly logsLoading = signal(false);
  protected readonly tail = signal(50);
  protected readonly logsCopied = signal(false);

  // Live stats (polled while the container is running). History is the
  // tail of the last N samples, used to draw the header sparklines.
  protected readonly currentStats = signal<ContainerStats | null>(null);
  protected readonly cpuHistory = signal<number[]>([]);
  protected readonly memHistory = signal<number[]>([]);
  private statsTimer: ReturnType<typeof setInterval> | null = null;

  // Mount editor state.
  protected readonly mountFormOpen = signal(false);
  protected readonly mountSaving = signal(false);
  protected readonly mountError = signal<string | null>(null);
  protected readonly availableVolumes = signal<VolumeSummary[]>([]);
  protected readonly newMountVolume = signal('');
  protected readonly newMountPath = signal('');
  protected readonly newMountReadOnly = signal(false);

  // Inline edit state (for editing an existing mount).
  protected readonly editingMountIndex = signal<number | null>(null);
  protected readonly editMountPath = signal('');
  protected readonly editMountReadOnly = signal(false);

  // Network editor state (live operations — no recreate required).
  protected readonly networkFormOpen = signal(false);
  protected readonly networkSaving = signal(false);
  protected readonly networkError = signal<string | null>(null);
  protected readonly availableNetworks = signal<NetworkSummary[]>([]);
  protected readonly newNetworkId = signal('');

  // Inline network creation.
  protected readonly creatingNetwork = signal(false);
  protected readonly newNetworkName = signal('');
  protected readonly networkCreateError = signal<string | null>(null);
  protected readonly availableNetworksToConnect = computed<NetworkSummary[]>(() => {
    const c = this.container();
    if (!c) return [];
    const connected = new Set(c.networks.map((n) => n.name));
    return this.availableNetworks().filter((n) => !connected.has(n.name));
  });

  // Inline volume creation.
  protected readonly creatingVolume = signal(false);
  protected readonly newVolumeName = signal('');
  protected readonly volumeCreateError = signal<string | null>(null);

  // Port editor state (recreate-style — stop required).
  protected readonly portFormOpen = signal(false);
  protected readonly portSaving = signal(false);
  protected readonly portError = signal<string | null>(null);
  protected readonly newPortHost = signal<number | null>(null);
  protected readonly newPortContainer = signal<number | null>(null);
  protected readonly newPortProtocol = signal<PortProtocol>('tcp');
  /** When true, the container port + protocol come from free inputs instead of the detected list. */
  protected readonly useCustomContainerPort = signal(false);

  /** Distinct container ports detected from the image (EXPOSE + already published). */
  protected readonly detectedContainerPorts = computed<
    { port: number; protocol: PortProtocol; label: string }[]
  >(() => {
    const c = this.container();
    if (!c) return [];
    const seen = new Set<string>();
    const out: { port: number; protocol: PortProtocol; label: string }[] = [];
    for (const p of c.ports) {
      const proto = (p.protocol || 'tcp') as PortProtocol;
      const key = `${p.privatePort}/${proto}`;
      if (seen.has(key)) continue;
      seen.add(key);
      out.push({ port: p.privatePort, protocol: proto, label: key });
    }
    return out.sort((a, b) => a.port - b.port);
  });

  // Inline edit state for an existing port mapping.
  protected readonly editingPortKey = signal<string | null>(null);
  protected readonly editPortHost = signal<number | null>(null);
  protected readonly editPortProtocol = signal<PortProtocol>('tcp');

  /** Host ports bound by *other* containers — used to flag conflicts. */
  protected readonly portsInUse = signal<PortInUse[]>([]);

  protected readonly canEditMounts = computed(() => {
    const c = this.container();
    return !!c && !c.state.running;
  });

  protected readonly mountsBlockedReason = computed<string | null>(() => {
    const c = this.container();
    if (!c) return null;
    if (c.state.running)
      return 'Detén el contenedor para añadir, editar o quitar volúmenes.';
    return null;
  });

  protected readonly addMountTooltip = computed<string>(() => {
    const c = this.container();
    if (!c) return '';
    if (c.state.running) return 'Detén el contenedor primero';
    return 'Montar un volumen nuevo';
  });

  protected readonly canEditPorts = computed(() => {
    const c = this.container();
    return !!c && !c.state.running;
  });

  protected readonly portsBlockedReason = computed<string | null>(() => {
    const c = this.container();
    if (!c) return null;
    if (c.state.running)
      return 'Detén el contenedor para añadir o quitar puertos.';
    return null;
  });

  protected readonly addPortTooltip = computed<string>(() => {
    const c = this.container();
    if (!c) return '';
    if (c.state.running) return 'Detén el contenedor primero';
    return 'Publicar un puerto nuevo';
  });

  /** Container name blocking the new-port form's hostPort, if any. */
  protected readonly newPortConflict = computed<string | null>(() =>
    this.findConflict(this.newPortHost(), this.newPortProtocol()),
  );

  /** Container name blocking the inline-edit row's hostPort, if any. */
  protected readonly editPortConflict = computed<string | null>(() =>
    this.findConflict(this.editPortHost(), this.editPortProtocol()),
  );

  private findConflict(host: number | null, proto: string): string | null {
    if (!host) return null;
    const c = this.container();
    const ownName = c?.name;
    const match = this.portsInUse().find(
      (p) =>
        p.hostPort === host &&
        p.protocol === proto &&
        p.containerName !== ownName,
    );
    return match ? match.containerName : null;
  }

  protected readonly primaryIp = computed<string>(() => {
    const c = this.container();
    if (!c || c.networks.length === 0) return '—';
    const withIp = c.networks.find((n) => n.ipAddress);
    return withIp?.ipAddress || '—';
  });

  protected readonly primaryNetwork = computed<string>(() => {
    const c = this.container();
    if (!c) return '—';
    if (c.networks.length === 1) return c.networks[0].name;
    if (c.networks.length === 0) return c.hostConfig.networkMode || '—';
    return `${c.networks[0].name} (+${c.networks.length - 1})`;
  });

  protected readonly commandLine = computed<string>(() => {
    const c = this.container();
    if (!c) return '';
    const parts = [...c.config.entrypoint, ...c.config.command];
    return parts.join(' ');
  });

  protected readonly uniquePorts = computed<ContainerPort[]>(() => {
    const c = this.container();
    if (!c) return [];
    const seen = new Set<string>();
    return c.ports.filter((p) => {
      const key = `${p.privatePort}-${p.publicPort ?? ''}-${p.protocol}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  });

  constructor() {
    // Lazy-load logs the first time the user enters the Logs tab.
    effect(() => {
      if (this.tab() === 'logs' && this.container() && this.logs() === null) {
        this.reloadLogs();
      }
    });

    // Always pin the logs view to the bottom when entering the tab or after
    // a refresh — users want to see the newest lines first.
    effect(() => {
      if (this.tab() !== 'logs') return;
      if (this.logs() === null) return;
      // Defer past Angular's render so the @if branch and #logsPane are in
      // the DOM before we measure scrollHeight.
      setTimeout(() => {
        const el = this.logsPane()?.nativeElement;
        if (el) el.scrollTop = el.scrollHeight;
      }, 0);
    });

    // Keep the URL in sync with the active tab so it survives refresh.
    effect(() => {
      const t = this.tab();
      const current = this.route.snapshot.queryParamMap.get('tab');
      if (t === current) return;
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { tab: t === 'general' ? null : t },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    });
  }

  ngOnInit(): void {
    // Restore active tab from the URL query (`?tab=volumes`).
    const queryTab = this.route.snapshot.queryParamMap.get('tab') as Tab | null;
    if (queryTab && this.tabs.some((t) => t.id === queryTab)) {
      this.tab.set(queryTab);
    }

    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errorMessage.set('Falta el id del contenedor.');
      this.loading.set(false);
      return;
    }
    this.load(id);
  }

  reload(): void {
    const c = this.container();
    if (c) this.load(c.id);
  }

  private load(id: string): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.get(id).subscribe({
      next: (c) => {
        this.container.set(c);
        this.loading.set(false);
        this.titleService.setTitle(`DockLite | ${c.name}`);
        this.refreshPortsInUse();
        this.syncStatsPolling(c);
        if (this.tab() === 'logs') this.reloadLogs();
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          err instanceof ApiError ? err.message : 'No se pudo cargar el contenedor.',
        );
      },
    });
  }

  /**
   * Starts/stops the stats polling loop based on the container state.
   * We don't poll stopped containers (the daemon would just return
   * zeros), and we discard the history on stop so the next start gets a
   * fresh chart.
   */
  private syncStatsPolling(c: ContainerDetail): void {
    if (c.state.running) {
      if (this.statsTimer === null) {
        this.fetchStatsOnce(c.id);
        this.statsTimer = setInterval(() => this.fetchStatsOnce(c.id), STATS_POLL_INTERVAL_MS);
      }
    } else {
      this.stopStatsPolling();
      this.currentStats.set(null);
      this.cpuHistory.set([]);
      this.memHistory.set([]);
    }
  }

  private stopStatsPolling(): void {
    if (this.statsTimer !== null) {
      clearInterval(this.statsTimer);
      this.statsTimer = null;
    }
  }

  private fetchStatsOnce(id: string): void {
    this.api.stats(id).subscribe({
      next: (s) => {
        this.currentStats.set(s);
        this.cpuHistory.update((arr) => trimHistory([...arr, s.cpuPercent ?? 0]));
        this.memHistory.update((arr) => trimHistory([...arr, s.memoryPercent ?? 0]));
      },
      error: () => {
        // Silently ignore — stats might fail mid-stop, we'll resume next tick.
      },
    });
  }

  ngOnDestroy(): void {
    this.stopStatsPolling();
  }

  /** Builds the SVG `points="x,y x,y …"` string for a sparkline polyline. */
  protected sparkPoints(history: number[]): string {
    if (history.length === 0) return '';
    const n = Math.max(history.length, 2);
    const max = 100; // values are percentages, fixed scale.
    return history
      .map((v, i) => {
        const x = (i / (n - 1)) * SPARKLINE_WIDTH;
        const y = SPARKLINE_HEIGHT - (Math.min(v, max) / max) * SPARKLINE_HEIGHT;
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');
  }

  protected readonly sparkSize = { w: SPARKLINE_WIDTH, h: SPARKLINE_HEIGHT };

  private refreshPortsInUse(): void {
    this.api.portsInUse().subscribe({ next: (p) => this.portsInUse.set(p) });
  }

  private readonly logsPane = viewChild<ElementRef<HTMLPreElement>>('logsPane');

  protected reloadLogs(): void {
    const c = this.container();
    if (!c) return;
    this.logsLoading.set(true);
    this.api.logs(c.id, this.tail()).subscribe({
      next: (text) => {
        this.logs.set(text || '');
        this.logsLoading.set(false);
        // The auto-scroll is handled by the effect that watches tab + logs,
        // so we don't need to schedule it manually here.
      },
      error: (err: unknown) => {
        this.logsLoading.set(false);
        this.errorMessage.set(
          err instanceof ApiError ? err.message : 'No se pudieron cargar los logs.',
        );
      },
    });
  }

  protected async copyLogs(): Promise<void> {
    const text = this.logs() ?? '';
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      this.logsCopied.set(true);
      setTimeout(() => this.logsCopied.set(false), 1500);
    } catch {
      // Clipboard API rejected — fall back silently.
    }
  }

  protected onTailChange(event: Event): void {
    const value = parseInt((event.target as HTMLSelectElement).value, 10);
    this.tail.set(value);
    this.reloadLogs();
  }

  protected action(kind: RowAction): void {
    const c = this.container();
    if (!c || this.actionPending()) return;
    this.actionPending.set(true);

    const stream: Observable<void> =
      kind === 'start'
        ? this.api.start(c.id)
        : kind === 'stop'
          ? this.api.stop(c.id)
          : this.api.restart(c.id);

    stream.subscribe({
      next: () => {
        this.actionPending.set(false);
        this.refreshDetail(c.id);
      },
      error: (err: unknown) => {
        this.actionPending.set(false);
        this.errorMessage.set(
          err instanceof ApiError ? err.message : `No se pudo ejecutar la acción "${kind}".`,
        );
      },
    });
  }

  /** Silent refresh — keeps the layout visible while updating the signal. */
  private refreshDetail(id: string): void {
    this.api.get(id).subscribe({
      next: (c) => {
        this.container.set(c);
        this.titleService.setTitle(`DockLite | ${c.name}`);
        this.syncStatsPolling(c);
      },
    });
  }

  protected async confirmDelete(): Promise<void> {
    const c = this.container();
    if (!c) return;
    const ok = await this.confirm.ask({
      title: 'Eliminar contenedor',
      message: `¿Seguro que quieres eliminar definitivamente el contenedor "${c.name}"?\nEsta acción no se puede deshacer.`,
      variant: 'danger',
      confirmText: 'Eliminar',
    });
    if (!ok) return;

    this.actionPending.set(true);
    this.api.remove(c.id).subscribe({
      next: () => {
        this.actionPending.set(false);
        void this.router.navigate(['/containers']);
      },
      error: (err: unknown) => {
        this.actionPending.set(false);
        this.errorMessage.set(
          err instanceof ApiError ? err.message : 'No se pudo eliminar el contenedor.',
        );
      },
    });
  }

  protected openMountForm(): void {
    if (!this.canEditMounts()) return;
    this.mountFormOpen.set(true);
    this.mountError.set(null);
    this.newMountVolume.set('');
    this.newMountPath.set('');
    this.newMountReadOnly.set(false);
    this.volumeApi.list().subscribe({
      next: (vs) => this.availableVolumes.set(vs),
    });
  }

  protected closeMountForm(): void {
    this.mountFormOpen.set(false);
    this.creatingVolume.set(false);
    this.newVolumeName.set('');
    this.volumeCreateError.set(null);
  }

  protected toggleVolumeCreator(): void {
    this.creatingVolume.update((v) => !v);
    this.newVolumeName.set('');
    this.volumeCreateError.set(null);
  }

  protected createVolume(): void {
    const name = this.newVolumeName().trim();
    if (!name) return;
    this.volumeCreateError.set(null);
    this.volumeApi.create(name).subscribe({
      next: (vol) => {
        this.availableVolumes.update((list) => [...list, vol]);
        this.newMountVolume.set(vol.name);
        this.creatingVolume.set(false);
        this.newVolumeName.set('');
      },
      error: (err: unknown) => {
        this.volumeCreateError.set(
          err instanceof ApiError ? err.message : 'No se pudo crear el volumen.',
        );
      },
    });
  }

  protected async addMount(): Promise<void> {
    const c = this.container();
    if (!c || !this.canEditMounts()) return;
    const name = this.newMountVolume().trim();
    const path = this.newMountPath().trim();
    if (!name || !path) {
      this.mountError.set('Selecciona un volumen y escribe la ruta.');
      return;
    }

    const ok = await this.confirm.ask({
      title: 'Añadir volumen',
      message:
        'Añadir un volumen recreará el contenedor. La configuración (env, puertos, red) y los datos de los volúmenes existentes se conservan.',
      confirmText: 'Añadir y recrear',
    });
    if (!ok) return;

    const body: VolumeMountInput[] = [
      ...this.currentVolumeMounts(),
      { volumeName: name, containerPath: path, readOnly: this.newMountReadOnly() },
    ];
    this.applyMounts(c.id, body);
  }

  protected startEditMount(index: number, mount: ContainerMount): void {
    if (!this.canEditMounts() || mount.type !== 'volume') return;
    this.editingMountIndex.set(index);
    this.editMountPath.set(mount.destination);
    this.editMountReadOnly.set(!mount.rw);
    this.mountError.set(null);
  }

  protected cancelEditMount(): void {
    this.editingMountIndex.set(null);
    this.editMountPath.set('');
    this.editMountReadOnly.set(false);
  }

  protected async saveEditMount(): Promise<void> {
    const c = this.container();
    const idx = this.editingMountIndex();
    if (!c || idx === null) return;
    const path = this.editMountPath().trim();
    if (!path) {
      this.mountError.set('La ruta no puede estar vacía.');
      return;
    }
    const editing = c.mounts[idx];
    if (!editing || editing.type !== 'volume' || !editing.volumeName) return;

    const ok = await this.confirm.ask({
      title: 'Editar volumen',
      message: `Cambiar la ruta o el modo de "${editing.volumeName}" recreará el contenedor. La configuración y los datos del volumen se conservan.`,
      confirmText: 'Guardar y recrear',
    });
    if (!ok) return;

    const body = this.currentVolumeMounts().map((m) => {
      if (
        m.volumeName === editing.volumeName &&
        m.containerPath === editing.destination
      ) {
        return {
          volumeName: m.volumeName,
          containerPath: path,
          readOnly: this.editMountReadOnly(),
        };
      }
      return m;
    });
    this.applyMounts(c.id, body);
  }

  protected openNetworkForm(): void {
    this.networkFormOpen.set(true);
    this.networkError.set(null);
    this.newNetworkId.set('');
    this.networkApi.list().subscribe({
      next: (ns) => this.availableNetworks.set(ns),
    });
  }

  protected closeNetworkForm(): void {
    this.networkFormOpen.set(false);
    this.newNetworkId.set('');
    this.networkError.set(null);
    this.creatingNetwork.set(false);
    this.newNetworkName.set('');
    this.networkCreateError.set(null);
  }

  protected toggleNetworkCreator(): void {
    this.creatingNetwork.update((v) => !v);
    this.newNetworkName.set('');
    this.networkCreateError.set(null);
  }

  protected createNetwork(): void {
    const name = this.newNetworkName().trim();
    if (!name) return;
    this.networkCreateError.set(null);
    this.networkApi.create(name).subscribe({
      next: (net) => {
        this.availableNetworks.update((list) => [...list, net]);
        this.newNetworkId.set(net.id);
        this.creatingNetwork.set(false);
        this.newNetworkName.set('');
      },
      error: (err: unknown) => {
        this.networkCreateError.set(
          err instanceof ApiError ? err.message : 'No se pudo crear la red.',
        );
      },
    });
  }

  protected connectNetwork(): void {
    const c = this.container();
    const id = this.newNetworkId();
    if (!c || !id) return;
    this.networkSaving.set(true);
    this.networkError.set(null);
    this.networkApi.connect(id, c.id).subscribe({
      next: () => {
        this.networkSaving.set(false);
        this.closeNetworkForm();
        this.refreshDetail(c.id);
      },
      error: (err: unknown) => {
        this.networkSaving.set(false);
        this.networkError.set(
          err instanceof ApiError ? err.message : 'No se pudo conectar a la red.',
        );
      },
    });
  }

  protected async confirmDisconnectNetwork(networkName: string): Promise<void> {
    const c = this.container();
    if (!c) return;
    const matching = this.availableNetworks().find((n) => n.name === networkName);
    if (!matching) {
      // Lazy-load available networks to find the id, then retry.
      this.networkApi.list().subscribe({
        next: (ns) => {
          this.availableNetworks.set(ns);
          void this.confirmDisconnectNetwork(networkName);
        },
      });
      return;
    }

    const isBuiltin = !!matching.builtin;
    let othersUsing = 0;
    if (!isBuiltin) {
      try {
        const detail = await this.networkApi.get(matching.id).toPromise();
        othersUsing = (detail?.connectedContainers ?? []).filter(
          (cc) => cc.id !== c.id,
        ).length;
      } catch {
        // If inspect fails, fall back to optimistic flow (offer the button).
        othersUsing = 0;
      }
    }

    const canDelete = !isBuiltin && othersUsing === 0;
    const message = isBuiltin
      ? `El contenedor perderá acceso a "${networkName}". Esta es una red interna de Docker, no se puede borrar.`
      : othersUsing > 0
        ? `El contenedor perderá acceso a "${networkName}". La red la usan ${othersUsing} contenedor${othersUsing === 1 ? '' : 'es'} más, así que no se puede borrar desde aquí.`
        : `El contenedor perderá acceso a "${networkName}". Nadie más la usa, puedes aprovechar para borrarla y mantener limpio el sistema.`;

    const choice = await this.confirm.askChoice({
      title: `Quitar "${networkName}"`,
      message,
      variant: 'soft-danger',
      confirmText: 'Solo desconectar',
      secondaryConfirmText: canDelete ? 'Desconectar y borrar la red' : undefined,
      secondaryVariant: 'danger',
    });
    if (choice === 'cancel') return;

    this.networkSaving.set(true);
    this.networkError.set(null);
    this.networkApi.disconnect(matching.id, c.id).subscribe({
      next: () => {
        if (choice === 'secondary') {
          this.deleteNetworkAfterDisconnect(matching.id, networkName, c.id);
        } else {
          this.networkSaving.set(false);
          this.refreshDetail(c.id);
        }
      },
      error: (err: unknown) => {
        this.networkSaving.set(false);
        this.networkError.set(
          err instanceof ApiError ? err.message : 'No se pudo desconectar de la red.',
        );
      },
    });
  }

  private deleteNetworkAfterDisconnect(
    networkId: string,
    networkName: string,
    containerId: string,
  ): void {
    this.networkApi.remove(networkId).subscribe({
      next: () => {
        this.networkSaving.set(false);
        this.refreshDetail(containerId);
      },
      error: (err: unknown) => {
        this.networkSaving.set(false);
        this.refreshDetail(containerId);
        const base =
          err instanceof ApiError
            ? err.message
            : 'No se pudo borrar la red.';
        this.networkError.set(
          `Desconectado de "${networkName}", pero no se pudo borrar: ${base}`,
        );
      },
    });
  }

  protected async confirmRemoveMount(mount: ContainerMount): Promise<void> {
    const c = this.container();
    if (!c || !this.canEditMounts() || !mount.volumeName) return;
    const ok = await this.confirm.ask({
      title: 'Quitar volumen',
      message: `Quitar "${mount.volumeName}" recreará el contenedor. El volumen seguirá existiendo en Docker; solo se desmonta de este contenedor.`,
      variant: 'danger',
      confirmText: 'Quitar y recrear',
    });
    if (!ok) return;

    const body = this.currentVolumeMounts().filter(
      (m) => m.volumeName !== mount.volumeName || m.containerPath !== mount.destination,
    );
    this.applyMounts(c.id, body);
  }

  private currentVolumeMounts(): VolumeMountInput[] {
    const c = this.container();
    if (!c) return [];
    return c.mounts
      .filter((m) => m.type === 'volume' && !!m.volumeName)
      .map((m) => ({
        volumeName: m.volumeName!,
        containerPath: m.destination,
        readOnly: !m.rw,
      }));
  }

  private applyMounts(id: string, body: VolumeMountInput[]): void {
    this.mountSaving.set(true);
    this.mountError.set(null);
    this.api.updateMounts(id, body).subscribe({
      next: (updated) => {
        this.mountSaving.set(false);
        this.container.set(updated);
        this.closeMountForm();
        this.cancelEditMount();
        if (updated.id !== id) {
          void this.router.navigate(['/containers', this.shortId(updated.id)], {
            replaceUrl: true,
          });
        }
      },
      error: (err: unknown) => {
        this.mountSaving.set(false);
        this.mountError.set(
          err instanceof ApiError ? err.message : 'No se pudieron actualizar los volúmenes.',
        );
      },
    });
  }

  protected openPortForm(): void {
    if (!this.canEditPorts()) return;
    this.portFormOpen.set(true);
    this.portError.set(null);
    this.newPortHost.set(null);
    const detected = this.detectedContainerPorts();
    if (detected.length > 0) {
      this.useCustomContainerPort.set(false);
      this.newPortContainer.set(detected[0].port);
      this.newPortProtocol.set(detected[0].protocol);
    } else {
      this.useCustomContainerPort.set(true);
      this.newPortContainer.set(null);
      this.newPortProtocol.set('tcp');
    }
  }

  protected closePortForm(): void {
    this.portFormOpen.set(false);
    this.portError.set(null);
  }

  /** Selecting a detected port from the dropdown sets both port and protocol at once. */
  protected onDetectedPortChange(value: string): void {
    if (value === '__custom__') {
      this.useCustomContainerPort.set(true);
      this.newPortContainer.set(null);
      this.newPortProtocol.set('tcp');
      return;
    }
    const [portStr, proto] = value.split('/');
    const port = parseInt(portStr, 10);
    if (!isNaN(port)) this.newPortContainer.set(port);
    if (proto === 'tcp' || proto === 'udp') this.newPortProtocol.set(proto);
  }

  /** Used as the [ngModel] of the detected-port select to reflect the current selection. */
  protected currentDetectedKey(): string {
    const port = this.newPortContainer();
    const proto = this.newPortProtocol();
    if (port === null) return '';
    return `${port}/${proto}`;
  }

  protected async addPort(): Promise<void> {
    const c = this.container();
    if (!c || !this.canEditPorts()) return;
    const host = this.newPortHost();
    const containerPort = this.newPortContainer();
    const protocol = this.newPortProtocol();
    if (!host || !containerPort) {
      this.portError.set('Indica el puerto del host y el del contenedor.');
      return;
    }
    if (host < 1 || host > 65535 || containerPort < 1 || containerPort > 65535) {
      this.portError.set('Los puertos deben estar entre 1 y 65535.');
      return;
    }
    const current = this.currentPortMappings();
    const dup = current.some(
      (p) => p.hostPort === host && p.protocol === protocol,
    );
    if (dup) {
      this.portError.set(`El puerto host ${host}/${protocol} ya está publicado.`);
      return;
    }
    const conflict = this.newPortConflict();
    if (conflict) {
      this.portError.set(`Puerto ${host} ya en uso por "${conflict}".`);
      return;
    }

    const ok = await this.confirm.ask({
      title: 'Publicar puerto',
      message:
        'Añadir un puerto recreará el contenedor. La configuración (env, red, volúmenes) y los datos persistidos se conservan.',
      confirmText: 'Añadir y recrear',
    });
    if (!ok) return;

    const body: PortMappingInput[] = [
      ...current,
      { hostPort: host, containerPort, protocol },
    ];
    this.applyPorts(c.id, body);
  }

  protected portKey(p: ContainerPort): string {
    return `${p.privatePort}-${p.publicPort ?? ''}-${p.protocol}`;
  }

  protected startEditPort(port: ContainerPort): void {
    if (!this.canEditPorts() || !port.publicPort) return;
    this.editingPortKey.set(this.portKey(port));
    this.editPortHost.set(port.publicPort);
    this.editPortProtocol.set((port.protocol || 'tcp') as PortProtocol);
    this.portError.set(null);
  }

  protected cancelEditPort(): void {
    this.editingPortKey.set(null);
    this.editPortHost.set(null);
    this.editPortProtocol.set('tcp');
  }

  protected async saveEditPort(port: ContainerPort): Promise<void> {
    const c = this.container();
    if (!c || !this.canEditPorts() || !port.publicPort) return;
    const newHost = this.editPortHost();
    const newProtocol = this.editPortProtocol();
    if (!newHost || newHost < 1 || newHost > 65535) {
      this.portError.set('Puerto host inválido (1-65535).');
      return;
    }
    const oldProtocol = (port.protocol || 'tcp') as PortProtocol;
    if (newHost === port.publicPort && newProtocol === oldProtocol) {
      this.cancelEditPort();
      return;
    }
    const current = this.currentPortMappings();
    const dup = current.some(
      (p) =>
        p.hostPort === newHost &&
        p.protocol === newProtocol &&
        !(p.hostPort === port.publicPort && p.protocol === oldProtocol),
    );
    if (dup) {
      this.portError.set(`El puerto host ${newHost}/${newProtocol} ya está publicado.`);
      return;
    }
    const conflict = this.editPortConflict();
    if (conflict) {
      this.portError.set(`Puerto ${newHost} ya en uso por "${conflict}".`);
      return;
    }

    const ok = await this.confirm.ask({
      title: 'Editar puerto',
      message: `Cambiar el mapeo ${port.publicPort}/${oldProtocol} → ${newHost}/${newProtocol} recreará el contenedor. La configuración y los volúmenes se conservan.`,
      confirmText: 'Guardar y recrear',
    });
    if (!ok) return;

    const body = current.map((p) => {
      if (
        p.hostPort === port.publicPort &&
        p.containerPort === port.privatePort &&
        p.protocol === oldProtocol
      ) {
        return { hostPort: newHost, containerPort: p.containerPort, protocol: newProtocol };
      }
      return p;
    });
    this.applyPorts(c.id, body);
  }

  protected async confirmRemovePort(port: ContainerPort): Promise<void> {
    const c = this.container();
    if (!c || !this.canEditPorts() || !port.publicPort) return;
    const protocol = (port.protocol || 'tcp') as PortProtocol;
    const ok = await this.confirm.ask({
      title: 'Quitar puerto publicado',
      message: `Quitar el mapeo ${port.publicPort} → ${port.privatePort}/${port.protocol} recreará el contenedor. La configuración y los volúmenes se conservan.`,
      variant: 'danger',
      confirmText: 'Quitar y recrear',
    });
    if (!ok) return;

    const body = this.currentPortMappings().filter(
      (p) =>
        !(
          p.hostPort === port.publicPort &&
          p.containerPort === port.privatePort &&
          p.protocol === protocol
        ),
    );
    this.applyPorts(c.id, body);
  }

  private currentPortMappings(): PortMappingInput[] {
    const c = this.container();
    if (!c) return [];
    return c.ports
      .filter((p) => p.publicPort !== null && p.publicPort !== undefined)
      .map((p) => ({
        hostPort: p.publicPort as number,
        containerPort: p.privatePort,
        protocol: ((p.protocol || 'tcp') as PortProtocol),
      }));
  }

  private applyPorts(id: string, body: PortMappingInput[]): void {
    this.portSaving.set(true);
    this.portError.set(null);
    this.api.updatePorts(id, body).subscribe({
      next: (updated) => {
        this.portSaving.set(false);
        this.container.set(updated);
        this.closePortForm();
        this.cancelEditPort();
        if (updated.id !== id) {
          void this.router.navigate(['/containers', this.shortId(updated.id)], {
            replaceUrl: true,
          });
        }
      },
      error: (err: unknown) => {
        this.portSaving.set(false);
        this.portError.set(
          err instanceof ApiError ? err.message : 'No se pudieron actualizar los puertos.',
        );
      },
    });
  }

  protected shortId(id: string): string {
    if (!id) return '';
    return id.startsWith('sha256:') ? id.slice(7, 19) : id.slice(0, 12);
  }

  protected uptime(): string {
    const c = this.container();
    if (!c) return '';
    if (!c.state.running || !c.state.startedAt || c.state.startedAt.startsWith('0001'))
      return c.state.status;
    const started = new Date(c.state.startedAt).getTime();
    if (isNaN(started)) return c.state.status;
    let secs = Math.max(0, Math.floor((Date.now() - started) / 1000));
    const d = Math.floor(secs / 86400); secs -= d * 86400;
    const h = Math.floor(secs / 3600); secs -= h * 3600;
    const m = Math.floor(secs / 60);
    if (d > 0) return `Uptime ${d}d ${h}h ${m}m`;
    if (h > 0) return `Uptime ${h}h ${m}m`;
    return `Uptime ${m}m`;
  }

  protected formatDate(iso: string): string {
    if (!iso || iso.startsWith('0001-01-01')) return '—';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    return d.toLocaleString('es-ES', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  }

  protected formatBytes(bytes: number): string {
    if (!bytes || bytes <= 0) return 'sin límite';
    const gib = bytes / 1024 ** 3;
    if (gib >= 1) return `${gib.toFixed(2)} GiB`;
    const mib = bytes / 1024 ** 2;
    return `${mib.toFixed(0)} MiB`;
  }

  protected formatCpus(nanoCpus: number): string {
    if (!nanoCpus || nanoCpus <= 0) return 'sin límite';
    return (nanoCpus / 1_000_000_000).toFixed(2);
  }

  protected formatDuration(nanos: number): string {
    if (!nanos || nanos <= 0) return '—';
    const seconds = nanos / 1_000_000_000;
    if (seconds < 60) return `${seconds.toFixed(0)}s`;
    const minutes = seconds / 60;
    return `${minutes.toFixed(1)}m`;
  }

  protected envName(entry: string): string {
    const idx = entry.indexOf('=');
    return idx === -1 ? entry : entry.slice(0, idx);
  }

  protected envValue(entry: string): string {
    const idx = entry.indexOf('=');
    return idx === -1 ? '' : entry.slice(idx + 1);
  }

  // ─── Settings tab ─────────────────────────────────────────────────────

  // ─── Rename (live) ──────
  protected readonly renameOpen = signal(false);
  protected readonly renameValue = signal('');
  protected readonly renameSaving = signal(false);

  protected openRename(): void {
    const c = this.container();
    if (!c) return;
    this.renameValue.set(c.name);
    this.renameOpen.set(true);
  }
  protected closeRename(): void {
    this.renameOpen.set(false);
  }
  protected saveRename(): void {
    const c = this.container();
    const name = this.renameValue().trim();
    if (!c || !name || name === c.name) {
      this.closeRename();
      return;
    }
    this.renameSaving.set(true);
    this.api.rename(c.id, name).subscribe({
      next: (updated) => {
        this.renameSaving.set(false);
        this.container.set(updated);
        this.titleService.setTitle(`DockLite | ${updated.name}`);
        this.closeRename();
      },
      error: (err: unknown) => {
        this.renameSaving.set(false);
        void this.confirm.ask({
          title: 'No se pudo renombrar',
          message: err instanceof ApiError ? err.message : 'Error al renombrar.',
          variant: 'danger',
          hideCancel: true,
          confirmText: 'Entendido',
        });
      },
    });
  }

  // ─── Resources (live for memory/cpus, recreate for restartPolicy) ──────
  protected readonly resourcesEditing = signal(false);
  protected readonly resMemoryMb = signal<number | null>(null);
  protected readonly resCpus = signal<number | null>(null);
  protected readonly resRestartPolicy = signal<RestartPolicy>('no');
  protected readonly resourcesSaving = signal(false);

  protected openResourcesEditor(): void {
    const c = this.container();
    if (!c) return;
    const mem = c.hostConfig.memoryLimitBytes;
    this.resMemoryMb.set(mem > 0 ? Math.round(mem / 1024 / 1024) : null);
    const cpu = c.hostConfig.nanoCpus;
    this.resCpus.set(cpu > 0 ? Number((cpu / 1_000_000_000).toFixed(2)) : null);
    this.resRestartPolicy.set((c.hostConfig.restartPolicy || 'no') as RestartPolicy);
    this.resourcesEditing.set(true);
  }
  protected closeResourcesEditor(): void {
    this.resourcesEditing.set(false);
  }
  protected async saveResources(): Promise<void> {
    const c = this.container();
    if (!c) return;
    const newMem = this.resMemoryMb();
    const newCpus = this.resCpus();
    const newRp = this.resRestartPolicy();
    const oldMem = c.hostConfig.memoryLimitBytes > 0
      ? Math.round(c.hostConfig.memoryLimitBytes / 1024 / 1024) : null;
    const oldCpus = c.hostConfig.nanoCpus > 0
      ? Number((c.hostConfig.nanoCpus / 1_000_000_000).toFixed(2)) : null;
    const oldRp = (c.hostConfig.restartPolicy || 'no') as RestartPolicy;

    const payload: { memoryMb?: number | null; cpus?: number | null; restartPolicy?: RestartPolicy | null } = {};
    if (newMem !== oldMem) payload.memoryMb = newMem;
    if (newCpus !== oldCpus) payload.cpus = newCpus;
    if (newRp !== oldRp) payload.restartPolicy = newRp;
    if (Object.keys(payload).length === 0) {
      this.closeResourcesEditor();
      return;
    }

    // restartPolicy change requires recreate (stopped). Confirm and pre-warn.
    if (payload.restartPolicy && c.state.running) {
      void this.confirm.ask({
        title: 'Detén el contenedor primero',
        message: 'Cambiar la política de reinicio recrea el contenedor, así que tienes que pararlo antes.',
        variant: 'danger',
        hideCancel: true,
        confirmText: 'Entendido',
      });
      return;
    }
    if (payload.restartPolicy) {
      const ok = await this.confirm.ask({
        title: 'Aplicar nuevos recursos',
        message: 'La política de reinicio recreará el contenedor (id nuevo). El resto de la config se conserva.',
        confirmText: 'Aplicar y recrear',
      });
      if (!ok) return;
    }

    this.resourcesSaving.set(true);
    this.api.updateResources(c.id, payload).subscribe({
      next: (updated) => {
        this.resourcesSaving.set(false);
        this.container.set(updated);
        this.closeResourcesEditor();
        if (updated.id !== c.id) {
          void this.router.navigate(['/containers', this.shortId(updated.id)], { replaceUrl: true });
        }
      },
      error: (err: unknown) => {
        this.resourcesSaving.set(false);
        void this.confirm.ask({
          title: 'No se pudieron aplicar los recursos',
          message: err instanceof ApiError ? err.message : 'Error al actualizar recursos.',
          variant: 'danger',
          hideCancel: true,
          confirmText: 'Entendido',
        });
      },
    });
  }

  // ─── Env vars (recreate, requires stopped) ──────
  protected readonly envEditing = signal(false);
  protected readonly envRows = signal<EnvVarInput[]>([]);
  protected readonly envSaving = signal(false);

  protected readonly canEditEnv = computed(() => {
    const c = this.container();
    return !!c && !c.state.running;
  });

  /** EXPOSE-like default env vars set by the image — these can't be removed
   *  via UI because they come back on recreate from the image config. */
  private readonly imageEnvDefaults = computed<Set<string>>(() => {
    // We don't currently fetch the image config. Treat all current env as
    // editable — the user can still add/remove/edit; if the image re-adds
    // its own defaults at recreate time, that's the daemon's call.
    return new Set();
  });

  protected openEnvEditor(): void {
    const c = this.container();
    if (!c || !this.canEditEnv()) return;
    this.envRows.set(
      c.config.env.map((e) => ({ name: this.envName(e), value: this.envValue(e) })),
    );
    this.envEditing.set(true);
  }
  protected closeEnvEditor(): void {
    this.envEditing.set(false);
  }
  protected addEnvRow(): void {
    this.envRows.update((rows) => [...rows, { name: '', value: '' }]);
  }
  protected removeEnvRow(i: number): void {
    this.envRows.update((rows) => rows.filter((_, idx) => idx !== i));
  }
  protected updateEnvRow(i: number, patch: Partial<EnvVarInput>): void {
    this.envRows.update((rows) =>
      rows.map((r, idx) => (idx === i ? { ...r, ...patch } : r)),
    );
  }
  protected async saveEnv(): Promise<void> {
    const c = this.container();
    if (!c || !this.canEditEnv()) return;
    const rows = this.envRows()
      .filter((r) => r.name.trim() !== '')
      .map((r) => ({ name: r.name.trim(), value: r.value ?? '' }));
    // Reject duplicate names.
    const seen = new Set<string>();
    for (const r of rows) {
      if (seen.has(r.name)) {
        void this.confirm.ask({
          title: 'Variables duplicadas',
          message: `La variable "${r.name}" aparece más de una vez. Cada nombre debe ser único.`,
          variant: 'danger', hideCancel: true, confirmText: 'Entendido',
        });
        return;
      }
      seen.add(r.name);
    }
    const ok = await this.confirm.ask({
      title: 'Aplicar variables de entorno',
      message: 'Recreará el contenedor. La configuración (puertos, volúmenes, red) y los datos persistidos se conservan.',
      confirmText: 'Aplicar y recrear',
    });
    if (!ok) return;
    this.envSaving.set(true);
    this.api.updateEnv(c.id, rows).subscribe({
      next: (updated) => {
        this.envSaving.set(false);
        this.container.set(updated);
        this.closeEnvEditor();
        if (updated.id !== c.id) {
          void this.router.navigate(['/containers', this.shortId(updated.id)], { replaceUrl: true });
        }
      },
      error: (err: unknown) => {
        this.envSaving.set(false);
        void this.confirm.ask({
          title: 'No se pudieron aplicar las variables',
          message: err instanceof ApiError ? err.message : 'Error al actualizar env.',
          variant: 'danger', hideCancel: true, confirmText: 'Entendido',
        });
      },
    });
  }

  // ─── Command / entrypoint (recreate, requires stopped) ──────
  protected readonly commandEditing = signal(false);
  protected readonly cmdEntrypoint = signal('');
  protected readonly cmdCommand = signal('');
  protected readonly commandSaving = signal(false);

  protected openCommandEditor(): void {
    const c = this.container();
    if (!c || !this.canEditEnv()) return; // same gating
    this.cmdEntrypoint.set((c.config.entrypoint ?? []).join(' '));
    this.cmdCommand.set((c.config.command ?? []).join(' '));
    this.commandEditing.set(true);
  }
  protected closeCommandEditor(): void {
    this.commandEditing.set(false);
  }
  protected async saveCommand(): Promise<void> {
    const c = this.container();
    if (!c || !this.canEditEnv()) return;
    const ep = this.cmdEntrypoint().trim();
    const cmd = this.cmdCommand().trim();
    const ok = await this.confirm.ask({
      title: 'Aplicar comando',
      message: 'Recreará el contenedor. Si dejas un campo en blanco, vuelve al valor de la imagen.',
      confirmText: 'Aplicar y recrear',
    });
    if (!ok) return;
    this.commandSaving.set(true);
    this.api.updateCommand(c.id, {
      entrypoint: ep ? ep.split(/\s+/) : null,
      command: cmd ? cmd.split(/\s+/) : null,
    }).subscribe({
      next: (updated) => {
        this.commandSaving.set(false);
        this.container.set(updated);
        this.closeCommandEditor();
        if (updated.id !== c.id) {
          void this.router.navigate(['/containers', this.shortId(updated.id)], { replaceUrl: true });
        }
      },
      error: (err: unknown) => {
        this.commandSaving.set(false);
        void this.confirm.ask({
          title: 'No se pudo aplicar el comando',
          message: err instanceof ApiError ? err.message : 'Error al actualizar el comando.',
          variant: 'danger', hideCancel: true, confirmText: 'Entendido',
        });
      },
    });
  }

  // ─── Healthcheck (recreate, requires stopped) ──────
  protected readonly hcEditing = signal(false);
  protected readonly hcEnabled = signal(true);
  protected readonly hcTest = signal('');
  protected readonly hcInterval = signal<number | null>(30);
  protected readonly hcTimeout = signal<number | null>(5);
  protected readonly hcRetries = signal<number | null>(3);
  protected readonly hcStartPeriod = signal<number | null>(0);
  protected readonly hcSaving = signal(false);

  protected openHealthcheckEditor(): void {
    const c = this.container();
    if (!c || !this.canEditEnv()) return;
    const hc = c.healthcheck;
    if (hc && !(hc.test.length === 1 && hc.test[0] === 'NONE')) {
      this.hcEnabled.set(true);
      this.hcTest.set(hc.test.join(' '));
      this.hcInterval.set(hc.intervalNanos > 0 ? Math.round(hc.intervalNanos / 1_000_000_000) : null);
      this.hcTimeout.set(hc.timeoutNanos > 0 ? Math.round(hc.timeoutNanos / 1_000_000_000) : null);
      this.hcRetries.set(hc.retries > 0 ? hc.retries : null);
      this.hcStartPeriod.set(hc.startPeriodNanos > 0 ? Math.round(hc.startPeriodNanos / 1_000_000_000) : null);
    } else {
      this.hcEnabled.set(false);
      this.hcTest.set('CMD-SHELL curl -f http://localhost/ || exit 1');
      this.hcInterval.set(30); this.hcTimeout.set(5); this.hcRetries.set(3); this.hcStartPeriod.set(0);
    }
    this.hcEditing.set(true);
  }
  protected closeHealthcheckEditor(): void {
    this.hcEditing.set(false);
  }
  protected async saveHealthcheck(): Promise<void> {
    const c = this.container();
    if (!c || !this.canEditEnv()) return;
    const payload: HealthcheckInput = this.hcEnabled()
      ? {
          test: this.hcTest().trim().split(/\s+/),
          intervalSeconds: this.hcInterval() ?? null,
          timeoutSeconds: this.hcTimeout() ?? null,
          retries: this.hcRetries() ?? null,
          startPeriodSeconds: this.hcStartPeriod() ?? null,
        }
      : { test: [] };
    if (this.hcEnabled() && payload.test.length === 0) {
      void this.confirm.ask({
        title: 'Indica el comando del healthcheck',
        message: 'Por ejemplo: CMD-SHELL curl -f http://localhost/ || exit 1',
        variant: 'danger', hideCancel: true, confirmText: 'Entendido',
      });
      return;
    }
    const ok = await this.confirm.ask({
      title: this.hcEnabled() ? 'Aplicar healthcheck' : 'Desactivar healthcheck',
      message: 'Recreará el contenedor. La configuración y los datos se conservan.',
      confirmText: 'Aplicar y recrear',
    });
    if (!ok) return;
    this.hcSaving.set(true);
    this.api.updateHealthcheck(c.id, payload).subscribe({
      next: (updated) => {
        this.hcSaving.set(false);
        this.container.set(updated);
        this.closeHealthcheckEditor();
        if (updated.id !== c.id) {
          void this.router.navigate(['/containers', this.shortId(updated.id)], { replaceUrl: true });
        }
      },
      error: (err: unknown) => {
        this.hcSaving.set(false);
        void this.confirm.ask({
          title: 'No se pudo aplicar el healthcheck',
          message: err instanceof ApiError ? err.message : 'Error al actualizar el healthcheck.',
          variant: 'danger', hideCancel: true, confirmText: 'Entendido',
        });
      },
    });
  }
}
