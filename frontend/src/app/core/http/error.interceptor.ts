import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

/** Shape returned by the backend's GlobalExceptionHandler. */
export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  fields?: Record<string, string>;
}

export class ApiError extends Error {
  readonly status: number;
  readonly fields: Record<string, string>;
  readonly raw: ApiErrorBody | null;

  constructor(status: number, message: string, fields: Record<string, string>, raw: ApiErrorBody | null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fields = fields;
    this.raw = raw;
  }
}

export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse)) return throwError(() => err);

      const body = (err.error ?? null) as ApiErrorBody | null;
      const message = body?.message ?? defaultMessage(err.status);
      const fields = body?.fields ?? {};
      return throwError(() => new ApiError(err.status, message, fields, body));
    }),
  );

function defaultMessage(status: number): string {
  switch (status) {
    case 0:
      return 'No se pudo contactar con el servidor.';
    case 401:
      return 'Sesión no válida o expirada.';
    case 403:
      return 'No tienes permiso para esta acción.';
    case 404:
      return 'Recurso no encontrado.';
    case 409:
      return 'Conflicto: el recurso ya existe.';
    case 423:
      return 'Cuenta bloqueada temporalmente.';
    case 500:
      return 'Error interno del servidor.';
    default:
      return `Error ${status}`;
  }
}
