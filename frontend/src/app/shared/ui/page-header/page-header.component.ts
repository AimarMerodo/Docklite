import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Standard page header: title + optional description on the left, action
 * buttons projected on the right. On mobile it stacks (title above, actions
 * wrapping below) so buttons never overflow the viewport, and the
 * description is hidden to keep the header compact.
 */
@Component({
  selector: 'app-page-header',
  standalone: true,
  templateUrl: './page-header.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class:
      'flex flex-col gap-4 md:flex-row md:items-start md:justify-between md:gap-6 mb-6 md:mb-8',
  },
})
export class PageHeaderComponent {
  readonly pageTitle = input.required<string>();
  readonly description = input<string>('');
}
