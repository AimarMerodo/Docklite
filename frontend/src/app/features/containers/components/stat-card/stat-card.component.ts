import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type StatVariant = 'accent' | 'success' | 'muted' | 'neutral';

@Component({
  selector: 'app-stat-card',
  standalone: true,
  templateUrl: './stat-card.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class:
      'flex flex-col gap-2 md:gap-3 rounded-lg border border-hairline bg-surface-1 px-4 py-3 md:px-5 md:py-4',
  },
})
export class StatCardComponent {
  readonly label = input.required<string>();
  readonly value = input<number | string>(0);
  readonly variant = input<StatVariant>('neutral');

  readonly labelClasses = computed(() => {
    switch (this.variant()) {
      case 'success':
        return 'text-success';
      default:
        return 'text-ink-tertiary';
    }
  });

  readonly iconClasses = computed(() => {
    switch (this.variant()) {
      case 'accent':
        return 'text-primary';
      case 'success':
        return 'text-success';
      default:
        return 'text-ink-tertiary';
    }
  });

  readonly valueClasses = computed(() => {
    switch (this.variant()) {
      case 'success':
        return 'text-success';
      case 'muted':
        return 'text-ink-muted';
      default:
        return 'text-ink';
    }
  });
}
