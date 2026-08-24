import { EnvironmentProviders, isDevMode, makeEnvironmentProviders } from '@angular/core';
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
//
// Hook de E2E (Playwright addInitScript apenas; inerte em build de produção,
// por `isDevMode()` — ver a função abaixo):
//   window.__GEST_E2E_DETECTION = 'mock'
//     → força o motor simulado, ignorando a configuração da app.
//
// Por que isto existe: desde a ADR-007 a app roda `detection:'browser'`
// (MediaPipe sobre os frames da webcam). Numa suíte Playwright a webcam é um
// MediaStream vazio, então o motor real nunca produz gesto nenhum — o teste
// fica preso em 'testing' para sempre. Os cenários que exercitam a MÁQUINA DE
// ESTADOS do facade (reconhecido, composto, timeout, erro de stream) precisam
// de um motor determinístico, e é exatamente para isso que o
// EdgeDetectionMockAdapter existe. Sem este hook não havia como alcançá-lo: o
// GestureTestFacade é `providedIn:'root'`, logo um override de porta no nível
// da rota /gest não o atingiria — a troca tem de acontecer aqui, na raiz.
//
// O hook só aceita 'mock', e o mock só emite 'recognized' se um segundo flag
// de E2E for ligado — a invariante do degradado honesto (ADR-005) continua de
// pé mesmo com o flag ativado por engano.
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

/**
 * Motor forçado pela suíte E2E, ou null fora dela. Lido uma vez, no bootstrap:
 * o addInitScript do Playwright roda antes de qualquer código da app.
 *
 * <p><b>Por que o `isDevMode()` é obrigatório aqui.</b> Este gancho troca o MOTOR
 * DE DETECÇÃO DE GESTO — num produto em que o gesto é o único canal de uma pessoa
 * que perdeu a fala. Sem a guarda, qualquer script capaz de escrever uma variável
 * global no navegador desligaria a detecção real, e o sintoma seria o pior
 * possível: nada acontece. O paciente faz o gesto e ninguém é avisado.
 *
 * <p>`isDevMode()` é falso em build de produção, e o `if` inteiro é removido pelo
 * empacotador. O gancho existe onde o E2E roda (`nx serve`, build de
 * desenvolvimento) e não existe onde há paciente.
 */
function motorForcadoPorE2E(): DetectionMode | null {
  if (!isDevMode()) return null;
  if (typeof window === 'undefined') return null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return (window as any).__GEST_E2E_DETECTION === 'mock' ? 'mock' : null;
}

export function provideGest(config: GestProviderConfig = {}): EnvironmentProviders {
  // Compatibilidade com a forma antiga: adapter:'http' significava a ponte WS.
  const legadoHttp = config.adapter === 'http';
  const detection: DetectionMode =
    motorForcadoPorE2E() ??
    config.detection ??
    (config.adapter ? (legadoHttp ? 'bridge' : 'mock') : 'browser');
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
