import {
  Component,
  ChangeDetectionStrategy,
  signal,
} from '@angular/core';
import { GestureLiveTestComponent } from './gesture-live-test.component';
import { ComandoDraft } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// GestDevHostComponent — dev/e2e harness for the thin /gest host route.
// NOT a production screen. Provides a default ComandoDraft for standalone
// development and Playwright e2e testing without the PAC module.
// Thin host only — GEST surface in production is always embedded via
// GestShellIntegrationService (SPEC-06 §1, GEST-INT-001).
// ---------------------------------------------------------------------------

const DEV_DRAFT: ComandoDraft = {
  commandDraftRef: 'dev-draft-001',
  rotulo: 'Piscar uma vez (teste)',
  primitivoPrincipal: 'blink-left',
  primitivoSecundario: null,
  sensibilidade: 'media',
  urgencia: 'rotina',
  tempoMinimoGestoMs: 400,
  tempoMaximoGestoMs: 3000,
  composite: false,
};

@Component({
  selector: 'gest-dev-host',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [GestureLiveTestComponent],
  template: `
    <div class="mx-auto max-w-2xl p-8">
      <header class="mb-6">
        <h1 class="text-xl font-bold text-ink">GEST — Dev Host</h1>
        <p class="mt-1 text-sm text-ink-soft">
          Harness de desenvolvimento para o painel de teste ao vivo.
          Em produção este componente é embutido em Calibração / Onboarding.
        </p>
      </header>

      <gest-live-test
        [commandDraft]="draft()"
        (testCompleted)="onTestCompleted($event)"
      />

      @if (lastOutcome()) {
        <div class="mt-4 rounded-lg bg-surface-2 p-4 text-xs text-ink-soft">
          <strong>Último resultado:</strong>
          recognized={{ lastOutcome()!.recognized }},
          degraded={{ lastOutcome()!.degraded }},
          composite={{ lastOutcome()!.composite }}
        </div>
      }
    </div>
  `,
})
export class GestDevHostComponent {
  readonly draft = signal<ComandoDraft>(DEV_DRAFT);
  readonly lastOutcome = signal<{ recognized: boolean; degraded: boolean; composite: boolean } | null>(null);

  onTestCompleted(outcome: { recognized: boolean; degraded: boolean; composite: boolean }): void {
    this.lastOutcome.set(outcome);
  }
}
