import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { AuthService } from '@core/auth/auth.service';
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
  },
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
}
