import { ChangeDetectionStrategy, Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '@core/auth/auth.service';
import { ThemeService } from '@core/theme/theme.service';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './topbar.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block shrink-0' },
})
export class TopbarComponent {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  private readonly host = inject(ElementRef<HTMLElement>);

  protected readonly menuOpen = signal(false);

  protected readonly initial = (): string =>
    (this.auth.username() ?? '?').slice(0, 1).toUpperCase();

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.menuOpen()) return;
    const target = event.target as Node;
    if (!this.host.nativeElement.contains(target)) {
      this.menuOpen.set(false);
    }
  }

  protected logout(): void {
    this.menuOpen.set(false);
    void this.auth.logout();
  }
}
