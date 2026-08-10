import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withHashLocation } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { APP_ROUTES } from './app.routes';
import { DATA_PROVIDERS } from './core/data-access';
import { POC_HTTP_PROVIDERS } from './core/poc-http-adapters';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(APP_ROUTES, withHashLocation()),
    provideHttpClient(),
    ...DATA_PROVIDERS,        // mock adapters para todas as coleções
    ...POC_HTTP_PROVIDERS,    // sobrescreve TipoGesto + Eventos com HTTP real (último vence)
  ],
};
