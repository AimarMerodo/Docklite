import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  OnInit,
  output,
  signal,
} from '@angular/core';
import { input } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, map, Observable, of, startWith, switchMap } from 'rxjs';

import {
  ContainerService,
  ContainerSummary,
  CreateContainerRequest,
  EnvVarInput,
  PortInUse,
  PortMappingInput,
  PortProtocol,
  RestartPolicy,
  VolumeMountInput,
} from '@core/api/container.service';
import { ImageDetail, ImageExposedPort, ImageService } from '@core/api/image.service';
import { NetworkService, NetworkSummary } from '@core/api/network.service';
import { VolumeService, VolumeSummary } from '@core/api/volume.service';
import { ApiError } from '@core/http/error.interceptor';
import { ConfirmService } from '@core/ui/confirm.service';

interface SummaryData {
  image: string;
  name: string;
  network: string;
  autoStart: boolean;
  ports: string;
  envCount: string;
  volumeCount: string;
}

@Component({
  selector: 'app-create-container-wizard',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './create-wizard.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateContainerWizard implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ContainerService);
  private readonly networkApi = inject(NetworkService);
  private readonly volumeApi = inject(VolumeService);
  private readonly imageApi = inject(ImageService);
  private readonly confirm = inject(ConfirmService);

  readonly open = input(false);
  readonly close = output<void>();
  readonly created = output<ContainerSummary>();

  protected readonly step = signal<1 | 2 | 3>(1);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly networks = signal<NetworkSummary[]>([]);
  protected readonly volumesList = signal<VolumeSummary[]>([]);

  protected readonly creatingVolume = signal(false);
  protected readonly newVolumeName = signal('');
  protected readonly volumeCreateError = signal<string | null>(null);
  /** Volumes created from inside this wizard session — can be undone with the × button. */
  protected readonly sessionVolumes = signal<string[]>([]);

  protected readonly creatingNetwork = signal(false);
  protected readonly newNetworkName = signal('');
  protected readonly networkCreateError = signal<string | null>(null);
  protected readonly sessionNetworks = signal<{ id: string; name: string }[]>([]);

  /** Detected EXPOSE'd ports of the chosen image (only for already-pulled images). */
  protected readonly imageExposedPorts = signal<ImageExposedPort[]>([]);

  /** True while the wizard is inspecting the image for exposed ports. */
  protected readonly portsHydrating = signal(false);

  /** Host ports already in use by other containers on the machine. */
  protected readonly portsInUse = signal<PortInUse[]>([]);

  protected readonly stepLabels = ['Básicos', 'Configuración', 'Recursos'];
  protected readonly stepSubtitles = [
    'Imagen, nombre y red base.',
    'Puertos, variables y volúmenes (todo opcional).',
    'Límites de recursos y revisión final.',
  ];

  protected readonly form = this.fb.nonNullable.group({
    image: ['', [Validators.required, Validators.minLength(1)]],
    name: [''],
    networkId: [''],
    autoStart: [true],
    ports: this.fb.array<FormGroup>([]),
    env: this.fb.array<FormGroup>([]),
    volumes: this.fb.array<FormGroup>([]),
    memoryMb: this.fb.control<number | null>(null),
    cpus: this.fb.control<number | null>(null),
    restartPolicy: this.fb.nonNullable.control<RestartPolicy>('no'),
  });

  get ports(): FormArray<FormGroup> {
    return this.form.controls.ports as FormArray<FormGroup>;
  }
  get envVars(): FormArray<FormGroup> {
    return this.form.controls.env as FormArray<FormGroup>;
  }
  get volumes(): FormArray<FormGroup> {
    return this.form.controls.volumes as FormArray<FormGroup>;
  }

  /**
   * Reactive forms aren't signal-aware, so a plain `computed` reading
   * `form.getRawValue()` would never re-evaluate. Bridge the form's
   * `valueChanges` into a signal so the summary refreshes whenever the
   * user edits any field.
   */
  private readonly formValue = toSignal(
    this.form.valueChanges.pipe(startWith(this.form.getRawValue())),
    { initialValue: this.form.getRawValue() },
  );

  protected readonly summary = computed<SummaryData>(() => {
    this.formValue();
    const v = this.form.getRawValue();
    const network = v.networkId
      ? (this.networks().find((n) => n.id === v.networkId)?.name ?? v.networkId)
      : 'Por defecto (bridge)';
    const portsText = (v.ports as PortMappingInput[])
      .filter((p) => p.hostPort && p.containerPort)
      .map((p) => `${p.hostPort}→${p.containerPort}/${p.protocol}`)
      .join('  ');
    return {
      image: v.image,
      name: v.name,
      network,
      autoStart: v.autoStart,
      ports: portsText,
      envCount: countLabel(v.env.length, 'ninguna', 'variable', 'variables'),
      volumeCount: countLabel(v.volumes.length, 'ninguno', 'volumen', 'volúmenes'),
    };
  });

  constructor() {
    // Reset wizard state every time it gets opened.
    effect(() => {
      if (this.open()) {
        this.reset();
        // Refresh the port-conflict cache — other users may have created
        // containers since the last time this wizard was opened.
        this.api.portsInUse().subscribe({ next: (p) => this.portsInUse.set(p) });
      }
    });
  }

  ngOnInit(): void {
    this.networkApi.list().subscribe({ next: (n) => this.networks.set(n) });
    this.volumeApi.list().subscribe({ next: (v) => this.volumesList.set(v) });
    this.api.portsInUse().subscribe({ next: (p) => this.portsInUse.set(p) });

    // Whenever the user changes the image, look it up locally to populate
    // the detected-ports dropdown. If the image isn't pulled yet, fall back
    // to inspecting the registry (cheap manifest read, no pull).
    this.form.controls.image.valueChanges
      .pipe(
        debounceTime(400),
        switchMap((value) => {
          const ref = (value || '').trim();
          if (ref) this.portsHydrating.set(true);
          return this.fetchExposedPorts(ref);
        }),
      )
      .subscribe((detected) => {
        this.imageExposedPorts.set(detected);
        this.refreshPortRowsForImage(detected);
        this.portsHydrating.set(false);
      });
  }

  private fetchExposedPorts(ref: string): Observable<ImageExposedPort[]> {
    if (!ref) return of<ImageExposedPort[]>([]);
    return this.imageApi.get(ref).pipe(
      map<ImageDetail, ImageExposedPort[]>((d) => d.exposedPorts ?? []),
      catchError(() =>
        this.imageApi.inspectRemote(ref).pipe(
          map<{ exposedPorts: ImageExposedPort[] }, ImageExposedPort[]>(
            (r) => r.exposedPorts ?? [],
          ),
          catchError(() => of<ImageExposedPort[]>([])),
        ),
      ),
    );
  }

  /**
   * After the image changes, sync the port rows with the new detected list:
   *  - Custom rows (typed by hand) are left untouched.
   *  - Auto rows that no longer match the new image's EXPOSE list are removed.
   *  - Detected ports that aren't already represented are auto-added with an
   *    empty host port — leaving the user just to fill in the publish.
   */
  private refreshPortRowsForImage(detected: ImageExposedPort[]): void {
    // 1. Drop auto rows that no longer match the new image's EXPOSE list.
    for (let i = this.ports.length - 1; i >= 0; i--) {
      const row = this.ports.at(i);
      if (row.get('customPort')?.value) continue;
      const port = row.get('containerPort')?.value as number | null;
      const proto = row.get('protocol')?.value;
      const stillValid =
        port !== null &&
        detected.some((p) => p.port === port && p.protocol === proto);
      if (!stillValid) this.ports.removeAt(i);
    }
    // 2. Add a row for each detected port that isn't already present.
    for (const p of detected) {
      const proto = (p.protocol === 'udp' ? 'udp' : 'tcp') as PortProtocol;
      const exists = this.ports.controls.some((row) => {
        return (
          row.get('containerPort')?.value === p.port &&
          row.get('protocol')?.value === proto
        );
      });
      if (!exists) this.ports.push(this.makePortRow(null, p.port, proto, false));
    }
  }

  protected canAdvance(): boolean {
    if (this.step() === 1) return this.form.controls.image.valid;
    if (this.step() === 2) {
      return (
        this.ports.valid &&
        this.envVars.valid &&
        this.volumes.valid &&
        !this.hasPortConflicts()
      );
    }
    return this.form.valid && !this.hasPortConflicts();
  }

  protected next(): void {
    if (!this.canAdvance()) {
      this.markStepTouched();
      return;
    }
    if (this.step() < 3) {
      this.step.update((s) => (s + 1) as 1 | 2 | 3);
      // The image may have been typed faster than the 400ms debounce — make
      // sure the detected ports are hydrated by the time the user lands on
      // the ports section.
      if (this.step() === 2) this.ensurePortsHydrated();
    }
  }

  private ensurePortsHydrated(): void {
    if (this.imageExposedPorts().length > 0) return;
    const ref = (this.form.controls.image.value || '').trim();
    if (!ref) return;
    this.portsHydrating.set(true);
    this.fetchExposedPorts(ref).subscribe((detected) => {
      this.imageExposedPorts.set(detected);
      this.refreshPortRowsForImage(detected);
      this.portsHydrating.set(false);
    });
  }

  protected back(): void {
    if (this.step() > 1) this.step.update((s) => (s - 1) as 1 | 2 | 3);
  }

  protected cancel(): void {
    this.close.emit();
  }

  /** Tracks whether the mousedown started on the backdrop itself (not the panel). */
  private backdropMouseDown = false;

  protected onBackdropMouseDown(event: MouseEvent): void {
    this.backdropMouseDown = event.target === event.currentTarget;
  }

  protected onBackdropClick(event: MouseEvent): void {
    // Ignore clicks that started inside the panel and drifted out.
    if (!this.backdropMouseDown) return;
    if (event.target !== event.currentTarget) return;
    this.cancel();
  }

  protected addPort(): void {
    if (this.ports.length > 0 && this.ports.at(this.ports.length - 1).invalid) {
      this.ports.at(this.ports.length - 1).markAllAsTouched();
      return;
    }
    // Manual-add buttons start in custom mode so the user types both ports.
    this.ports.push(this.makePortRow(null, null, 'tcp', true));
  }

  /** Builds a port row. Empty hostPort means "leave it internal" (won't publish). */
  private makePortRow(
    hostPort: number | null,
    containerPort: number | null,
    protocol: 'tcp' | 'udp',
    customPort: boolean,
  ): FormGroup {
    return this.fb.nonNullable.group({
      hostPort: this.fb.control<number | null>(hostPort, [Validators.min(1)]),
      containerPort: this.fb.control<number | null>(containerPort, [
        Validators.required,
        Validators.min(1),
      ]),
      protocol: [protocol, Validators.required],
      // Excluded from the payload — UI-only flag for the row's input mode.
      customPort: [customPort],
    });
  }

  protected removePort(i: number): void {
    this.ports.removeAt(i);
  }

  /** Returns the name of the container blocking this host port, or null if free. */
  protected portConflictForRow(i: number): string | null {
    this.formValue();
    const row = this.ports.at(i);
    if (!row) return null;
    const host = row.get('hostPort')?.value as number | null;
    const proto = row.get('protocol')?.value;
    if (!host) return null;
    const match = this.portsInUse().find(
      (p) => p.hostPort === host && p.protocol === proto,
    );
    return match ? match.containerName : null;
  }

  /** True if any port row has a hostPort already taken by another container. */
  protected hasPortConflicts(): boolean {
    for (let i = 0; i < this.ports.length; i++) {
      if (this.portConflictForRow(i)) return true;
    }
    return false;
  }

  /** Returns `port/protocol` for the row's current containerPort+protocol, or empty. */
  protected portKeyForRow(i: number): string {
    const row = this.ports.at(i);
    if (!row) return '';
    const port = row.get('containerPort')?.value;
    const proto = row.get('protocol')?.value;
    if (port === null || port === undefined || port === '') return '';
    return `${port}/${proto}`;
  }

  /** When user picks a detected port from the dropdown, patch both fields. */
  protected onDetectedPortChange(i: number, value: string): void {
    const row = this.ports.at(i);
    if (!row) return;
    if (value === '__custom__') {
      row.patchValue({ customPort: true, containerPort: null });
      return;
    }
    const [portStr, proto] = value.split('/');
    const port = parseInt(portStr, 10);
    if (isNaN(port)) return;
    row.patchValue({
      containerPort: port,
      protocol: (proto === 'udp' ? 'udp' : 'tcp') as PortProtocol,
      customPort: false,
    });
  }

  /** Manual escape from custom mode back to detected list. */
  protected useDetectedForRow(i: number): void {
    const row = this.ports.at(i);
    if (!row) return;
    const first = this.imageExposedPorts()[0];
    if (!first) return;
    row.patchValue({
      containerPort: first.port,
      protocol: (first.protocol === 'udp' ? 'udp' : 'tcp') as PortProtocol,
      customPort: false,
    });
  }

  protected addEnv(): void {
    if (this.envVars.length > 0 && this.envVars.at(this.envVars.length - 1).invalid) {
      this.envVars.at(this.envVars.length - 1).markAllAsTouched();
      return;
    }
    this.envVars.push(
      this.fb.nonNullable.group({
        name: ['', Validators.required],
        value: [''],
      }),
    );
  }

  protected removeEnv(i: number): void {
    this.envVars.removeAt(i);
  }

  protected toggleVolumeCreator(): void {
    this.creatingVolume.update((v) => !v);
    this.newVolumeName.set('');
    this.volumeCreateError.set(null);
  }

  protected onNewVolumeName(event: Event): void {
    this.newVolumeName.set((event.target as HTMLInputElement).value);
  }

  protected createVolume(): void {
    const name = this.newVolumeName().trim();
    if (!name) return;
    this.volumeCreateError.set(null);
    this.volumeApi.create(name).subscribe({
      next: (vol) => {
        this.volumesList.update((list) => [...list, vol]);
        this.sessionVolumes.update((s) => [...s, vol.name]);
        const emptyRow = this.volumes.controls.find(
          (g) => !g.get('volumeName')?.value,
        );
        if (emptyRow) emptyRow.patchValue({ volumeName: vol.name });
        else {
          this.addVolume();
          this.volumes.at(this.volumes.length - 1).patchValue({ volumeName: vol.name });
        }
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

  protected undoSessionVolume(name: string): void {
    this.volumeApi.remove(name).subscribe({
      next: () => {
        this.volumesList.update((list) => list.filter((v) => v.name !== name));
        this.sessionVolumes.update((s) => s.filter((n) => n !== name));
        this.volumes.controls.forEach((row) => {
          if (row.get('volumeName')?.value === name) {
            row.patchValue({ volumeName: '' });
          }
        });
      },
      error: (err: unknown) => {
        this.volumeCreateError.set(
          err instanceof ApiError ? err.message : 'No se pudo eliminar el volumen.',
        );
      },
    });
  }

  protected toggleNetworkCreator(): void {
    this.creatingNetwork.update((v) => !v);
    this.newNetworkName.set('');
    this.networkCreateError.set(null);
  }

  protected onNewNetworkName(event: Event): void {
    this.newNetworkName.set((event.target as HTMLInputElement).value);
  }

  protected createNetwork(): void {
    const name = this.newNetworkName().trim();
    if (!name) return;
    this.networkCreateError.set(null);
    this.networkApi.create(name).subscribe({
      next: (net) => {
        this.networks.update((list) => [...list, net]);
        this.sessionNetworks.update((s) => [...s, { id: net.id, name: net.name }]);
        this.form.patchValue({ networkId: net.id });
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

  protected undoSessionNetwork(id: string): void {
    this.networkApi.remove(id).subscribe({
      next: () => {
        this.networks.update((list) => list.filter((n) => n.id !== id));
        this.sessionNetworks.update((s) => s.filter((n) => n.id !== id));
        if (this.form.controls.networkId.value === id) {
          this.form.patchValue({ networkId: '' });
        }
      },
      error: (err: unknown) => {
        this.networkCreateError.set(
          err instanceof ApiError ? err.message : 'No se pudo eliminar la red.',
        );
      },
    });
  }

  protected addVolume(): void {
    if (this.volumes.length > 0 && this.volumes.at(this.volumes.length - 1).invalid) {
      this.volumes.at(this.volumes.length - 1).markAllAsTouched();
      return;
    }
    this.volumes.push(
      this.fb.nonNullable.group({
        volumeName: ['', Validators.required],
        containerPath: ['', Validators.required],
        readOnly: [false],
      }),
    );
  }

  protected removeVolume(i: number): void {
    this.volumes.removeAt(i);
  }

  protected submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.api.create(this.buildPayload()).subscribe({
      next: (container) => {
        this.loading.set(false);
        this.created.emit(container);
        this.close.emit();
      },
      error: (err: unknown) => {
        this.loading.set(false);
        const friendly = humanizeError(err instanceof ApiError ? err.message : null);
        // Surface the error in a proper dialog instead of the cramped
        // banner inside the wizard — easier to read on long messages.
        void this.confirm.ask({
          title: 'No se pudo crear el contenedor',
          message: friendly,
          variant: 'danger',
          hideCancel: true,
          confirmText: 'Entendido',
        });
      },
    });
  }

  private buildPayload(): CreateContainerRequest {
    const v = this.form.getRawValue();
    const ports = (v.ports as PortMappingInput[])
      .filter((p) => p.hostPort && p.containerPort)
      .map((p) => ({
        hostPort: Number(p.hostPort),
        containerPort: Number(p.containerPort),
        protocol: p.protocol,
      }));
    const env = (v.env as EnvVarInput[])
      .filter((e) => e.name)
      .map((e) => ({ name: e.name, value: e.value ?? '' }));
    const volumes = (v.volumes as VolumeMountInput[])
      .filter((vol) => vol.volumeName && vol.containerPath)
      .map((vol) => ({
        volumeName: vol.volumeName,
        containerPath: vol.containerPath,
        readOnly: !!vol.readOnly,
      }));

    return {
      image: v.image.trim(),
      name: v.name?.trim() || undefined,
      autoStart: v.autoStart,
      networkId: v.networkId || undefined,
      ports: ports.length ? ports : undefined,
      env: env.length ? env : undefined,
      volumes: volumes.length ? volumes : undefined,
      restartPolicy: v.restartPolicy && v.restartPolicy !== 'no' ? v.restartPolicy : undefined,
      memoryMb: v.memoryMb ?? undefined,
      cpus: v.cpus ?? undefined,
    };
  }

  private reset(): void {
    this.step.set(1);
    this.loading.set(false);
    this.errorMessage.set(null);
    this.form.reset({
      image: '',
      name: '',
      networkId: '',
      autoStart: true,
      memoryMb: null,
      cpus: null,
      restartPolicy: 'no',
    });
    this.ports.clear();
    this.envVars.clear();
    this.volumes.clear();
    this.sessionVolumes.set([]);
    this.sessionNetworks.set([]);
    this.creatingVolume.set(false);
    this.creatingNetwork.set(false);
    this.newVolumeName.set('');
    this.newNetworkName.set('');
    this.volumeCreateError.set(null);
    this.networkCreateError.set(null);
  }

  private markStepTouched(): void {
    if (this.step() === 1) this.form.controls.image.markAsTouched();
    else if (this.step() === 2) {
      this.ports.controls.forEach((c) => c.markAllAsTouched());
      this.envVars.controls.forEach((c) => c.markAllAsTouched());
      this.volumes.controls.forEach((c) => c.markAllAsTouched());
    } else this.form.markAllAsTouched();
  }
}

function countLabel(n: number, zero: string, singular: string, plural: string): string {
  if (n === 0) return zero;
  if (n === 1) return `1 ${singular}`;
  return `${n} ${plural}`;
}

/** Strip docker-java's wrapper noise and translate the most common cases. */
function humanizeError(raw: string | null): string {
  if (!raw) return 'No se pudo crear el contenedor.';

  const pull = raw.match(/Could not pull image '([^']+)'.*pull access denied/i);
  if (pull) {
    return `La imagen "${pull[1]}" no existe en Docker Hub o requiere autenticación. Revisa el nombre.`;
  }

  // Container name conflict — the host's name namespace is shared across
  // users, so a name another user picked first will fail here. Don't leak
  // that the conflicting container belongs to someone else; just say it's
  // taken.
  const nameConflict = raw.match(/container name "?\/?([\w.-]+)"? is already in use/i);
  if (nameConflict) {
    return `El nombre "${nameConflict[1]}" ya está cogido por otro contenedor del host. Elige otro nombre o déjalo en blanco para que Docker genere uno automático.`;
  }

  // Port conflict (host port already bound by another container)
  const portConflict = raw.match(/port is already allocated|address already in use/i);
  if (portConflict) {
    return 'Uno de los puertos host que has elegido ya está en uso por otro contenedor. Cambia el puerto y vuelve a intentar.';
  }

  const status = raw.match(/Status \d+: \{[^}]*"message"\s*:\s*"([^"]+)"/);
  if (status) return capitalize(status[1]);

  // Generic — drop the noisy "conflict:" / "Could not …" prefixes.
  const cleaned = raw
    .replace(/^Could not [a-z ]+: /i, '')
    .replace(/^conflict:?\s*/i, '');
  return capitalize(cleaned);
}

function capitalize(s: string): string {
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : s;
}
