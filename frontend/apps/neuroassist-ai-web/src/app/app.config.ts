import { ApplicationConfig, provideZoneChangeDetection, signal } from '@angular/core';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideGest } from '@neuroassist/gest';
import { provideEvnt } from '@neuroassist/evnt';
import { providePac } from '@neuroassist/pac';
import { provideAcs, acsHttpInterceptorFn } from '@neuroassist/acs';
import { provideEdge } from '@neuroassist/edge';
import { provideHist } from '@neuroassist/hist';
import {
  provideDash,
  DASH_ACTIVE_EVENT_TOKEN,
  DASH_DEVICE_BADGE_TOKEN,
} from '@neuroassist/dash';
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
//
// DASH cross-module token safe defaults (INT-FE-DASH-001 / INT-FE-DASH-002):
//   DASH_ACTIVE_EVENT_TOKEN — safe default signal(null); override when EVNT FE scaffolded.
//   DASH_DEVICE_BADGE_TOKEN — safe default signal(null); override when EDGE FE scaffolded.
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
    provideHist({ adapter: 'mock' }),
    provideDash({ adapter: 'mock' }),
    // Safe defaults until EVNT / EDGE FE modules export their signals.
    // When ready: replace useValue: signal(null) with the real signal from
    // the respective module (e.g. inject(EvntFacade).activeEvent).
    { provide: DASH_ACTIVE_EVENT_TOKEN, useValue: signal(null) },
    { provide: DASH_DEVICE_BADGE_TOKEN, useValue: signal(null) },
  ],
};
