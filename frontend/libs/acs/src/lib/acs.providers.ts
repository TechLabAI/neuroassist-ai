import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { ACS_TENANT_PORT } from './api/acs-tenant.port';
import { ACS_CUIDADOR_PORT } from './api/acs-cuidador.port';
import { ACS_LINK_PORT } from './api/acs-link.port';
import { AcsTenantMockAdapter } from './api/acs-tenant-mock.adapter';
import { AcsTenantHttpAdapter } from './api/acs-tenant-http.adapter';
import { AcsCuidadorMockAdapter } from './api/acs-cuidador-mock.adapter';
import { AcsCuidadorHttpAdapter } from './api/acs-cuidador-http.adapter';
import { AcsLinkMockAdapter } from './api/acs-link-mock.adapter';
import { AcsLinkHttpAdapter } from './api/acs-link-http.adapter';

// =============================================================================
// provideAcs() — factory that wires ACS ports to adapters.
// adapter: 'mock'  → dev/test (default); no backend required
// adapter: 'http'  → production; requires acs-service + GWY running
//
// Usage in app.config.ts:
//   providers: [..., provideAcs({ adapter: 'mock' })]
//
// TODO[INT-FE-ACS-001..003]: switch to 'http' once acs-service is deployed.
// =============================================================================

export interface AcsProviderConfig {
  adapter: 'mock' | 'http';
}

export function provideAcs(
  config: AcsProviderConfig = { adapter: 'mock' },
): EnvironmentProviders {
  const isMock = config.adapter !== 'http';
  return makeEnvironmentProviders([
    { provide: ACS_TENANT_PORT, useClass: isMock ? AcsTenantMockAdapter : AcsTenantHttpAdapter },
    { provide: ACS_CUIDADOR_PORT, useClass: isMock ? AcsCuidadorMockAdapter : AcsCuidadorHttpAdapter },
    { provide: ACS_LINK_PORT, useClass: isMock ? AcsLinkMockAdapter : AcsLinkHttpAdapter },
  ]);
}
