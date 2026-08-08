import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { SidebarComponent } from '@shared/layout/sidebar/sidebar.component';
import { TopbarComponent } from '@shared/layout/topbar/topbar.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, TopbarComponent],
  templateUrl: './shell.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'flex h-dvh w-full bg-canvas text-ink overflow-hidden',
    '(document:keydown.escape)': 'mobileNavOpen.set(false)',
  },
})
export class ShellComponent {
  protected readonly mobileNavOpen = signal(false);
}
