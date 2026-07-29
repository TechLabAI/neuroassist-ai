import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { EVNT_CLIENT_PORT } from './api/evnt-client.port';
import { EvntClientMockAdapter } from './api/evnt-client-mock.adapter';
import { EvntClientHttpAdapter } from './api/evnt-client-http.adapter';

// =============================================================================
// provideEvnt() — factory that wires EVNT ports to adapters.
// adapter: 'mock'  → dev/test (default); no backend required
// adapter: 'http'  → production; requires evnt-service + GWY running
//
// Usage in app.config.ts:
//   providers: [..., provideEvnt({ adapter: 'mock' })]
//
// TODO[INT-FE-EVNT-002]: http adapter openStream() is a stub that immediately
// errors → facade falls back to polling (SPEC-04 §4 degradation path).
// Resolve FE-EVNT-007 (EventSource + Auth header) before switching to 'http'.
// =============================================================================

export interface EvntProviderConfig {
  adapter: 'mock' | 'http';
}

export function provideEvnt(
  config: EvntProviderConfig = { adapter: 'mock' },
): EnvironmentProviders {
  return makeEnvironmentProviders([
    {
      provide: EVNT_CLIENT_PORT,
      useClass:
        config.adapter === 'http' ? EvntClientHttpAdapter : EvntClientMockAdapter,
    },
  ]);
}
