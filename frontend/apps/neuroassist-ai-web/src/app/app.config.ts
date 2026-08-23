import { ApplicationConfig, inject, provideZoneChangeDetection, signal } from '@angular/core';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideGest, GEST_SERVICE_BASE_URL } from '@neuroassist/gest';
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
import { AcsEmpresaContextService } from '@neuroassist/acs';
import { accessHttpInterceptor, EMPRESA_ID_TOKEN } from '@neuroassist/shared/access-http-base';
import { appRoutes } from './app.routes';
import { APP_ADAPTER_MODE } from './config/adapter-mode';
import { CURRENT_ADAPTER_MODE } from './config/adapter-mode-env';

// ---------------------------------------------------------------------------
// app.config.ts — root ApplicationConfig for neuroassist-ai-web.
//
// Adapter mode (P2 FE-E2E-006):
//   CURRENT_ADAPTER_MODE is resolved at build time via fileReplacements:
//     'mock'   — default; no backend required (CI, PR checks, local dev)
//     'http'   — integrated profile; requires docker-compose stack on :18080
//   In both modes app.config.ts is unchanged — only adapter-mode-env.ts is swapped.
//
// Interceptor chain (P1 FE-E2E-002):
//   accessHttpInterceptor  — global: Authorization, X-Empresa-Id, X-Correlation-Id,
//                            Idempotency-Key, 401-refresh, ProblemDetail mapping.
//   acsHttpInterceptorFn   — ACS-local X-Empresa-Id (kept until accessHttpInterceptor
//                            fully supersedes it in P2 validation cycle).
//
// EMPRESA_ID_TOKEN (P1 FE-E2E-002):
//   Wires accessHttpInterceptor to AcsEmpresaContextService.empresaId signal.
//   ACS is the sole writer of the active tenant context (INT-FE-ACS-003).
//
// GEST_SERVICE_BASE_URL override (P2 FE-E2E-004):
//   In 'http' mode, overrides the factory default (absolute :8084) with the
//   GWY-relative base '/api/v1/gest'. Only active when CURRENT_ADAPTER_MODE='http'.
//   EDGE_BRIDGE_WS_URL is NOT overridden — it is localhost-only, never via GWY.
//
// DASH cross-module token safe defaults (INT-FE-DASH-001 / INT-FE-DASH-002):
//   DASH_ACTIVE_EVENT_TOKEN — safe default signal(null); override when EVNT FE scaffolded.
//   DASH_DEVICE_BADGE_TOKEN — safe default signal(null); override when EDGE FE scaffolded.
// ---------------------------------------------------------------------------

const isHttp = CURRENT_ADAPTER_MODE === 'http';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(appRoutes),
    provideHttpClient(
      withInterceptors([accessHttpInterceptor, acsHttpInterceptorFn]),
    ),
    provideAnimations(),
    // P2 FE-E2E-006: expose build-time adapter mode as injectable token.
    { provide: APP_ADAPTER_MODE, useValue: CURRENT_ADAPTER_MODE },
    // P1 FE-E2E-002: wire EMPRESA_ID_TOKEN to AcsEmpresaContextService.empresaId signal.
    // ACS is the sole writer of the active tenant id (ACS-003 / INT-FE-ACS-003).
    {
      provide: EMPRESA_ID_TOKEN,
      useFactory: () => inject(AcsEmpresaContextService).empresaId,
    },
    // P2 FE-E2E-004: override GEST base URL in http mode so eligibility calls
    // route through the proxy (/api/v1/gest/eligibility) instead of :8084.
    // MUST appear before provideGest() so the adapter picks up the overridden value.
    ...(isHttp ? [{ provide: GEST_SERVICE_BASE_URL, useValue: '/api/v1/gest' }] : []),
    provideGest({ adapter: CURRENT_ADAPTER_MODE }),
    provideEvnt({ adapter: CURRENT_ADAPTER_MODE }),
    providePac({ adapter: CURRENT_ADAPTER_MODE }),
    provideAcs({ adapter: CURRENT_ADAPTER_MODE }),
    provideEdge({ adapter: CURRENT_ADAPTER_MODE }),
    provideHist({ adapter: CURRENT_ADAPTER_MODE }),
    provideDash({ adapter: CURRENT_ADAPTER_MODE }),
    // Safe defaults until EVNT / EDGE FE modules export their signals.
    // When ready: replace useValue: signal(null) with the real signal from
    // the respective module (e.g. inject(EvntFacade).activeEvent).
    { provide: DASH_ACTIVE_EVENT_TOKEN, useValue: signal(null) },
    { provide: DASH_DEVICE_BADGE_TOKEN, useValue: signal(null) },
  ],
};
