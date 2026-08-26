import { EnvironmentProviders, isDevMode, makeEnvironmentProviders } from '@angular/core';
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

/**
 * Adaptador forçado pela suíte E2E, ou null fora dela.
 *
 * <p>Hook de Playwright (`page.addInitScript`), réplica do que o EDGE já usa em
 * `edge.providers.ts`:
 *
 * <pre>window.__PAC_E2E_ADAPTER = 'http'</pre>
 *
 * <p>POR QUE EXISTE: sete cenários do PAC — justamente os de carregamento, erro,
 * lista vazia e validação de campo — estavam pulados por BLOCKER-4, e o bloqueio
 * era real mas não intransponível: `page.route()` só intercepta HTTP, e
 * `app.config.ts` fixa `adapter:'mock'`, que não emite chamada nenhuma. Eles NÃO
 * precisam do pac-service no ar; precisam que a app EMITA a chamada para o
 * intercept pegar. O gate contava esses arquivos como existentes e nunca os
 * executava.
 *
 * <p>`isDevMode()` é falso em build de produção e o `if` inteiro é removido pelo
 * empacotador — uma global capaz de trocar de onde vêm os dados do PACIENTE não
 * pode existir onde há paciente.
 */
function adaptadorForcadoPorE2E(): 'http' | null {
  if (!isDevMode()) return null;
  if (typeof window === 'undefined') return null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return (window as any).__PAC_E2E_ADAPTER === 'http' ? 'http' : null;
}

export function providePac(
  config: PacProviderConfig = { adapter: 'mock' },
): EnvironmentProviders {
  const adapter = adaptadorForcadoPorE2E() ?? config.adapter;
  return makeEnvironmentProviders([
    // Port → Adapter bindings
    {
      provide: PAC_PACIENTE_PORT,
      useClass: adapter === 'http' ? PacienteHttpAdapter : PacienteMockAdapter,
    },
    {
      provide: PAC_COMANDO_PORT,
      useClass: adapter === 'http' ? ComandoHttpAdapter : ComandoMockAdapter,
    },
    {
      provide: TIPOS_GESTO_READ_PORT,
      useClass:
        adapter === 'http' ? TiposGestoReadHttpAdapter : TiposGestoReadMockAdapter,
    },
    // ADR-005 stub — always pending-sync until INT-FE-PAC-001 is resolved
    EdgeDeviceAckStubAdapter,
    // Facades (component-injectable)
    PacPacienteFacade,
    PacComandoFacade,
    TiposGestoReadFacade,
  ]);
}
