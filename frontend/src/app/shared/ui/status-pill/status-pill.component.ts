import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ContainerState } from '@core/api/container.service';

@Component({
  selector: 'app-status-pill',
  standalone: true,
  templateUrl: './status-pill.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusPillComponent {
  readonly state = input.required<ContainerState>();

  readonly label = computed(() => translate(this.state()));

  readonly cssClasses = computed(() =>
    this.state() === 'running'
      ? 'bg-success/15 text-success'
      : 'bg-surface-2 text-ink-muted border border-hairline-strong',
  );

  readonly dotClasses = computed(() => {
    switch (this.state()) {
      case 'running':
        return 'bg-success';
      case 'restarting':
        return 'bg-ink-muted animate-pulse';
      default:
        return 'bg-ink-tertiary';
    }
  });
}

function translate(state: ContainerState): string {
  switch (state) {
    case 'running':
      return 'Running';
    case 'paused':
      return 'Paused';
    case 'restarting':
      return 'Restarting';
    case 'exited':
      return 'Stopped';
    case 'created':
      return 'Created';
    case 'dead':
      return 'Dead';
    default:
      return state ? state[0].toUpperCase() + state.slice(1) : 'Unknown';
  }
}
