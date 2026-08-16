import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { HIST_CLIENT_PORT } from './api/hist-client.port';
import { HIST_FEEDBACK_PORT } from './api/hist-feedback.port';
import { HistClientMockAdapter } from './api/hist-client-mock.adapter';
import { HistClientHttpAdapter } from './api/hist-client-http.adapter';
import { HistFeedbackMockAdapter } from './api/hist-feedback-mock.adapter';
import { HistFeedbackHttpAdapter } from './api/hist-feedback-http.adapter';

// =============================================================================
// provideHist() — factory that wires HIST ports to adapters.
// adapter: 'mock'  → dev/test (default); no backend required
// adapter: 'http'  → production; requires hist-service + GWY + evnt-service running
//
// Usage in app.config.ts:
//   providers: [..., provideHist({ adapter: 'mock' })]
//
// TODO[INT-FE-HIST-001..006]: switch to 'http' once services are deployed.
// =============================================================================

export interface HistProviderConfig {
  adapter: 'mock' | 'http';
}

export function provideHist(
  config: HistProviderConfig = { adapter: 'mock' },
): EnvironmentProviders {
  const isMock = config.adapter !== 'http';
  return makeEnvironmentProviders([
    {
      provide: HIST_CLIENT_PORT,
      useClass: isMock ? HistClientMockAdapter : HistClientHttpAdapter,
    },
    {
      provide: HIST_FEEDBACK_PORT,
      // Feedback always targets evnt-service (non-optimistic, G1 resolved).
      // Both adapters implement the same HistFeedbackPort interface.
      useClass: isMock ? HistFeedbackMockAdapter : HistFeedbackHttpAdapter,
    },
  ]);
}
