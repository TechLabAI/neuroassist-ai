import {
  APP_INITIALIZER,
  EnvironmentInjector,
  EnvironmentProviders,
  InjectionToken,
  makeEnvironmentProviders,
  runInInjectionContext,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { EVNT_CLIENT_PORT } from './api/evnt-client.port';
import { EvntClientMockAdapter } from './api/evnt-client-mock.adapter';
import { EvntClientHttpAdapter } from './api/evnt-client-http.adapter';
import { evntHttpInterceptorFn } from './api/evnt-http.interceptor';
import { evntBootstrap } from './evnt.bootstrap';

// =============================================================================
// provideEvnt() — factory that wires EVNT ports to adapters.
// adapter: 'mock'  → dev/test (default); no backend required
// adapter: 'http'  → production; requires evnt-service + GWY running
//
// Usage in app.config.ts (dev/test):
//   providers: [..., provideEvnt({ adapter: 'mock' })]
//
// Usage in app.config.ts (production):
//   providers: [..., provideEvntForProduction()]
//
// P6-A: evntHttpInterceptorFn registered via withInterceptors([]) so the
// X-Empresa-Id header is appended to every EvntClientHttpAdapter call.
// fetch()-based SSE reads EVNT_EMPRESA_CONTEXT directly in the adapter constructor.
//
// P6-E: EVNT_E2E_BYPASS token allows e2e / mock-mode to skip auth guard checks.
//
// P9: APP_INITIALIZER runs evntBootstrap.init() so the alarm overlay hydrates
// at app startup — making the overlay visible before the router's first navigation.
// runInInjectionContext() provides the injection context needed for inject() calls
// that are synchronous within init() (before the first await).
// =============================================================================

export interface EvntProviderConfig {
  adapter: 'mock' | 'http';
}

/**
 * InjectionToken used by evntGuard to bypass GUARD-01 / GUARD-02 checks.
 * Set to true in provideEvnt({ adapter: 'mock' }) and in e2e test providers.
 * Replaces the previous class-instance check on EvntClientMockAdapter (P6-E).
 */
export const EVNT_E2E_BYPASS = new InjectionToken<boolean>('EVNT_E2E_BYPASS', {
  providedIn: 'root',
  factory: () => false,
});

export function provideEvnt(
  config: EvntProviderConfig = { adapter: 'mock' },
): EnvironmentProviders {
  const isMock = config.adapter !== 'http';
  return makeEnvironmentProviders([
    provideHttpClient(withInterceptors([evntHttpInterceptorFn])),
    {
      provide: EVNT_CLIENT_PORT,
      useClass: isMock ? EvntClientMockAdapter : EvntClientHttpAdapter,
    },
    {
      provide: EVNT_E2E_BYPASS,
      useValue: isMock,
    },
    // P9: Bootstrap alarm hydration before router makes its first navigation.
    // This ensures AlarmOverlayComponent shows the active alarm immediately after
    // app init — critical for the e2e overlay preemption scenarios (FE-EVNT-026..030).
    {
      provide: APP_INITIALIZER,
      useFactory: (injector: EnvironmentInjector) => {
        return () => runInInjectionContext(injector, () => evntBootstrap.init());
      },
      deps: [EnvironmentInjector],
      multi: true,
    },
  ]);
}

/**
 * Production-ready provider factory.
 * Equivalent to provideEvnt({ adapter: 'http' }) — exposes a named export so
 * app.config.ts can import a single symbol that communicates intent clearly.
 *
 * Usage in app.config.ts (production):
 *   providers: [..., provideEvntForProduction()]
 */
export function provideEvntForProduction(): EnvironmentProviders {
  return provideEvnt({ adapter: 'http' });
}
