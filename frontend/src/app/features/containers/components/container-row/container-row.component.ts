import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { Router } from '@angular/router';

import { ContainerSummary } from '@core/api/container.service';
import { StatusPillComponent } from '@shared/ui/status-pill/status-pill.component';

export type RowAction = 'terminal' | 'start' | 'stop' | 'restart';

@Component({
  selector: 'app-container-row',
  standalone: true,
  imports: [StatusPillComponent],
  templateUrl: './container-row.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class:
      'flex items-center gap-4 px-5 h-16 border-b border-hairline last:border-b-0 cursor-pointer hover:bg-surface-2 transition-colors',
    role: 'button',
    tabindex: '0',
    '(click)': 'goToDetail()',
    '(keydown.enter)': 'goToDetail()',
    '(keydown.space)': 'goToDetail(); $event.preventDefault()',
  },
})
export class ContainerRowComponent {
  private readonly router = inject(Router);

  readonly container = input.required<ContainerSummary>();
  readonly pending = input<RowAction | null>(null);

  readonly action = output<RowAction>();

  readonly isRunning = computed(() => this.container().state === 'running');

  readonly shortId = computed(() => {
    const id = this.container().id ?? '';
    return id.startsWith('sha256:') ? id.slice(7, 19) : id.slice(0, 12);
  });

  readonly uptime = computed(() => {
    const c = this.container();
    if (c.state !== 'running') return '—';
    const m = c.status.match(/^Up\s+(.+?)(\s*\(.*\))?$/);
    return m?.[1] ?? c.status;
  });

  protected goToDetail(): void {
    void this.router.navigate(['/containers', this.shortId()]);
  }
}
