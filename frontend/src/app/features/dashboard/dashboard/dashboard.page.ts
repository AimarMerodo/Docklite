import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { ContainerService, ContainerSummary } from '@core/api/container.service';
import {
  ActivityEntry,
  DashboardService,
  DashboardSummary,
} from '@core/api/dashboard.service';
import { ImageService, ImageSummary } from '@core/api/image.service';
import { SystemInfoDto, SystemService } from '@core/api/system.service';
import { AuthService } from '@core/auth/auth.service';
import { ApiError } from '@core/http/error.interceptor';
import { ActivityPanel } from '@features/dashboard/panels/activity/activity.panel';
import { ContainerStatusPanel } from '@features/dashboard/panels/container-status/container-status.panel';
import { RecentImagesPanel } from '@features/dashboard/panels/recent-images/recent-images.panel';
import { SystemInfoPanel } from '@features/dashboard/panels/system-info/system-info.panel';

interface StatCard {
  label: string;
  value: number;
  hint?: string;
  link: string;
}

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [
    RouterLink,
    ActivityPanel,
    ContainerStatusPanel,
    SystemInfoPanel,
    RecentImagesPanel,
  ],
  templateUrl: './dashboard.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage implements OnInit {
  private readonly api = inject(DashboardService);
  private readonly containerApi = inject(ContainerService);
  private readonly imageApi = inject(ImageService);
  private readonly systemApi = inject(SystemService);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly summary = signal<DashboardSummary | null>(null);
  protected readonly activity = signal<ActivityEntry[]>([]);
  protected readonly containers = signal<ContainerSummary[]>([]);
  protected readonly images = signal<ImageSummary[]>([]);
  protected readonly systemInfo = signal<SystemInfoDto | null>(null);

  protected readonly cards = (): StatCard[] => {
    const s = this.summary();
    return [
      {
        label: 'Contenedores',
        value: s?.totalContainers ?? 0,
        hint: s ? `${s.running} corriendo · ${s.stopped} parados` : '',
        link: '/containers',
      },
      { label: 'Imágenes', value: s?.totalImages ?? 0, link: '/images' },
      { label: 'Redes', value: s?.totalNetworks ?? 0, link: '/networks' },
      { label: 'Volúmenes', value: s?.totalVolumes ?? 0, link: '/volumes' },
    ];
  };

  ngOnInit(): void {
    forkJoin({
      summary: this.api.getSummary().pipe(catchError(() => of(null))),
      activity: this.api.recentActivity(5).pipe(catchError(() => of(null))),
      containers: this.containerApi.list({ all: true }).pipe(catchError(() => of([]))),
      images: this.imageApi.list().pipe(catchError(() => of([]))),
      info: this.systemApi.info().pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ summary, activity, containers, images, info }) => {
        if (summary) this.summary.set(summary);
        if (activity) this.activity.set(activity.content ?? []);
        this.containers.set(containers);
        this.images.set(images);
        this.systemInfo.set(info);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          err instanceof ApiError ? err.message : 'No se pudo cargar el resumen.',
        );
      },
    });
  }
}
