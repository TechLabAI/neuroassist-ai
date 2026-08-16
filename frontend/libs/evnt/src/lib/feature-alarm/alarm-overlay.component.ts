import { Component, ChangeDetectionStrategy, inject, computed } from '@angular/core';
import { EventDeliveryFacade } from '../evnt.facade';

// =============================================================================
// AlarmOverlayComponent — SPEC-02, SPEC-03 §2
// Promoted from design-reference angular-prototype: screens/alarme/alarme.component.ts
//
// KEY ARCHITECTURE NOTE (ADR-005, SPEC-06 §2):
// This component is NOT a routed page — it is a global shell outlet registered by
// EvntShellIntegrationService. The shell renders it above the router-outlet
// (z-layer: interrupt) so it pre-empts whatever screen is active.
//
// The overlay is visible ONLY while AlarmVm.visible is true (i.e., activeEvent != null).
// Confirmation is non-optimistic: activeEvent clears only after POST /confirmation 200.
// =============================================================================

@Component({
  selector: 'evnt-alarm-overlay',
  standalone: true,
  template: `
    @if (vm().visible && vm().event; as evt) {
      <div
        class="fixed inset-0 z-50 flex flex-col items-center justify-center bg-alarm px-8 text-center"
        role="alertdialog"
        aria-live="assertive"
        aria-modal="true"
        data-testid="alarm-overlay"
        [attr.aria-label]="evt.patientName + ' precisa de ajuda'">

        <span class="material-symbols-outlined text-[96px] text-alarm-ink">emergency_home</span>

        <h1 class="mt-6 font-display text-4xl font-extrabold tracking-tight text-alarm-ink">
          {{ evt.patientName }} precisa de ajuda
        </h1>

        <p class="mt-3 text-lg font-semibold text-alarm-ink/90">"{{ evt.commandLabel }}"</p>

        <p class="mt-2 text-base font-medium text-alarm-ink/80">
          Gesto detectado {{ evt.displayTime }}
        </p>

        @if (vm().confirmError) {
          <p class="mt-4 rounded-lg bg-white/20 px-4 py-2 text-sm font-medium text-alarm-ink">
            Erro ao confirmar — tente novamente.
          </p>
        }

        @if (vm().alreadyConfirmed) {
          <div class="mt-6 rounded-xl bg-white/30 px-6 py-4 text-alarm-ink">
            <p class="font-semibold">Já confirmado por outra pessoa</p>
          </div>
        } @else {
          <button
            (click)="confirmar(evt.eventoId)"
            [disabled]="vm().confirming"
            class="mt-12 inline-flex items-center gap-2 rounded-xl bg-white px-8 py-4 text-base font-bold text-alarm shadow-lg transition-transform hover:scale-[1.02] active:scale-[0.98] disabled:opacity-60 disabled:cursor-not-allowed">
            @if (vm().confirming) {
              <span class="material-symbols-outlined animate-spin text-[22px]">progress_activity</span>
              Confirmando…
            } @else {
              <span class="material-symbols-outlined text-[22px]">check</span>
              Confirmar que atendi {{ primeiroNome(evt.patientName) }}
            }
          </button>
        }
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmOverlayComponent {
  private readonly facade = inject(EventDeliveryFacade);

  /** AlarmVm computed from facade — single source of truth (SPEC-03 §2). */
  readonly vm = computed(() => this.facade.alarmVm());

  protected primeiroNome(nome: string): string {
    return nome.split(' ')[0];
  }

  /** Delegate to facade (non-optimistic: clears overlay only on 200). */
  protected confirmar(eventoId: string): void {
    void this.facade.confirm(eventoId, 'real');
  }
}
