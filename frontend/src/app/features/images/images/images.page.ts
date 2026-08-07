import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuthService } from '@core/auth/auth.service';
import {
  DockerHubSearchResult,
  ImageService,
  ImageSummary,
} from '@core/api/image.service';
import { humanizeDeleteError } from '@core/http/error-messages';
import { ApiError } from '@core/http/error.interceptor';
import { ConfirmService } from '@core/ui/confirm.service';

@Component({
  selector: 'app-images-page',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './images.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImagesPage implements OnInit {
  private readonly api = inject(ImageService);
  private readonly auth = inject(AuthService);
  private readonly confirm = inject(ConfirmService);

  protected readonly isDemo = this.auth.isDemo;

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly images = signal<ImageSummary[]>([]);
  protected readonly search = signal('');

  // Pull modal state.
  protected readonly pullModalOpen = signal(false);
  protected readonly pullImage = signal('');
  protected readonly pullTag = signal('latest');
  protected readonly pulling = signal(false);
  protected readonly pullError = signal<string | null>(null);
  protected readonly hubResults = signal<DockerHubSearchResult[]>([]);
  protected readonly hubSearching = signal(false);

  protected readonly isAdmin = this.auth.isAdmin;

  protected readonly filtered = computed<ImageSummary[]>(() => {
    const q = this.search().trim().toLowerCase();
    const list = this.images();
    if (!q) return list;
    return list.filter((img) => {
      if (img.id.toLowerCase().includes(q)) return true;
      return img.tags?.some((t) => t.toLowerCase().includes(q));
    });
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.list().subscribe({
      next: (list) => {
        // Newest first by created epoch.
        const sorted = [...list].sort(
          (a, b) => parseInt(b.created || '0', 10) - parseInt(a.created || '0', 10),
        );
        this.images.set(sorted);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          err instanceof ApiError ? err.message : 'No se pudieron cargar las imágenes.',
        );
      },
    });
  }

  protected openPullModal(): void {
    this.pullModalOpen.set(true);
    this.pullImage.set('');
    this.pullTag.set('latest');
    this.pullError.set(null);
    this.hubResults.set([]);
  }

  protected closePullModal(): void {
    this.pullModalOpen.set(false);
    this.pullError.set(null);
    this.hubResults.set([]);
  }

  protected searchHub(): void {
    const q = this.pullImage().trim();
    if (!q) {
      this.hubResults.set([]);
      return;
    }
    this.hubSearching.set(true);
    this.api.search(q, 25).subscribe({
      next: (results) => {
        this.hubResults.set(results);
        this.hubSearching.set(false);
      },
      error: () => {
        this.hubSearching.set(false);
        this.hubResults.set([]);
      },
    });
  }

  protected pickHubResult(r: DockerHubSearchResult): void {
    this.pullImage.set(r.name);
    this.hubResults.set([]);
  }

  protected doPull(): void {
    const image = this.pullImage().trim();
    const tag = this.pullTag().trim() || 'latest';
    if (!image) {
      this.pullError.set('Indica el nombre de la imagen.');
      return;
    }
    this.pulling.set(true);
    this.pullError.set(null);
    this.api.pull(image, tag).subscribe({
      next: () => {
        this.pulling.set(false);
        this.closePullModal();
        this.load();
      },
      error: (err: unknown) => {
        this.pulling.set(false);
        this.pullError.set(
          err instanceof ApiError ? err.message : 'No se pudo descargar la imagen.',
        );
      },
    });
  }

  protected async confirmRemove(img: ImageSummary): Promise<void> {
    if (!this.isAdmin()) return;
    const label = this.primaryTag(img) || this.shortDigest(img.id);
    const ok = await this.confirm.ask({
      title: 'Eliminar imagen',
      message: `¿Seguro que quieres eliminar "${label}"? Si algún contenedor la usa, Docker rechazará la operación.`,
      variant: 'danger',
      confirmText: 'Eliminar',
    });
    if (!ok) return;

    this.api.remove(img.id).subscribe({
      next: () => this.load(),
      error: (err: unknown) => {
        void this.confirm.ask({
          title: 'No se pudo eliminar la imagen',
          message: humanizeDeleteError(err, 'imagen'),
          variant: 'danger',
          hideCancel: true,
          confirmText: 'Entendido',
        });
      },
    });
  }

  protected onSearchInput(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected primaryTag(img: ImageSummary): string {
    return img.tags?.length ? img.tags[0] : '';
  }

  protected extraTags(img: ImageSummary): string[] {
    return (img.tags ?? []).slice(1);
  }

  protected shortDigest(id: string): string {
    if (!id) return '';
    return id.startsWith('sha256:') ? id.slice(7, 19) : id.slice(0, 12);
  }

  protected formatBytes(bytes: number): string {
    if (!bytes || bytes <= 0) return '—';
    const mib = bytes / 1024 ** 2;
    if (mib >= 1024) return `${(mib / 1024).toFixed(1)} GiB`;
    return `${mib.toFixed(0)} MiB`;
  }

  protected formatCreated(epoch: string): string {
    if (!epoch) return '—';
    const ms = parseInt(epoch, 10) * 1000;
    if (isNaN(ms)) return '—';
    const diff = Date.now() - ms;
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    if (days <= 0) return 'hoy';
    if (days === 1) return 'ayer';
    if (days < 30) return `hace ${days}d`;
    if (days < 365) return `hace ${Math.floor(days / 30)} meses`;
    return `hace ${Math.floor(days / 365)} años`;
  }
}
