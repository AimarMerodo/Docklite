import { HttpErrorResponse, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, EMPTY, switchMap, throwError } from 'rxjs';

import { AuthService } from '@core/auth/auth.service';

const REFRESH_PATHS = ['/auth/login', '/auth/refresh', '/auth/logout', '/invitations/'];

function isPublic(url: string): boolean {
  return REFRESH_PATHS.some((p) => url.includes(p));
}

function withBearer(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.accessToken();

  const outgoing = !isPublic(req.url) && token ? withBearer(req, token) : req;

  return next(outgoing).pipe(
    catchError((err: unknown) => {
      const status = err instanceof HttpErrorResponse ? err.status : 0;

      // 401 on a protected request with an active session → try refresh once.
      const canRetry = status === 401 && !isPublic(req.url) && auth.session() !== null;
      if (canRetry) {
        return auth.refresh().pipe(
          switchMap((session) => next(withBearer(req, session.accessToken))),
          // Refresh failed → user has been kicked to /login by AuthService.
          // Swallow the error so the dying page doesn't flash "Authentication required".
          catchError(() => EMPTY),
        );
      }

      // 401 on a protected request when there's no session means we're already
      // logged out (or never were). Same idea: don't surface noise to the UI.
      if (status === 401 && !isPublic(req.url)) {
        return EMPTY;
      }

      return throwError(() => err);
    }),
  );
};

// Re-export the next handler type to satisfy the file's strict imports.
export type { HttpHandlerFn };
