import { ChangeDetectionStrategy, Component } from '@angular/core';

// =============================================================================
// TreiAuthoringBannerComponent — SPEC-06 §4 (TREI-FE-021)
//
// Presentational. Rendered by TreiRootComponent so it appears on EVERY TREI
// route without being duplicated in individual page components (INV-6).
//
// Previously inlined inside TrainingSessionPageComponent (P4); extracted here
// in P6 per the plan's architecture-hardening wave.
//
// Accessibility: role="status" + aria-label communicates the authoring context
// to screen reader users.
// =============================================================================

@Component({
  selector: 'trei-authoring-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="mb-4 flex items-center gap-2 rounded-xl border border-warning bg-warning-bg
             px-4 py-2.5 text-sm font-semibold text-warning-ink"
      role="status"
      data-testid="authoring-banner"
      aria-label="Esta é a superfície de autoria — não é o app do paciente">
      <span class="material-symbols-outlined text-[18px]" aria-hidden="true">psychology</span>
      <span>Treinamento — não é o app do paciente</span>
    </div>
  `,
})
export class TreiAuthoringBannerComponent {}
