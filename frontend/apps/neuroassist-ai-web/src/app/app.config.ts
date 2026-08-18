import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideGest } from '@neuroassist/gest';
import { provideEvnt } from '@neuroassist/evnt';
import { providePac } from '@neuroassist/pac';
import { provideAcs } from '@neuroassist/acs';
import { provideEdge } from '@neuroassist/edge';
import { provideHist } from '@neuroassist/hist';
import { provideDash } from '@neuroassist/dash';
import { appRoutes } from './app.routes';

// ---------------------------------------------------------------------------
// app.config.ts — root ApplicationConfig for neuroassist-ai-web.
//
// adapter: 'mock' for every module — no backend microservice is required to
// serve/e2e this app locally (see docs/architecture/backend/standards/
// api-mock-convention.md). Flip individual modules to 'http' once their
// backend + GWY route is deployed and reachable (per-module TODO in each
// provide<Module>() factory).
// ---------------------------------------------------------------------------

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(appRoutes),
    provideHttpClient(withInterceptors([])),
    provideAnimations(),
    provideGest({ adapter: 'mock' }),
    provideEvnt({ adapter: 'mock' }),
    providePac({ adapter: 'mock' }),
    provideAcs({ adapter: 'mock' }),
    provideEdge({ adapter: 'mock' }),
    provideHist({ adapter: 'mock' }),
    provideDash({ adapter: 'mock' }),
  ],
};
