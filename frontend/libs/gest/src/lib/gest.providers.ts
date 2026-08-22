import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { EDGE_DETECTION_PORT } from './api/edge-detection.port';
import { EdgeDetectionMockAdapter } from './api/edge-detection-mock.adapter';
import { EdgeDetectionHttpAdapter } from './api/edge-detection-http.adapter';
import { EdgeDetectionBrowserAdapter } from './api/edge-detection-browser.adapter';
import { ELIGIBILITY_PORT } from './api/eligibility.port';
import { EligibilityMockAdapter } from './api/eligibility-mock.adapter';
import { EligibilityHttpAdapter } from './api/eligibility-http.adapter';

// ---------------------------------------------------------------------------
// provideGest() — liga as portas do GEST aos adapters.
//
// detection:
//   'browser' → ADR-007: MediaPipe no browser. É o motor do produto.
//   'mock'    → dev/test; nenhum motor, veredito simulado e rotulado.
//   'bridge'  → LEGADO: ponte WS com o agente Python (edge-agent-ws.py).
//               Fora do caminho do produto desde a ADR-007; mantido só para
//               comparar motores enquanto o agente ainda existe no repo.
//
// eligibility: 'mock' | 'http' — independente do motor de detecção, porque
// elegibilidade é consulta ao gest-service, não detecção.
//
// Uso em app.config.ts:
//   providers: [..., provideGest({ detection: 'browser', eligibility: 'http' })]
// ---------------------------------------------------------------------------

export type DetectionMode = 'browser' | 'mock' | 'bridge';
export type EligibilityMode = 'mock' | 'http';

export interface GestProviderConfig {
  /** Motor de detecção. Default: 'browser' (ADR-007). */
  readonly detection?: DetectionMode;
  /** Origem dos dados de elegibilidade. Default: 'mock'. */
  readonly eligibility?: EligibilityMode;
  /** @deprecated Forma antiga: 'mock' | 'http' aplicada aos dois. Use os campos acima. */
  readonly adapter?: 'mock' | 'http';
}

const DETECTION_ADAPTERS = {
  browser: EdgeDetectionBrowserAdapter,
  mock: EdgeDetectionMockAdapter,
  bridge: EdgeDetectionHttpAdapter,
} as const;

export function provideGest(config: GestProviderConfig = {}): EnvironmentProviders {
  // Compatibilidade com a forma antiga: adapter:'http' significava a ponte WS.
  const legadoHttp = config.adapter === 'http';
  const detection: DetectionMode =
    config.detection ?? (config.adapter ? (legadoHttp ? 'bridge' : 'mock') : 'browser');
  const eligibility: EligibilityMode = config.eligibility ?? (legadoHttp ? 'http' : 'mock');

  return makeEnvironmentProviders([
    {
      provide: EDGE_DETECTION_PORT,
      useClass: DETECTION_ADAPTERS[detection],
    },
    {
      provide: ELIGIBILITY_PORT,
      useClass: eligibility === 'http' ? EligibilityHttpAdapter : EligibilityMockAdapter,
    },
  ]);
}
