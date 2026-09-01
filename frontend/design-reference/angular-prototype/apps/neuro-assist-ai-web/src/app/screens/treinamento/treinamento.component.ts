import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { NoArvoreFalaFacade } from '../../core/data-access';
import { DsCardComponent } from '../../ui/ds-card.component';
import { DsBadgeComponent } from '../../ui/ds-badge.component';
import { DsButtonComponent } from '../../ui/ds-button.component';
import { DsInputComponent } from '../../ui/ds-input.component';
import { Locale, NoArvoreFala } from '../../shared/data-model';

// Contrato: wireframe.md Tela 08 — Treinamento (Emenda 4, 2026-08-31; ADR-016 accepted).
// Objetivo: cuidadora inicia/assiste uma conversa com a LLM (API Claude/Anthropic, ver
// ADR-016) para propor categorias/frases novas do CONV, em cada idioma. Vive no shell da
// cuidadora (DASH), não no shell restrito do paciente. Cada proposta exige Dupla
// Autorização (ADR-018) — a cuidadora aprova seu próprio lado aqui; a confirmação do
// paciente acontece de forma assíncrona na Tela 09, sem handoff de tela síncrono.
// Conversa com a LLM é SCRIPTADA no protótipo — sem chamada real de API.

interface Mensagem { de: 'llm' | 'cuidadora'; texto: string; }

const ROTEIRO_LLM: Mensagem[] = [
  { de: 'llm', texto: 'Olá! Vamos revisar frases novas para a árvore de falas do Márcio. Quer adicionar algo hoje?' },
  { de: 'cuidadora', texto: 'Sim — uma para pedir água com gelo, e outra para chamar o Danilo.' },
  { de: 'llm', texto: 'Feito. Preparei as duas propostas abaixo, em português e inglês. Revise e aprove o que fizer sentido — o Márcio confirma o dele depois, no celular dele.' },
];

@Component({
  selector: 'app-treinamento',
  standalone: true,
  imports: [DsCardComponent, DsBadgeComponent, DsButtonComponent, DsInputComponent],
  template: `
    <div class="mx-auto max-w-4xl p-8">
      <header class="mb-2 flex items-center justify-between">
        <div>
          <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Treinamento da árvore de falas</h1>
          <p class="mt-1 text-sm text-ink-soft">Conduzido pela cuidadora — o Márcio confirma o dele depois, no celular.</p>
        </div>
        <app-ds-badge tone="pu">
          <span class="material-symbols-outlined text-[14px]">auto_awesome</span>
          Treinamento — não é o app do paciente
        </app-ds-badge>
      </header>

      <div class="mb-5 flex overflow-hidden rounded-full border border-line-strong text-xs font-semibold w-fit">
        <button (click)="locale.set('pt')" class="px-3 py-1.5" [class]="locale() === 'pt' ? 'bg-brand text-white' : 'text-ink-soft'">PT</button>
        <button (click)="locale.set('en')" class="px-3 py-1.5" [class]="locale() === 'en' ? 'bg-brand text-white' : 'text-ink-soft'">EN</button>
      </div>

      @if (!sessaoIniciada()) {
        <app-ds-card>
          <p class="text-center text-sm text-ink-soft">Nenhuma sessão de treinamento iniciada.</p>
          <div class="mt-4 flex justify-center">
            <app-ds-button (click)="iniciarSessao()">Iniciar treinamento</app-ds-button>
          </div>
        </app-ds-card>
      } @else {
        <app-ds-card class="mb-5">
          <div class="flex flex-col gap-3">
            @for (m of mensagens(); track $index) {
              <div class="flex" [class]="m.de === 'llm' ? 'justify-start' : 'justify-end'">
                <div class="max-w-[80%] rounded-card px-3.5 py-2 text-sm"
                     [class]="m.de === 'llm' ? 'bg-ai-bg text-ai-ink' : 'bg-brand-subtle text-brand'">
                  <span class="mb-0.5 block text-2xs font-semibold uppercase tracking-wide opacity-70">{{ m.de === 'llm' ? 'LLM' : 'Cuidadora' }}</span>
                  {{ m.texto }}
                </div>
              </div>
            }
            @if (digitando()) {
              <div class="flex items-center gap-2 text-xs text-ink-faint">
                <span class="material-symbols-outlined animate-spin text-[16px]">progress_activity</span>
                LLM respondendo…
              </div>
            }
          </div>

          @if (proximaFalaCuidadora()) {
            <div class="mt-4 flex justify-end">
              <app-ds-button size="sm" variant="secondary" (click)="avancarRoteiro()">
                {{ proximaFalaCuidadora() }}
              </app-ds-button>
            </div>
          }
        </app-ds-card>

        <h2 class="mb-3 font-display text-sm font-semibold text-ink">Propostas pendentes (idioma: {{ locale() === 'pt' ? 'PT' : 'EN' }})</h2>
        @if (propostas().length === 0) {
          <app-ds-card><p class="text-center text-sm text-ink-soft">Nenhuma proposta pendente neste idioma.</p></app-ds-card>
        } @else {
          <div class="flex flex-col gap-3">
            @for (p of propostas(); track p.id) {
              <app-ds-card>
                <div class="flex items-start justify-between gap-4">
                  <div class="min-w-0">
                    <p class="font-display text-base font-semibold text-ink">"{{ p.conteudo[locale()].texto }}"</p>
                    <p class="mt-0.5 text-xs text-ink-faint">categoria: {{ categoriaDe(p) }}</p>
                    <div class="mt-2 flex items-center gap-2">
                      <app-ds-badge [tone]="p.conteudo[locale()].confirmadoCuidadora ? 'ok' : 'wn'">
                        {{ p.conteudo[locale()].confirmadoCuidadora ? '✅ cuidadora' : '⏳ cuidadora' }}
                      </app-ds-badge>
                      <app-ds-badge [tone]="p.conteudo[locale()].confirmadoPaciente ? 'ok' : 'wn'">
                        {{ p.conteudo[locale()].confirmadoPaciente ? '✅ paciente' : '⏳ paciente' }}
                      </app-ds-badge>
                    </div>
                  </div>
                  @if (!p.conteudo[locale()].confirmadoCuidadora) {
                    <app-ds-button size="sm" (click)="aprovar(p.id)">Aprovar</app-ds-button>
                  }
                </div>
              </app-ds-card>
            }
          </div>
        }

        <div class="mt-6 flex flex-col gap-3 border-t border-line pt-5">
          <p class="text-xs font-semibold text-ink-soft">Adicionar frase manualmente (sem LLM)</p>
          <div class="flex gap-2">
            <app-ds-input placeholder="Texto em PT" [(value)]="novoTextoPt" />
            <app-ds-input placeholder="Text in EN" [(value)]="novoTextoEn" />
            <app-ds-button variant="secondary" [disabled]="!novoTextoPt() || !novoTextoEn()" (click)="adicionarManual()">Propor</app-ds-button>
          </div>
        </div>

        <div class="mt-6 flex justify-end">
          <app-ds-button variant="ghost" size="sm" (click)="encerrarSessao()">Encerrar sessão de treinamento</app-ds-button>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TreinamentoComponent {
  private readonly facade = inject(NoArvoreFalaFacade);

  readonly locale = signal<Locale>('pt');
  readonly sessaoIniciada = signal(false);
  readonly digitando = signal(false);
  readonly mensagens = signal<Mensagem[]>([]);
  readonly novoTextoPt = signal('');
  readonly novoTextoEn = signal('');

  private roteiroIndex = 0;

  // Nós propostos via TREINAMENTO (propostoPorTreinamento=true) ainda sem Dupla
  // Autorização completa no locale ativo — o que a cuidadora revisa nesta tela.
  readonly propostas = computed(() =>
    this.facade.items().filter(n => n.propostoPorTreinamento && !this.facade.estaAtivo(n, this.locale())),
  );

  readonly proximaFalaCuidadora = computed(() => {
    const proxima = ROTEIRO_LLM[this.roteiroIndex];
    return proxima?.de === 'cuidadora' ? proxima.texto : null;
  });

  protected iniciarSessao(): void {
    this.sessaoIniciada.set(true);
    this.tocarProximaFalaLlm();
  }

  /** Roteiro scriptado (protótipo, sem API real) — a fala da cuidadora avança por clique
   * (simula o input dela); a fala seguinte da LLM toca automaticamente, com indicador de
   * "digitando". */
  protected avancarRoteiro(): void {
    const proxima = ROTEIRO_LLM[this.roteiroIndex];
    if (proxima?.de !== 'cuidadora') return;
    this.mensagens.update(list => [...list, proxima]);
    this.roteiroIndex++;
    this.tocarProximaFalaLlm();
  }

  private tocarProximaFalaLlm(): void {
    const proxima = ROTEIRO_LLM[this.roteiroIndex];
    if (proxima?.de !== 'llm') return;
    this.digitando.set(true);
    setTimeout(() => {
      this.digitando.set(false);
      this.mensagens.update(list => [...list, proxima]);
      this.roteiroIndex++;
    }, 900);
  }

  protected categoriaDe(no: NoArvoreFala): string {
    const pai = this.facade.items().find(n => n.id === no.parentId);
    return pai?.conteudo[this.locale()]?.texto ?? '—';
  }

  protected aprovar(id: string): void {
    this.facade.confirmarCuidadora(id, this.locale());
  }

  protected adicionarManual(): void {
    if (!this.novoTextoPt() || !this.novoTextoEn()) return;
    this.facade.propor('pac-marcio', 'no-cat-conversa', this.novoTextoPt(), this.novoTextoEn());
    this.novoTextoPt.set('');
    this.novoTextoEn.set('');
  }

  protected encerrarSessao(): void {
    // Propostas pendentes NÃO são descartadas ao encerrar — ficam em rascunho
    // indefinidamente (ADR-016 "Decisões complementares", sem expiração automática).
    this.sessaoIniciada.set(false);
    this.mensagens.set([]);
    this.roteiroIndex = 0;
  }
}
