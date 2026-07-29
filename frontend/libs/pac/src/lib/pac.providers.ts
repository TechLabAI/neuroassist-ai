import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { PAC_PACIENTE_PORT } from './api/paciente.port';
import { PacienteMockAdapter } from './api/paciente-mock.adapter';
import { PacienteHttpAdapter } from './api/paciente-http.adapter';
import { PAC_COMANDO_PORT } from './api/comando.port';
import { ComandoMockAdapter } from './api/comando-mock.adapter';
import { ComandoHttpAdapter } from './api/comando-http.adapter';
import { TIPOS_GESTO_READ_PORT } from './api/tipos-gesto-read.port';
import { TiposGestoReadMockAdapter } from './api/tipos-gesto-read-mock.adapter';
import { TiposGestoReadHttpAdapter } from './api/tipos-gesto-read-http.adapter';
import { EdgeDeviceAckStubAdapter } from './api/edge-device-ack-stub.adapter';
import { PacPacienteFacade } from './pac-paciente.facade';
import { PacComandoFacade } from './pac-comando.facade';
import { TiposGestoReadFacade } from './tipos-gesto-read.facade';

// ---------------------------------------------------------------------------
// providePac() — factory that wires PAC ports to adapters.
// adapter: 'mock'  → dev/test (default); no backend required
// adapter: 'http'  → production; requires pac-service + gest-service via GWY
//
// Usage in app.config.ts:
//   providers: [..., providePac({ adapter: 'mock' })]
//
// TODO[INT-FE-PAC-001]: EdgeDeviceAckAdapter (real) replaces EdgeDeviceAckStubAdapter
// when the EDGE heartbeat contract defines the ack signal (ADR-005 gap).
// ---------------------------------------------------------------------------

export interface PacProviderConfig {
  adapter: 'mock' | 'http';
}

export function providePac(
  config: PacProviderConfig = { adapter: 'mock' },
): EnvironmentProviders {
  return makeEnvironmentProviders([
    // Port → Adapter bindings
    {
      provide: PAC_PACIENTE_PORT,
      useClass: config.adapter === 'http' ? PacienteHttpAdapter : PacienteMockAdapter,
    },
    {
      provide: PAC_COMANDO_PORT,
      useClass: config.adapter === 'http' ? ComandoHttpAdapter : ComandoMockAdapter,
    },
    {
      provide: TIPOS_GESTO_READ_PORT,
      useClass:
        config.adapter === 'http' ? TiposGestoReadHttpAdapter : TiposGestoReadMockAdapter,
    },
    // ADR-005 stub — always pending-sync until INT-FE-PAC-001 is resolved
    EdgeDeviceAckStubAdapter,
    // Facades (component-injectable)
    PacPacienteFacade,
    PacComandoFacade,
    TiposGestoReadFacade,
  ]);
}
