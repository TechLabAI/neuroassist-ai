import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NoArvoreFalaFacade } from '../../core/data-access';
import { DsCardComponent } from '../../ui/ds-card.component';
import { DsButtonComponent } from '../../ui/ds-button.component';
import { Locale } from '../../shared/data-model';

// Contrato: wireframe.md Tela 09 — Confirmação do Paciente (Emenda 4, 2026-08-31; ADR-016
// accepted). Objetivo: paciente vê propostas já aprovadas pela cuidadora (Tela 08) e
// confirma/ajusta/recusa, no seu próprio ritmo — sem handoff de tela síncrono. Vive no
// shell restrito do paciente (patient-shell.component.ts, mesmo shell da Tela 06). Sem
// edição livre de texto (motricidade limitada) — só sim/ajustar(variações curtas)/recusar,
// pelo mesmo canal de entrada agnóstico da Tela 06.

@Component({
  selector: 'app-confirmacao-treinamento',
  standalone: true,
  imports: [DsCardComponent, DsButtonComponent, RouterLink],
  template: `
    <div class="mx-auto max-w-4xl p-8">
      <header class="mb-6">
        <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Novas frases para confirmar</h1>
        <p class="mt-1 text-sm text-ink-soft">
          @if (locale() === 'pt') { Idioma: Português } @else { Idioma: English }
        </p>
      </header>

      @if (loading()) {
        <p class="text-sm text-ink-faint">Carregando…</p>
      } @else if (pendentes().length === 0) {
        <app-ds-card>
          <p class="text-center text-sm text-ink-soft">Nenhuma frase pendente de confirmação.</p>
        </app-ds-card>
      } @else {
        <div class="flex flex-col gap-4">
          @for (no of pendentes(); track no.id) {
            <app-ds-card>
              <p class="font-display text-lg font-semibold text-ink">"{{ no.conteudo[locale()].texto }}"</p>
              <div class="mt-4 flex flex-wrap gap-2">
                <app-ds-button size="sm" (click)="confirmar(no.id)">
                  <span class="material-symbols-outlined text-[18px]">check</span> Confirmar
                </app-ds-button>
                <app-ds-button size="sm" variant="secondary" (click)="ajustar(no.id)">
                  <span class="material-symbols-outlined text-[18px]">edit</span> Ajustar
                </app-ds-button>
                <app-ds-button size="sm" variant="danger" (click)="recusar(no.id)">
                  <span class="material-symbols-outlined text-[18px]">close</span> Recusar
                </app-ds-button>
              </div>
              @if (ajustandoId() === no.id) {
                <div class="mt-3 flex flex-wrap gap-2 rounded-lg bg-surface-2 p-3">
                  <p class="w-full text-xs text-ink-faint">Variações sugeridas pela LLM (protótipo — sem edição livre de texto):</p>
                  @for (variacao of variacoes(no); track variacao) {
                    <button (click)="confirmarComVariacao(no.id, variacao)"
                            class="rounded-full border border-line-strong px-3 py-1.5 text-xs font-semibold text-ink hover:bg-surface">
                      "{{ variacao }}"
                    </button>
                  }
                </div>
              }
            </app-ds-card>
          }
        </div>
      }

      <div class="mt-8">
        <a routerLink="/paciente/conversar" class="inline-flex items-center gap-1.5 text-sm font-semibold text-ink-soft hover:text-ink">
          <span class="material-symbols-outlined text-[18px]">arrow_back</span> Voltar para conversa
        </a>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmacaoTreinamentoComponent {
  private readonly facade = inject(NoArvoreFalaFacade);
  readonly loading = this.facade.loading;

  /** Idioma ativo da sessão do paciente — no protótipo, fixo em PT (o seletor completo
   * vive na Tela 06; ver TODO[arquiteto] de UX bilíngue no ADR-018). */
  readonly locale = signal<Locale>('pt');
  readonly ajustandoId = signal<string | null>(null);

  // Só nós com confirmadoCuidadora=true e confirmadoPaciente=false no locale ativo —
  // Dupla Autorização pendente do lado do paciente (ADR-016/ADR-018).
  readonly pendentes = computed(() =>
    this.facade.items().filter(n => {
      const c = n.conteudo[this.locale()];
      return c.confirmadoCuidadora && !c.confirmadoPaciente;
    }),
  );

  protected confirmar(id: string): void {
    this.facade.confirmarPaciente(id, this.locale());
    this.ajustandoId.set(null);
  }

  protected ajustar(id: string): void {
    this.ajustandoId.set(this.ajustandoId() === id ? null : id);
  }

  protected recusar(id: string): void {
    this.facade.recusar(id, this.locale());
    this.ajustandoId.set(null);
  }

  /** Variações curtas ilustrativas — no produto real, viriam da LLM da sessão de
   * TREINAMENTO (Tela 08), não hardcoded. Protótipo mostra a interação sem edição livre. */
  protected variacoes(no: { conteudo: Record<Locale, { texto: string }> }): string[] {
    const base = no.conteudo[this.locale()].texto;
    return [base, `${base}, por favor`];
  }

  protected confirmarComVariacao(id: string, _variacao: string): void {
    // TODO[arquiteto]: persistir a variação escolhida como o texto final do nó, não só
    // confirmar o texto original — protótipo só demonstra a interação de escolha.
    this.confirmar(id);
  }
}
