import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from '@core/http/auth.interceptor';
import { errorInterceptor } from '@core/http/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // Order matters: errorInterceptor wraps the chain so that authInterceptor
    // sees the raw HttpErrorResponse and can react to 401 before it gets
    // converted into an ApiError for the components.
    provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
  ],
};
