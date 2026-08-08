import { ChangeDetectionStrategy, Component, computed, inject, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '@core/auth/auth.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  adminOnly?: boolean;
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    label: 'General',
    items: [
      { path: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
      { path: '/containers', label: 'Contenedores', icon: 'containers' },
      { path: '/images', label: 'Imágenes', icon: 'images' },
    ],
  },
  {
    label: 'Network',
    items: [{ path: '/networks', label: 'Redes', icon: 'network' }],
  },
  {
    label: 'Storage',
    items: [{ path: '/volumes', label: 'Volúmenes', icon: 'volume' }],
  },
  {
    label: 'Administración',
    items: [
      { path: '/admin/users', label: 'Usuarios', icon: 'users', adminOnly: true },
      { path: '/admin/invitations', label: 'Invitaciones', icon: 'mail', adminOnly: true },
    ],
  },
];

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'complementary',
    // Display (hidden/flex per viewport) is decided by the host that embeds
    // the sidebar: static column on desktop, overlay drawer on mobile.
    class: 'w-60 shrink-0 flex-col border-r border-hairline bg-surface-1',
  },
})
export class SidebarComponent {
  private readonly auth = inject(AuthService);
  readonly year = new Date().getFullYear();
  readonly isDemo = this.auth.isDemo;

  /** Emitted on nav link click so the mobile drawer can close itself. */
  readonly navigated = output<void>();

  readonly visibleGroups = computed<NavGroup[]>(() => {
    // DEMO sees the admin sections too (read-only demo of the full app).
    const adminView = this.auth.isAdmin() || this.auth.isDemo();
    return NAV_GROUPS.map((g) => ({
      ...g,
      items: g.items.filter((i) => !i.adminOnly || adminView),
    })).filter((g) => g.items.length > 0);
  });
}
