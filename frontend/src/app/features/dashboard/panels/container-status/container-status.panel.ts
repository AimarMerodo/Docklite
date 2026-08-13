import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ContainerState, ContainerSummary } from '@core/api/container.service';
import { StatusPillComponent } from '@shared/ui/status-pill/status-pill.component';

@Component({
  selector: 'app-container-status-panel',
  standalone: true,
  imports: [RouterLink, StatusPillComponent],
  templateUrl: './container-status.panel.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-col h-full rounded-lg border border-hairline bg-surface-1' },
})
export class ContainerStatusPanel {
  readonly containers = input<ContainerSummary[]>([]);
  readonly loading = input(false);
  readonly limit = input(5);

  protected readonly skeletonRows = [0, 1, 2, 3, 4];

  readonly visible = computed(() => {
    const sorted = [...this.containers()].sort(
      (a, b) => statePriority(a.state) - statePriority(b.state),
    );
    return sorted.slice(0, this.limit());
  });

  protected shortId(id: string): string {
    if (!id) return '';
    return id.startsWith('sha256:') ? id.slice(7, 19) : id.slice(0, 12);
  }
}

/** Lower number = shown first. Running containers always lead. */
function statePriority(state: ContainerState): number {
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
