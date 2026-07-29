import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { DEVICE_PORT } from './api/device.port';
import { DeviceMockAdapter } from './api/device-mock.adapter';
import { DeviceHttpAdapter } from './api/device-http.adapter';
import { PAIRING_PORT } from './api/pairing.port';
import { PairingMockAdapter } from './api/pairing-mock.adapter';
import { PairingHttpAdapter } from './api/pairing-http.adapter';

// ---------------------------------------------------------------------------
// provideEdge() — factory that wires EDGE ports to adapters.
// adapter: 'mock'  → dev/test (default); no backend required
// adapter: 'http'  → production; requires edge-service running (Layer 2)
//
// Usage in app.config.ts:
//   providers: [..., provideEdge({ adapter: 'mock' })]
//
// TODO[INT-FE-EDGE-001]: switch to 'http' when edge-service is available
// ---------------------------------------------------------------------------

export interface EdgeProviderConfig {
  adapter: 'mock' | 'http';
}

export function provideEdge(
  config: EdgeProviderConfig = { adapter: 'mock' },
): EnvironmentProviders {
  return makeEnvironmentProviders([
    {
      provide: DEVICE_PORT,
      useClass:
        config.adapter === 'http' ? DeviceHttpAdapter : DeviceMockAdapter,
    },
    {
      provide: PAIRING_PORT,
      useClass:
        config.adapter === 'http' ? PairingHttpAdapter : PairingMockAdapter,
    },
  ]);
}
