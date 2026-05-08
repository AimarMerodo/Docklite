import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';

import { ActivityEntry } from '@core/api/dashboard.service';

@Component({
  selector: 'app-activity-panel',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './activity.panel.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-col h-full rounded-lg border border-hairline bg-surface-1' },
})
export class ActivityPanel {
  readonly entries = input<ActivityEntry[]>([]);
  readonly loading = input(false);

  protected shortId(id: string): string {
    if (!id) return '';
    return id.startsWith('sha256:') ? id.slice(7, 19) : id.slice(0, 12);
  }
}
