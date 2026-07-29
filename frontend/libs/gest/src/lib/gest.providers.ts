import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { EDGE_DETECTION_PORT } from './api/edge-detection.port';
import { EdgeDetectionMockAdapter } from './api/edge-detection-mock.adapter';
import { EdgeDetectionHttpAdapter } from './api/edge-detection-http.adapter';
import { ELIGIBILITY_PORT } from './api/eligibility.port';
import { EligibilityMockAdapter } from './api/eligibility-mock.adapter';
import { EligibilityHttpAdapter } from './api/eligibility-http.adapter';

// ---------------------------------------------------------------------------
// provideGest() — factory that wires GEST ports to adapters.
// adapter: 'mock'  → dev/test (default); no backend required
// adapter: 'http'  → production; requires gest-service running
//
// Usage in app.config.ts:
//   providers: [..., provideGest({ adapter: 'mock' })]
//
// TODO[GEST-INT-004]: isolate bridge token provider here when the mTLS vs
// OAuth2 decision is finalised with the EDGE team (bridgeToken InjectionToken).
// ---------------------------------------------------------------------------

export interface GestProviderConfig {
  adapter: 'mock' | 'http';
}

export function provideGest(
  config: GestProviderConfig = { adapter: 'mock' },
): EnvironmentProviders {
  return makeEnvironmentProviders([
    {
      provide: EDGE_DETECTION_PORT,
      useClass:
        config.adapter === 'http'
          ? EdgeDetectionHttpAdapter
          : EdgeDetectionMockAdapter,
    },
    {
      provide: ELIGIBILITY_PORT,
      useClass:
        config.adapter === 'http'
          ? EligibilityHttpAdapter
          : EligibilityMockAdapter,
    },
  ]);
}
