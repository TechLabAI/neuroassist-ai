import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NoArvoreFalaFacade } from '../../core/data-access';
import { DsCardComponent } from '../../ui/ds-card.component';
import { DsBadgeComponent } from '../../ui/ds-badge.component';
import { DsButtonComponent } from '../../ui/ds-button.component';
import { Locale } from '../../shared/data-model';

// Contrato: wireframe.md Tela 06 — Árvore de Falas (Emenda 3, 2026-08-30; ADR-013 accepted).
// Objetivo: navegação categoria → frase, canal de entrada agnóstico (gesto reaproveitando
// GEST via engineFeatureRef, ou toque/mouse direto) — o mesmo modelo de "nó selecionado"
// é alcançável por qualquer canal, sem hierarquia entre eles (Princípio 7 — ≤2 cliques).
// TTS é simulado no protótipo (destaque visual + ícone de áudio), sem síntese real.
//
// Emenda 4 (2026-08-31, wireframe §7 adendo) — sem reescrever o comportamento acima:
// (1) só nós com Dupla Autorização completa no locale ativo aparecem aqui (retrieval puro
//     sobre conteúdo já revisado, ver ADR-015/TECH-007); nós ainda em TREINAMENTO ficam de
//     fora, exceto o banner de "N frases para confirmar" (Tela 09);
// (2) conteúdo passa a ser por Locale (`conteudo.pt`/`conteudo.en`), não mais `texto` único
//     (ADR-018) — seletor de idioma simplificado no protótipo (PT/EN);
// (3) TTSPlayer deixa de ter "escopo a confirmar": reflete visualmente se o áudio está
//     cacheado (ADR-017) ou cairia no fallback Web Speech API (OPS-004).

type CanalEntrada = 'toque' | 'gesto';

@Component({
  selector: 'app-conv-arvore',
  standalone: true,
  imports: [DsCardComponent, DsBadgeComponent, DsButtonComponent, RouterLink],
  template: `
    <div class="mx-auto max-w-4xl p-8">
      <header class="mb-4 flex items-center justify-between">
        <div>
          <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Conversar</h1>
          <p class="mt-1 text-sm text-ink-soft">
            @if (nivelAtual(); as pai) {
              {{ pai.conteudo[locale()].texto }}
            } @else {
              Escolha uma categoria
            }
          </p>
        </div>
        <div class="flex items-center gap-2">
          <div class="flex overflow-hidden rounded-full border border-line-strong text-xs font-semibold">
            <button (click)="locale.set('pt')" class="px-2.5 py-1" [class]="locale() === 'pt' ? 'bg-brand text-white' : 'text-ink-soft'">PT</button>
            <button (click)="locale.set('en')" class="px-2.5 py-1" [class]="locale() === 'en' ? 'bg-brand text-white' : 'text-ink-soft'">EN</button>
          </div>
          <app-ds-badge [tone]="canal() === 'gesto' ? 'pu' : 'nf'">
            {{ canal() === 'gesto' ? 'Canal ativo: gesto' : 'Canal ativo: toque' }}
          </app-ds-badge>
        </div>
      </header>

      <!-- Emenda 4 (ADR-016) — link para a Tela 09 quando há propostas do TREINAMENTO já
           aprovadas pela cuidadora, aguardando confirmação do paciente. Assíncrono, sem
           handoff de tela síncrono. -->
      @if (pendentesConfirmacao() > 0) {
        <a routerLink="/paciente/confirmar-treinamento"
           class="mb-4 flex items-center justify-between rounded-card border border-ai-bg bg-ai-bg/40 px-4 py-3 text-sm font-semibold text-ai-ink transition-colors hover:bg-ai-bg/60">
          <span class="flex items-center gap-2">
            <span class="material-symbols-outlined text-[18px]">auto_awesome</span>
            {{ pendentesConfirmacao() }} nova(s) frase(s) para confirmar
          </span>
          <span class="material-symbols-outlined text-[18px]">chevron_right</span>
        </a>
      }

      @if (loading()) {
        <p class="text-sm text-ink-faint">Carregando árvore…</p>
      } @else if (nosNoNivel().length === 0) {
        <app-ds-card>
          <p class="text-center text-sm text-ink-soft">Árvore ainda não configurada para este paciente neste idioma.</p>
          <p class="mt-1 text-center text-xs text-ink-faint">TODO[arquiteto]: comportamento de UX para nó "ativo em PT, pendente em EN" (ver ADR-018 TODO).</p>
        </app-ds-card>
      } @else {
        <div class="grid grid-cols-2 gap-4 sm:grid-cols-3">
          @for (no of nosNoNivel(); track no.id) {
            <button
              (click)="selecionar(no.id)"
              class="flex min-h-[110px] flex-col items-center justify-center gap-2 rounded-card border-[1.5px] p-4 text-center transition-colors"
              [class]="noSelecionadoId() === no.id
                ? 'border-brand bg-brand-subtle text-brand'
                : 'border-line-strong text-ink hover:bg-surface-2'">
              <span class="material-symbols-outlined text-[28px]">{{ no.tipo === 'categoria' ? 'folder' : 'chat_bubble' }}</span>
              <span class="text-sm font-semibold">{{ no.conteudo[locale()].texto }}</span>
              @if (no.engineFeatureRef) {
                <span class="text-2xs text-ink-faint">alcançável por gesto</span>
              }
            </button>
          }
        </div>

        @if (noSelecionado(); as sel) {
          <app-ds-card class="mt-6">
            <div class="flex items-center gap-3">
              <span class="material-symbols-outlined text-[22px] text-brand">{{ sel.conteudo[locale()].audioRef ? 'volume_up' : 'record_voice_over' }}</span>
              <p class="font-display text-lg font-semibold text-ink">"{{ sel.conteudo[locale()].texto }}"</p>
            </div>
            @if (sel.conteudo[locale()].audioRef) {
              <p class="mt-1 text-xs text-ink-faint">Reproduzido com áudio cacheado da voz do paciente (ElevenLabs, sintetizado na fase de TREINAMENTO — ADR-017). Simulado no protótipo.</p>
            } @else {
              <p class="mt-1 text-xs text-ink-faint">Áudio cacheado ainda não disponível — fallback TTS nativo do navegador (Web Speech API, constraint OPS-004). Simulado no protótipo.</p>
            }
          </app-ds-card>
        }
      }

      <div class="mt-7 flex items-center justify-between">
        @if (nivelAtual()) {
          <app-ds-button variant="secondary" (click)="voltar()">
            <span class="material-symbols-outlined text-[18px]">arrow_back</span> Voltar para categorias
          </app-ds-button>
        } @else {
          <span></span>
        }

        <!-- Simulação de canal por gesto — protótipo não tem webcam real (ver ADR-014, PLAT-001). -->
        <app-ds-button variant="ghost" size="sm" (click)="simularGesto()">
          Simular gesto (sobrancelha)
        </app-ds-button>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConvArvoreComponent {
  private readonly facade = inject(NoArvoreFalaFacade);
  readonly loading = this.facade.loading;

  readonly parentIdAtual = signal<string | null>(null);
  readonly noSelecionadoId = signal<string | null>(null);
  readonly canal = signal<CanalEntrada>('toque');
  /** Seletor de idioma simplificado do protótipo (ADR-018) — cada locale tem seu próprio
   * conjunto de nós "ativos" (Dupla Autorização completa), ver `NoArvoreFalaFacade.estaAtivo`. */
  readonly locale = signal<Locale>('pt');

  readonly nivelAtual = computed(() =>
    this.facade.items().find(n => n.id === this.parentIdAtual()) ?? null,
  );

  // Retrieval puro (ADR-015, TECH-007): só nós com Dupla Autorização completa no locale
  // ativo aparecem na navegação — categorias sempre visíveis (estrutura, não conteúdo
  // sensível a revisão), frases só depois de aprovadas nos dois lados.
  readonly nosNoNivel = computed(() => {
    const locale = this.locale();
    return this.facade.items()
      .filter(n => n.parentId === this.parentIdAtual())
      .filter(n => n.tipo === 'categoria' || this.facade.estaAtivo(n, locale))
      .sort((a, b) => a.ordem - b.ordem);
  });

  readonly noSelecionado = computed(() =>
    this.facade.items().find(n => n.id === this.noSelecionadoId()) ?? null,
  );

  /** Nós já aprovados pela cuidadora no locale ativo, mas ainda aguardando confirmação
   * do paciente — alimenta o banner de link para a Tela 09 (ADR-016). */
  readonly pendentesConfirmacao = computed(() => {
    const locale = this.locale();
    return this.facade.items().filter(n => {
      const c = n.conteudo[locale];
      return c.confirmadoCuidadora && !c.confirmadoPaciente;
    }).length;
  });

  protected selecionar(id: string): void {
    const no = this.facade.items().find(n => n.id === id);
    if (!no) return;
    if (no.tipo === 'categoria') {
      this.parentIdAtual.set(no.id);
      this.noSelecionadoId.set(null);
    } else {
      this.canal.set('toque');
      this.noSelecionadoId.set(no.id);
      // TODO[arquiteto]: se o nó tiver engineFeatureRef mapeado a um Comando, avaliar se a
      // seleção também deve gerar um Evento no catálogo existente (Framing §12 pergunta 6).
    }
  }

  protected voltar(): void {
    const pai = this.nivelAtual();
    this.parentIdAtual.set(pai?.parentId ?? null);
    this.noSelecionadoId.set(null);
  }

  /** Demonstra o canal de entrada agnóstico (Princípio da Tela 06): seleciona, por
   * "gesto" simulado, o primeiro nó do nível atual com engineFeatureRef = 'tg-sobrancelha' —
   * o mesmo destino que o toque alcançaria, sem privilégio de canal. */
  protected simularGesto(): void {
    const alvo = this.nosNoNivel().find(n => n.engineFeatureRef === 'tg-sobrancelha');
    if (!alvo) return;
    this.canal.set('gesto');
    if (alvo.tipo === 'categoria') {
      this.parentIdAtual.set(alvo.id);
    } else {
      this.noSelecionadoId.set(alvo.id);
    }
  }
}
