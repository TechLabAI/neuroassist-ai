import { Component, ChangeDetectionStrategy, inject, signal, computed, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConvFacade } from '../conv.facade';
import { DsCardComponent } from '../ui/ds-card.component';
import { DsButtonComponent } from '../ui/ds-button.component';
import { Locale, NoArvoreFala } from '../conv.types';

// =============================================================================
// ConfirmacaoTreinamentoComponent — Patient Proposal Confirmation (Tela 09).
//
// Promoted from:
//   design-reference/.../screens/confirmacao-treinamento/confirmacao-treinamento.component.ts
//   (Emenda 4, 2026-08-31 — ADR-016 accepted).
//
// Architectural changes vs prototype:
//   - NoArvoreFalaFacade (prototype, local) → ConvFacade (production, @neuroassist/conv)
//   - DS selectors: app-ds-* → conv-ds-*
//   - locale provided by PatientShellComponent via @Input (shell owns locale signal)
//   - confirmarPaciente() / recusar() delegate to ConvFacade (ProposalPort, always Mock
//     in Estágio 0 — G-2/CONV-INT-006)
//   - variacoes() kept local (LLM variants are illustrative; real variants come from
//     TREI backend in the future — CONV-INT-006)
//
// Scope boundary:
//   - No import from scope:gest, scope:pac, scope:acs.
//   - Reads facade.items() to filter pendentes — same dataset as ConvArvoreComponent.
// =============================================================================

@Component({
  selector: 'conv-confirmacao-treinamento',
  standalone: true,
  imports: [DsCardComponent, DsButtonComponent, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-4xl p-8">
      <header class="mb-6">
        <h1 class="font-display text-2xl font-bold tracking-tight text-ink">
          Novas frases para confirmar
        </h1>
        <p class="mt-1 text-sm text-ink-soft">
          @if (activeLocale() === 'pt') { Idioma: Português } @else { Idioma: English }
        </p>
      </header>

      @if (loading()) {
        <p class="text-sm text-ink-faint">Carregando…</p>
      } @else if (error()) {
        <conv-ds-card>
          <p class="text-center text-sm text-danger">{{ error() }}</p>
        </conv-ds-card>
      } @else if (pendentes().length === 0) {
        <conv-ds-card>
          <p class="text-center text-sm text-ink-soft">Nenhuma frase pendente de confirmação.</p>
        </conv-ds-card>
      } @else {
        <div class="flex flex-col gap-4">
          @for (no of pendentes(); track no.id) {
            <conv-ds-card>
              <p class="font-display text-lg font-semibold text-ink">
                "{{ no.conteudo[activeLocale()].texto }}"
              </p>
              <div class="mt-4 flex flex-wrap gap-2">
                <conv-ds-button size="sm" (click)="confirmar(no.id)">
                  <span class="material-symbols-outlined text-[18px]">check</span>
                  Confirmar
                </conv-ds-button>
                <conv-ds-button size="sm" variant="secondary" (click)="ajustar(no.id)">
                  <span class="material-symbols-outlined text-[18px]">edit</span>
                  Ajustar
                </conv-ds-button>
                <conv-ds-button size="sm" variant="danger" (click)="recusar(no.id)">
                  <span class="material-symbols-outlined text-[18px]">close</span>
                  Recusar
                </conv-ds-button>
              </div>
              @if (ajustandoId() === no.id) {
                <div class="mt-3 flex flex-wrap gap-2 rounded-lg bg-surface-2 p-3">
                  <p class="w-full text-xs text-ink-faint">
                    Variações sugeridas pela LLM (protótipo — sem edição livre de texto):
                  </p>
                  @for (variacao of variacoes(no); track variacao) {
                    <button
                      (click)="confirmarComVariacao(no.id, variacao)"
                      class="rounded-full border border-line-strong px-3 py-1.5 text-xs font-semibold text-ink hover:bg-surface">
                      "{{ variacao }}"
                    </button>
                  }
                </div>
              }
            </conv-ds-card>
          }
        </div>
      }

      <div class="mt-8">
        <a routerLink="/paciente/conversar"
           class="inline-flex items-center gap-1.5 text-sm font-semibold text-ink-soft hover:text-ink">
          <span class="material-symbols-outlined text-[18px]">arrow_back</span>
          Voltar para conversa
        </a>
      </div>
    </div>
  `,
})
export class ConfirmacaoTreinamentoComponent {
  private readonly facade = inject(ConvFacade);

  readonly loading = this.facade.loading;
  readonly error = this.facade.error;

  /** Active locale — provided by PatientShellComponent via @Input (same signal as ConvArvoreComponent).
   *  TODO[INT-FE-CONV-001]: seed from PatientShellComponent.locale signal via DI or router data. */
  @Input() set locale(value: Locale) { this._locale.set(value); }
  private readonly _locale = signal<Locale>('pt');
  readonly activeLocale = this._locale.asReadonly();

  readonly ajustandoId = signal<string | null>(null);

  /** Pending proposals: caregiver-approved, patient-not-yet-confirmed, in the active locale. */
  readonly pendentes = computed(() =>
    this.facade.items().filter(n => {
      const c = n.conteudo[this.activeLocale()];
      return c.confirmadoCuidadora && !c.confirmadoPaciente;
    }),
  );

  protected confirmar(id: string): void {
    this.facade.confirmarPaciente(id, this.activeLocale());
    this.ajustandoId.set(null);
  }

  protected ajustar(id: string): void {
    this.ajustandoId.set(this.ajustandoId() === id ? null : id);
  }

  protected recusar(id: string): void {
    this.facade.recusar(id, this.activeLocale());
    this.ajustandoId.set(null);
  }

  /** Short LLM-suggested variants (illustrative; real variants from TREI — CONV-INT-006).
   *  Patient can only pick, never free-type (motor limitation constraint). */
  protected variacoes(no: NoArvoreFala): string[] {
    const base = no.conteudo[this.activeLocale()].texto;
    return [base, `${base}, por favor`];
  }

  protected confirmarComVariacao(id: string, _variacao: string): void {
    // TODO[CONV-INT-006]: persist chosen variant as the node's final text.
    this.confirmar(id);
  }
}
