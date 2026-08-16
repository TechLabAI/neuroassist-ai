import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideGest } from '@neuroassist/gest';
import { provideEvnt } from '@neuroassist/evnt';
import { providePac } from '@neuroassist/pac';
import { provideAcs, acsHttpInterceptorFn } from '@neuroassist/acs';
import { provideEdge } from '@neuroassist/edge';
import { appRoutes } from './app.routes';

// ---------------------------------------------------------------------------
// app.config.ts — root ApplicationConfig for neuroassist-ai-web.
//
// adapter: 'mock' for every module — no backend microservice is required to
// serve/e2e this app locally (see docs/architecture/backend/standards/
// api-mock-convention.md). Flip individual modules to 'http' once their
// backend + GWY route is deployed and reachable (per-module TODO in each
// provide<Module>() factory).
//
// acsHttpInterceptorFn: ACS-specific HTTP interceptor that adds X-Empresa-Id
//   header to ACS requests once a tenant has been provisioned (INT-FE-ACS-001).
//   Tenant provisioning routes are excluded via SKIP_EMPRESA_ID_HEADER token.
// ---------------------------------------------------------------------------

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(appRoutes),
    provideHttpClient(withInterceptors([acsHttpInterceptorFn])),
    provideAnimations(),
    provideGest({ adapter: 'mock' }),
    provideEvnt({ adapter: 'mock' }),
    providePac({ adapter: 'mock' }),
    provideAcs({ adapter: 'mock' }),
    provideEdge({ adapter: 'mock' }),
  ],
};
