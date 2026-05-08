import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type InfoTone = 'default' | 'success' | 'danger';

@Component({
  selector: 'app-info-cell',
  standalone: true,
  templateUrl: './info-cell.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-col min-w-0' },
})
export class InfoCellComponent {
  readonly label = input.required<string>();
  readonly value = input<string | number | null | undefined>('—');
  readonly mono = input(false, { transform: booleanAttribute });
  readonly truncate = input(false, { transform: booleanAttribute });
  readonly tone = input<InfoTone>('default');
}

function booleanAttribute(value: unknown): boolean {
  return value !== false && value !== null && value !== undefined && value !== '';
}
