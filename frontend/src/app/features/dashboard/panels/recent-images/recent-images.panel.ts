import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ImageSummary } from '@core/api/image.service';

@Component({
  selector: 'app-recent-images-panel',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './recent-images.panel.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-col h-full rounded-lg border border-hairline bg-surface-1' },
})
export class RecentImagesPanel {
  readonly images = input<ImageSummary[]>([]);
  readonly loading = input(false);
  readonly limit = input(5);

  protected readonly skeletonRows = [0, 1, 2, 3, 4];

  readonly visible = computed(() => {
    const sorted = [...this.images()].sort(
      (a, b) => parseInt(b.created || '0', 10) - parseInt(a.created || '0', 10),
    );
    return sorted.slice(0, this.limit());
  });

  protected primaryTag(img: ImageSummary): string {
    if (img.tags?.length) return img.tags[0];
    return shortDigest(img.id);
  }

  protected shortDigest(id: string): string {
    return shortDigest(id);
  }

  protected formatBytes(bytes: number): string {
    return formatBytes(bytes);
  }
}

function shortDigest(id: string): string {
  if (!id) return '';
  return id.startsWith('sha256:') ? id.slice(7, 19) : id.slice(0, 12);
}

function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return '—';
  const mib = bytes / 1024 ** 2;
  if (mib >= 1024) return `${(mib / 1024).toFixed(1)} GiB`;
  return `${mib.toFixed(0)} MiB`;
}
