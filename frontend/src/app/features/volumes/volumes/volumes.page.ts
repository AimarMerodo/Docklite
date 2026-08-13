import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { VolumeService, VolumeSummary } from '@core/api/volume.service';
import { AuthService } from '@core/auth/auth.service';
import { humanizeDeleteError } from '@core/http/error-messages';
import { ApiError } from '@core/http/error.interceptor';
import { ConfirmService } from '@core/ui/confirm.service';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';

@Component({
  selector: 'app-volumes-page',
  standalone: true,
  imports: [FormsModule, PageHeaderComponent],
  templateUrl: './volumes.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VolumesPage implements OnInit {
  protected readonly skeletonRows = [0, 1, 2, 3, 4];

  private readonly api = inject(VolumeService);
  private readonly confirm = inject(ConfirmService);
  private readonly auth = inject(AuthService);

  protected readonly isDemo = this.auth.isDemo;

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly volumes = signal<VolumeSummary[]>([]);
  protected readonly search = signal('');

  protected readonly createOpen = signal(false);
  protected readonly newVolumeName = signal('');
  protected readonly creating = signal(false);
  protected readonly createError = signal<string | null>(null);

  protected readonly filtered = computed<VolumeSummary[]>(() => {
    const q = this.search().trim().toLowerCase();
    const list = this.volumes();
    if (!q) return list;
    return list.filter((v) => v.name.toLowerCase().includes(q));
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.list().subscribe({
      next: (list) => {
        this.volumes.set([...list].sort((a, b) => a.name.localeCompare(b.name)));
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          err instanceof ApiError ? err.message : 'No se pudieron cargar los volúmenes.',
        );
      },
    });
  }

  protected onSearchInput(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected openCreate(): void {
    this.createOpen.set(true);
    this.newVolumeName.set('');
    this.createError.set(null);
  }

  protected closeCreate(): void {
    this.createOpen.set(false);
    this.newVolumeName.set('');
    this.createError.set(null);
  }

  protected doCreate(): void {
    const name = this.newVolumeName().trim();
    if (!name) {
      this.createError.set('Indica un nombre para el volumen.');
      return;
    }
    this.creating.set(true);
    this.createError.set(null);
    this.api.create(name).subscribe({
      next: () => {
        this.creating.set(false);
        this.closeCreate();
        this.load();
      },
      error: (err: unknown) => {
        this.creating.set(false);
        this.createError.set(
          err instanceof ApiError ? err.message : 'No se pudo crear el volumen.',
        );
      },
    });
  }

  protected async confirmRemove(vol: VolumeSummary): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Eliminar volumen',
      message: `¿Seguro que quieres eliminar "${vol.name}"? Si algún contenedor lo usa, Docker rechazará la operación.`,
      variant: 'danger',
      confirmText: 'Eliminar',
    });
    if (!ok) return;
    this.api.remove(vol.name).subscribe({
      next: () => this.load(),
      error: (err: unknown) => {
        void this.confirm.ask({
          title: 'No se pudo eliminar el volumen',
          message: humanizeDeleteError(err, 'volumen'),
          variant: 'danger',
          hideCancel: true,
          confirmText: 'Entendido',
        });
      },
    });
  }
}
