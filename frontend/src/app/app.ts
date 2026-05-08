import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { ThemeService } from '@core/theme/theme.service';
import { AuthService } from '@core/auth/auth.service';
import { ConfirmDialogComponent } from '@shared/ui/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ConfirmDialogComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App implements OnInit {
  private readonly theme = inject(ThemeService);
  private readonly auth = inject(AuthService);

  ngOnInit(): void {
    this.theme.init();
    this.auth.bootstrapFromStorage();
  }
}
