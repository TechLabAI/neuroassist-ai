import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { NO_ARVORE_FALA_PORT } from './api/no-arvore-fala.port';
import { NoArvoreFalaMockAdapter } from './api/no-arvore-fala-mock.adapter';
import { NoArvoreFalaHttpAdapter } from './api/no-arvore-fala-http.adapter';
import { PROPOSAL_PORT } from './api/proposal.port';
import { ProposalMockAdapter } from './api/proposal-mock.adapter';

// =============================================================================
// provideConv() — wires CONV ports to adapters.
//
// adapter:
//   'mock' (default) → MockAdapters. dev/test; no backend required.
//   'http'           → HttpAdapter for NoArvoreFalaPort.
//                      ProposalPort is ALWAYS mock (CONV-INT-006 / G-2 —
//                      real endpoint belongs to future TREI module).
//
// Usage in app.config.ts:
//   providers: [..., provideConv({ adapter: 'mock' })]
//   providers: [..., provideConv({ adapter: 'http' })]
// =============================================================================

export interface ConvProviderConfig {
  /** Adapter mode for NoArvoreFalaPort. Default: 'mock'. */
  readonly adapter?: 'mock' | 'http';
}

export function provideConv(config: ConvProviderConfig = {}): EnvironmentProviders {
  const adapter = config.adapter ?? 'mock';
  return makeEnvironmentProviders([
    {
      provide: NO_ARVORE_FALA_PORT,
      useClass: adapter === 'http' ? NoArvoreFalaHttpAdapter : NoArvoreFalaMockAdapter,
    },
    {
      // ProposalPort is always Mock — HttpAdapter deferred to TREI (CONV-INT-006 / G-2).
      provide: PROPOSAL_PORT,
      useClass: ProposalMockAdapter,
    },
  ]);
}
