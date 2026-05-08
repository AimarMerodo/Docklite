import { Routes } from '@angular/router';

import { authGuard } from '@core/auth/auth.guard';
import { roleGuard } from '@core/auth/role.guard';
import { guestGuard } from '@core/auth/guest.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('@features/auth/login/login.page').then((m) => m.LoginPage),
    title: 'DockLite | Iniciar sesión',
  },
  {
    path: 'invite/:token',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('@features/auth/accept-invite/accept-invite.page').then((m) => m.AcceptInvitePage),
    title: 'DockLite | Aceptar invitación',
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('@shared/layout/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('@features/dashboard/dashboard/dashboard.page').then((m) => m.DashboardPage),
        title: 'DockLite | Dashboard',
      },
      {
        path: 'containers',
        loadComponent: () =>
          import('@features/containers/containers/containers.page').then(
            (m) => m.ContainersPage,
          ),
        title: 'DockLite | Contenedores',
      },
      {
        path: 'containers/:id',
        loadComponent: () =>
          import('@features/containers/container-detail/container-detail.page').then(
            (m) => m.ContainerDetailPage,
          ),
        // The container detail page sets a per-container title (with the
        // container name) once it loads — see ContainerDetailPage.load().
        title: 'DockLite | Contenedor',
      },
      {
        path: 'images',
        loadComponent: () =>
          import('@features/images/images/images.page').then((m) => m.ImagesPage),
        title: 'DockLite | Imágenes',
      },
      {
        path: 'networks',
        loadComponent: () =>
          import('@features/networks/networks/networks.page').then((m) => m.NetworksPage),
        title: 'DockLite | Redes',
      },
      {
        path: 'volumes',
        loadComponent: () =>
          import('@features/volumes/volumes/volumes.page').then((m) => m.VolumesPage),
        title: 'DockLite | Volúmenes',
      },
      {
        path: 'me',
        loadComponent: () =>
          import('@features/me/me/me.page').then((m) => m.MePage),
        title: 'DockLite | Mi perfil',
      },
      {
        path: 'admin/users',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('@features/admin/users/users.page').then((m) => m.AdminUsersPage),
        title: 'DockLite | Usuarios',
      },
      {
        path: 'admin/invitations',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('@features/admin/invitations/invitations.page').then((m) => m.AdminInvitationsPage),
        title: 'DockLite | Invitaciones',
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
