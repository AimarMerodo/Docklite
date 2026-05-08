import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { API_BASE_URL } from './api.config';
import { PageResponse } from './dashboard.service';

export type ContainerState =
  | 'running'
  | 'paused'
  | 'restarting'
  | 'exited'
  | 'created'
  | 'dead'
  | string;

export interface ContainerSummary {
  id: string;
  name: string;
  image: string;
  state: ContainerState;
  status: string;
}

export type RestartPolicy = 'no' | 'always' | 'on-failure' | 'unless-stopped';
export type PortProtocol = 'tcp' | 'udp';

export interface ContainerStateDetail {
  status: string;
  running: boolean;
  paused: boolean;
  restarting: boolean;
  pid: number;
  exitCode: number;
  startedAt: string;
  finishedAt: string;
  restartCount: number;
}

export interface ContainerConfig {
  command: string[];
  entrypoint: string[];
  workingDir: string;
  user: string;
  env: string[];
}

export interface ContainerHostConfig {
  memoryLimitBytes: number;
  nanoCpus: number;
  restartPolicy: string;
  networkMode: string;
}

export interface ContainerNetwork {
  name: string;
  ipAddress: string;
  gateway: string;
  macAddress: string;
}

export interface ContainerPort {
  privatePort: number;
  publicPort: number | null;
  protocol: PortProtocol | string;
}

export interface ContainerMount {
  type: string;
  source: string;
  destination: string;
  rw: boolean;
  /** Only present when type === 'volume'. */
  volumeName?: string;
}

export interface ContainerHealthcheck {
  test: string[];
  intervalNanos: number;
  timeoutNanos: number;
  retries: number;
  startPeriodNanos: number;
}

export interface ContainerDetail {
  id: string;
  name: string;
  image: string;
  imageId: string;
  createdAt: string;
  state: ContainerStateDetail;
  config: ContainerConfig;
  hostConfig: ContainerHostConfig;
  networks: ContainerNetwork[];
  ports: ContainerPort[];
  mounts: ContainerMount[];
  healthcheck: ContainerHealthcheck | null;
}

export interface VolumeMountInput {
  volumeName: string;
  containerPath: string;
  readOnly: boolean;
}

export interface EnvVarInput {
  name: string;
  value: string;
}

export interface PortMappingInput {
  hostPort: number;
  containerPort: number;
  protocol: PortProtocol;
}

export interface PortInUse {
  hostPort: number;
  protocol: PortProtocol | string;
  containerName: string;
}

export interface CreateContainerRequest {
  image: string;
  name?: string;
  autoStart: boolean;
  networkId?: string;
  volumes?: VolumeMountInput[];
  env?: EnvVarInput[];
  ports?: PortMappingInput[];
  restartPolicy?: RestartPolicy;
  memoryMb?: number;
  cpus?: number;
}

@Injectable({ providedIn: 'root' })
export class ContainerService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  list(opts: { all?: boolean } = {}): Observable<ContainerSummary[]> {
    const params = new HttpParams().set('all', String(opts.all ?? true));
    return this.http
      .get<PageResponse<ContainerSummary> | ContainerSummary[]>(
        `${this.base}/containers`,
        { params },
      )
      .pipe(map(unwrap));
  }

  /** Host ports currently bound by any container on the machine. */
  portsInUse(): Observable<PortInUse[]> {
    return this.http.get<PortInUse[]>(`${this.base}/containers/ports-in-use`);
  }

  start(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/containers/${id}/start`, {});
  }

  stop(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/containers/${id}/stop`, {});
  }

  restart(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/containers/${id}/restart`, {});
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/containers/${id}`);
  }

  create(payload: CreateContainerRequest): Observable<ContainerSummary> {
    return this.http.post<ContainerSummary>(`${this.base}/containers`, payload);
  }

  get(id: string): Observable<ContainerDetail> {
    return this.http.get<ContainerDetail>(`${this.base}/containers/${id}`);
  }

  logs(id: string, tail = 200): Observable<string> {
    const params = new HttpParams().set('tail', tail);
    return this.http.get(`${this.base}/containers/${id}/logs`, {
      params,
      responseType: 'text',
    });
  }

  updateMounts(id: string, mounts: VolumeMountInput[]): Observable<ContainerDetail> {
    return this.http.put<ContainerDetail>(`${this.base}/containers/${id}/mounts`, {
      mounts,
    });
  }

  updatePorts(id: string, ports: PortMappingInput[]): Observable<ContainerDetail> {
    return this.http.put<ContainerDetail>(`${this.base}/containers/${id}/ports`, {
      ports,
    });
  }

  updateEnv(id: string, env: EnvVarInput[]): Observable<ContainerDetail> {
    return this.http.put<ContainerDetail>(`${this.base}/containers/${id}/env`, { env });
  }

  updateCommand(
    id: string,
    payload: { entrypoint?: string[] | null; command?: string[] | null },
  ): Observable<ContainerDetail> {
    return this.http.put<ContainerDetail>(`${this.base}/containers/${id}/command`, payload);
  }

  updateHealthcheck(
    id: string,
    payload: HealthcheckInput,
  ): Observable<ContainerDetail> {
    return this.http.put<ContainerDetail>(`${this.base}/containers/${id}/healthcheck`, payload);
  }

  /** Live for memoryMb/cpus, recreate for restartPolicy (requires stopped). */
  updateResources(
    id: string,
    payload: { memoryMb?: number | null; cpus?: number | null; restartPolicy?: RestartPolicy | null },
  ): Observable<ContainerDetail> {
    return this.http.patch<ContainerDetail>(`${this.base}/containers/${id}/resources`, payload);
  }

  rename(id: string, name: string): Observable<ContainerDetail> {
    return this.http.post<ContainerDetail>(`${this.base}/containers/${id}/rename`, { name });
  }

  stats(id: string): Observable<ContainerStats> {
    return this.http.get<ContainerStats>(`${this.base}/containers/${id}/stats`);
  }
}

export interface ContainerStats {
  id: string;
  timestamp: string;
  cpuPercent: number | null;
  memoryUsageBytes: number | null;
  memoryLimitBytes: number | null;
  memoryPercent: number | null;
}

export interface HealthcheckInput {
  /** Empty array disables the healthcheck. */
  test: string[];
  intervalSeconds?: number | null;
  timeoutSeconds?: number | null;
  retries?: number | null;
  startPeriodSeconds?: number | null;
}

function unwrap<T>(data: PageResponse<T> | T[]): T[] {
  return Array.isArray(data) ? data : (data?.content ?? []);
}
