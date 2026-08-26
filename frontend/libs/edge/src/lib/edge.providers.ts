import { EnvironmentProviders, isDevMode, makeEnvironmentProviders } from '@angular/core';
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
//
// Hook de E2E (Playwright addInitScript apenas; inerte em build de produção):
//   window.__EDGE_E2E_ADAPTER = 'http'
//     → força os adaptadores HTTP, ignorando a configuração da app.
//
// Por que isto existe: dezoito cenários do SPEC-08 — justamente os de erro,
// conflito e RBAC — estavam pulados por `test.skip(!EDGE_HTTP_MODE)`, e a
// variável não podia valer nada, porque `app.config.ts` fixa `adapter:'mock'` e
// não havia como trocar em runtime. O gate contava esses arquivos como
// existentes e nunca os executava. Eles NÃO precisam do edge-service no ar:
// interceptam as chamadas com `page.route`. Só precisavam que a app as EMITISSE.
// ---------------------------------------------------------------------------

export interface EdgeProviderConfig {
  adapter: 'mock' | 'http';
}

/**
 * Adaptador forçado pela suíte E2E, ou null fora dela.
 *
 * <p>`isDevMode()` é falso em build de produção e o `if` inteiro é removido pelo
 * empacotador — pelo mesmo motivo do gancho equivalente no GEST: uma global capaz
 * de trocar de onde vêm os dados do dispositivo não pode existir onde há
 * paciente.
 */
function adaptadorForcadoPorE2E(): 'http' | null {
  if (!isDevMode()) return null;
  if (typeof window === 'undefined') return null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return (window as any).__EDGE_E2E_ADAPTER === 'http' ? 'http' : null;
}

export function provideEdge(
  config: EdgeProviderConfig = { adapter: 'mock' },
): EnvironmentProviders {
  const adapter = adaptadorForcadoPorE2E() ?? config.adapter;
  return makeEnvironmentProviders([
    {
      provide: DEVICE_PORT,
      useClass:
        adapter === 'http' ? DeviceHttpAdapter : DeviceMockAdapter,
    },
    {
      provide: PAIRING_PORT,
      useClass:
        adapter === 'http' ? PairingHttpAdapter : PairingMockAdapter,
    },
  ]);
}
