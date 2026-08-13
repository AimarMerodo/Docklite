import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { SystemInfoDto } from '@core/api/system.service';

interface InfoRow {
  label: string;
  value: string;
}

@Component({
  selector: 'app-system-info-panel',
  standalone: true,
  templateUrl: './system-info.panel.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-col h-full rounded-lg border border-hairline bg-surface-1' },
})
export class SystemInfoPanel {
  readonly info = input<SystemInfoDto | null>(null);
  readonly loading = input(false);

  protected readonly skeletonRows = [0, 1, 2, 3];

  readonly rows = computed<InfoRow[]>(() => {
    const i = this.info();
    if (!i) return [];
    return [
      { label: 'Host OS', value: i.OperatingSystem },
      { label: 'Architecture', value: `${i.Architecture} · ${i.OSType}` },
      { label: 'CPUs', value: `${i.NCPU} cores` },
      { label: 'RAM', value: formatBytes(i.MemTotal) },
    ];
  });
}

function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return '—';
  const gib = bytes / 1024 ** 3;
  if (gib >= 1) return `${gib.toFixed(1)} GiB`;
  const mib = bytes / 1024 ** 2;
  return `${mib.toFixed(0)} MiB`;
}
