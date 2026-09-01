import { Component, ChangeDetectionStrategy, inject, signal, computed, OnInit, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConvFacade } from '../conv.facade';
import { DsCardComponent } from '../ui/ds-card.component';
import { DsBadgeComponent } from '../ui/ds-badge.component';
import { DsButtonComponent } from '../ui/ds-button.component';
import { Locale, NoArvoreFala } from '../conv.types';

// =============================================================================
// ConvArvoreComponent — Speech Tree Navigator (Tela 06 / CONV-FE-005..013).
//
// Promoted from:
//   design-reference/.../screens/conv-arvore/conv-arvore.component.ts (Emenda 4).
//
// Architectural changes vs prototype:
//   - NoArvoreFalaFacade (prototype, local) → ConvFacade (production, @neuroassist/conv)
//   - DsCard/DsButton/DsBadge selectors: app-ds-* → conv-ds-* (local lib prefix)
//   - locale managed by PatientShellComponent (signal input from shell) — not a
//     local signal anymore; shell passes it via @Input (scope:web composes channels)
//   - selectNode() delegates audit event to facade (fire-and-forget, non-blocking)
//   - Voice output chain: cached audioRef → Web Speech API fallback (ADR-017/OPS-004)
//     both simulated here (no real playback in Estágio 0); full implementation in
//     CONV-FE-013 (AudioPlayer) and CONV-FE-010 (TTS Web Speech fallback).
//
// Scope boundary (scope:conv → scope:web):
//   - Canal de entrada agnóstico (ADR-005): gesture channel composed at scope:web
//     via INPUT_CHANNEL_PORT (CONV-INT-003). This component receives a
//     gesture-selected node ID via simularGesto() for dev simulation only.
//   - DO NOT import @neuroassist/gest here (nx-boundary violation).
//
// State: parentIdAtual and noSelecionadoId are local UI state (view-model
//   navigation within the loaded tree) — NOT facade state (facade owns the tree
//   dataset; component owns the cursor).
// =============================================================================

type CanalEntrada = 'toque' | 'gesto';

@Component({
  selector: 'conv-speech-tree',
  standalone: true,
  imports: [DsCardComponent, DsBadgeComponent, DsButtonComponent, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-4xl p-8">
      <header class="mb-4 flex items-center justify-between">
        <div>
          <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Conversar</h1>
          <p class="mt-1 text-sm text-ink-soft">
            @if (nivelAtual(); as pai) {
              {{ pai.conteudo[activeLocale()].texto }}
            } @else {
              Escolha uma categoria
            }
          </p>
        </div>
        <div class="flex items-center gap-2">
          <conv-ds-badge [tone]="canal() === 'gesto' ? 'pu' : 'nf'">
            {{ canal() === 'gesto' ? 'Canal: gesto' : 'Canal: toque' }}
          </conv-ds-badge>
        </div>
      </header>

      <!-- Proposals banner (Tela 09 link) — ADR-016 -->
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
      } @else if (error()) {
        <conv-ds-card>
          <p class="text-center text-sm text-danger">{{ error() }}</p>
        </conv-ds-card>
      } @else if (nosNoNivel().length === 0) {
        <conv-ds-card>
          <p class="text-center text-sm text-ink-soft">
            Árvore ainda não configurada para este paciente neste idioma.
          </p>
          <p class="mt-1 text-center text-xs text-ink-faint">
            <!-- TODO[ADR-018]: UX for node active in PT but pending in EN -->
          </p>
        </conv-ds-card>
      } @else {
        <div class="grid grid-cols-2 gap-4 sm:grid-cols-3">
          @for (no of nosNoNivel(); track no.id) {
            <button
              (click)="selecionar(no.id)"
              class="flex min-h-[110px] flex-col items-center justify-center gap-2 rounded-card border-[1.5px] p-4 text-center transition-colors"
              [class]="noSelecionadoId() === no.id
                ? 'border-brand bg-brand-subtle text-brand'
                : 'border-line-strong text-ink hover:bg-surface-2'">
              <span class="material-symbols-outlined text-[28px]">
                {{ no.tipo === 'categoria' ? 'folder' : 'chat_bubble' }}
              </span>
              <span class="text-sm font-semibold">{{ no.conteudo[activeLocale()].texto }}</span>
              @if (no.engineFeatureRef) {
                <span class="text-2xs text-ink-faint">alcançável por gesto</span>
              }
            </button>
          }
        </div>

        @if (noSelecionado(); as sel) {
          <conv-ds-card class="mt-6">
            <div class="flex items-center gap-3">
              <span class="material-symbols-outlined text-[22px] text-brand">
                {{ sel.conteudo[activeLocale()].audioRef ? 'volume_up' : 'record_voice_over' }}
              </span>
              <p class="font-display text-lg font-semibold text-ink">
                "{{ sel.conteudo[activeLocale()].texto }}"
              </p>
            </div>
            @if (sel.conteudo[activeLocale()].audioRef) {
              <p class="mt-1 text-xs text-ink-faint">
                Áudio cacheado da voz do paciente (ElevenLabs, sintetizado na fase de TREINAMENTO
                — ADR-017). Reprodução real: CONV-FE-013 (AudioPlayer).
              </p>
            } @else {
              <p class="mt-1 text-xs text-ink-faint">
                Áudio cacheado indisponível — fallback Web Speech API (OPS-004).
                Implementação real: CONV-FE-010 (TTS fallback).
              </p>
            }
          </conv-ds-card>
        }
      }

      <div class="mt-7 flex items-center justify-between">
        @if (nivelAtual()) {
          <conv-ds-button variant="secondary" (click)="voltar()">
            <span class="material-symbols-outlined text-[18px]">arrow_back</span>
            Voltar para categorias
          </conv-ds-button>
        } @else {
          <span></span>
        }

        <!-- Dev-only gesture simulation (compose INPUT_CHANNEL_PORT at scope:web — CONV-INT-003) -->
        <conv-ds-button variant="ghost" size="sm" (click)="simularGesto()">
          Simular gesto (sobrancelha)
        </conv-ds-button>
      </div>
    </div>
  `,
})
export class ConvArvoreComponent implements OnInit {
  private readonly facade = inject(ConvFacade);

  readonly loading = this.facade.loading;
  readonly error = this.facade.error;

  /** Active locale — provided by PatientShellComponent via @Input.
   *  Shell owns the locale signal; component reads it. */
  @Input() set locale(value: Locale) { this._locale.set(value); }
  private readonly _locale = signal<Locale>('pt');
  readonly activeLocale = this._locale.asReadonly();

  readonly parentIdAtual = signal<string | null>(null);
  readonly noSelecionadoId = signal<string | null>(null);
  readonly canal = signal<CanalEntrada>('toque');

  readonly nivelAtual = computed(() =>
    this.facade.items().find(n => n.id === this.parentIdAtual()) ?? null,
  );

  /** Retrieval-only (ADR-015): only nodes with Dual-Authorization complete in
   *  the active locale are visible; categories always shown (structural). */
  readonly nosNoNivel = computed(() => {
    const locale = this.activeLocale();
    return this.facade.items()
      .filter(n => n.parentId === this.parentIdAtual())
      .filter(n => n.tipo === 'categoria' || this.facade.estaAtivo(n, locale))
      .sort((a, b) => a.ordem - b.ordem);
  });

  readonly noSelecionado = computed((): NoArvoreFala | null =>
    this.facade.items().find(n => n.id === this.noSelecionadoId()) ?? null,
  );

  /** Count of nodes caregiver-approved but still pending patient confirmation
   *  in the active locale — drives the proposals banner (ADR-016). */
  readonly pendentesConfirmacao = computed(() => {
    const locale = this.activeLocale();
    return this.facade.items().filter(n => {
      const c = n.conteudo[locale];
      return c.confirmadoCuidadora && !c.confirmadoPaciente;
    }).length;
  });

  ngOnInit(): void {
    this.facade.load(this.activeLocale());
  }

  protected selecionar(id: string): void {
    const no = this.facade.items().find(n => n.id === id);
    if (!no) return;
    if (no.tipo === 'categoria') {
      this.parentIdAtual.set(no.id);
      this.noSelecionadoId.set(null);
    } else {
      this.canal.set('toque');
      this.noSelecionadoId.set(no.id);
      this.facade.selectNode(no.id, this.activeLocale()); // audit — fire-and-forget
      // TODO[CONV-FE-013]: play cached audioRef via AudioPlayer
      // TODO[CONV-FE-010]: fallback to Web Speech API when audioRef is null
    }
  }

  protected voltar(): void {
    const pai = this.nivelAtual();
    this.parentIdAtual.set(pai?.parentId ?? null);
    this.noSelecionadoId.set(null);
  }

  /** Dev simulation of the gesture input channel (ADR-005 / CONV-INT-003).
   *  Selects the first node at the current level with engineFeatureRef = 'tg-sobrancelha'.
   *  In production, this selection happens through INPUT_CHANNEL_PORT at scope:web. */
  protected simularGesto(): void {
    const alvo = this.nosNoNivel().find(n => n.engineFeatureRef === 'tg-sobrancelha');
    if (!alvo) return;
    this.canal.set('gesto');
    if (alvo.tipo === 'categoria') {
      this.parentIdAtual.set(alvo.id);
    } else {
      this.noSelecionadoId.set(alvo.id);
      this.facade.selectNode(alvo.id, this.activeLocale());
    }
  }
}
