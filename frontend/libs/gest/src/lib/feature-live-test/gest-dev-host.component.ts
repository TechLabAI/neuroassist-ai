import { Component, ChangeDetectionStrategy, signal } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { ComandoDraft, GestureTestOutcome, ENGINE_FEATURE } from '@neuroassist/shared/contracts';
import { GestureLiveTestComponent } from './gesture-live-test.component';

// ---------------------------------------------------------------------------
// GestDevHostComponent — thin /gest route (dev/e2e only; showInNav:false).
// Not a production surface — PAC calibracao and onboarding compose
// GestureLiveTestComponent via GestShellIntegrationService instead. This host
// exists so the live-test surface can be exercised standalone (`nx serve
// gest`, Playwright specs) without the full PAC command-editor context.
//
// E2E window flags (read once at construction — set via page.addInitScript before bootstrap):
//   window.__GEST_E2E_TEMPO_MAX  — override tempoMaximoGestoMs for fast timeout tests
//   window.__GEST_E2E_COMPOSITE  — enable composite draft (2-step gesture flow)
// ---------------------------------------------------------------------------

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const _w = typeof window !== 'undefined' ? (window as any) : null;

@Component({
  selector: 'gest-dev-host',
  standalone: true,
  imports: [GestureLiveTestComponent, JsonPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-lg p-6">
      <h1 class="mb-4 text-lg font-semibold">GEST — dev host (live-test harness)</h1>
      <gest-gesture-live-test [draft]="draft()" (outcome)="onOutcome($event)" />
      @if (lastOutcome(); as o) {
        <pre class="mt-4 rounded bg-surface-2 p-3 text-xs">{{ o | json }}</pre>
      }
    </div>
  `,
})
export class GestDevHostComponent {
  protected readonly draft = signal<ComandoDraft>({
    commandDraftRef: 'draft-dev-host',
    rotulo: 'água',
    // O gesto secundário era `tg-fechar-punho-001` — fechar a mão, que motor
    // nenhum deste projeto detecta (o FaceLandmarker vê rosto, não mão). O host
    // de desenvolvimento é onde se confere o comportamento real, então usar um
    // gesto fantasma aqui garantia que o caminho composto nunca fosse exercido
    // de verdade.
    primitivoPrincipal: ENGINE_FEATURE.blinkDouble,
    primitivoSecundario: (_w?.__GEST_E2E_COMPOSITE as boolean | undefined)
      ? ENGINE_FEATURE.browRaiseBoth
      : null,
    engineFeatureRefPrincipal: ENGINE_FEATURE.blinkDouble,
    engineFeatureRefSecundario: (_w?.__GEST_E2E_COMPOSITE as boolean | undefined)
      ? ENGINE_FEATURE.browRaiseBoth
      : null,
    sensibilidade: 'media',
    urgencia: 'rotina',
    tempoMinimoGestoMs: 400,
    tempoMaximoGestoMs: (_w?.__GEST_E2E_TEMPO_MAX as number | undefined) ?? 8000,
    composite: (_w?.__GEST_E2E_COMPOSITE as boolean | undefined) ?? false,
  });

  protected readonly lastOutcome = signal<GestureTestOutcome | null>(null);

  protected onOutcome(outcome: GestureTestOutcome): void {
    this.lastOutcome.set(outcome);
  }
}
