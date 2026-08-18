import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { DASH_SUMMARY_PORT } from './api/dash-summary.port';
import { DASH_NOTIFICATIONS_PORT } from './api/dash-notifications.port';
import { DashSummaryMockAdapter } from './api/dash-summary-mock.adapter';
import { DashSummaryHttpAdapter } from './api/dash-summary-http.adapter';
import { DashNotificationsMockAdapter } from './api/dash-notifications-mock.adapter';
import { DashNotificationsHttpAdapter } from './api/dash-notifications-http.adapter';

// =============================================================================
// provideDash() — factory that wires DASH ports to adapters.
// adapter: 'mock'  → dev/test (default); no backend required
// adapter: 'http'  → production; requires dash-service + GWY running
//
// Usage in app.config.ts:
//   providers: [..., provideDash({ adapter: 'mock' })]
//
// TODO[INT-FE-DASH-004..005]: switch to 'http' once dash-service is deployed.
// =============================================================================

export interface DashProviderConfig {
  adapter: 'mock' | 'http';
}

export function provideDash(
  config: DashProviderConfig = { adapter: 'mock' },
): EnvironmentProviders {
  const isMock = config.adapter !== 'http';
  return makeEnvironmentProviders([
    {
      provide: DASH_SUMMARY_PORT,
      useClass: isMock ? DashSummaryMockAdapter : DashSummaryHttpAdapter,
    },
    {
      provide: DASH_NOTIFICATIONS_PORT,
      useClass: isMock ? DashNotificationsMockAdapter : DashNotificationsHttpAdapter,
    },
  ]);
}
