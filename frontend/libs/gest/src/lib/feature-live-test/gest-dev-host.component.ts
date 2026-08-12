import { Component, ChangeDetectionStrategy, signal } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { ComandoDraft, GestureTestOutcome } from '@neuroassist/shared/contracts';
import { GestureLiveTestComponent } from './gesture-live-test.component';

// ---------------------------------------------------------------------------
// GestDevHostComponent — thin /gest route (dev/e2e only; showInNav:false).
// Not a production surface — PAC calibracao and onboarding compose
// GestureLiveTestComponent via GestShellIntegrationService instead. This host
// exists so the live-test surface can be exercised standalone (`nx serve
// gest`, Playwright specs) without the full PAC command-editor context.
// ---------------------------------------------------------------------------

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
    primitivoPrincipal: 'tg-piscar-duplo-001',
    primitivoSecundario: null,
    sensibilidade: 'media',
    urgencia: 'rotina',
    tempoMinimoGestoMs: 400,
    tempoMaximoGestoMs: 8000,
    composite: false,
  });

  protected readonly lastOutcome = signal<GestureTestOutcome | null>(null);

  protected onOutcome(outcome: GestureTestOutcome): void {
    this.lastOutcome.set(outcome);
  }
}
