import {
  Component,
  ChangeDetectionStrategy,
  inject,
  OnInit,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProposalReviewFacade } from '../trei-proposal.facade';
import { TreiLocale, DraftNodeVm } from '../trei.types';

// =============================================================================
// ProposalReviewPageComponent — Tela 08 / Propostas sub-view (SPEC-04, FE-TREI-013..023)
// Route: /treinamento/sessao/propostas
//
// TREI has no BA prototype (D-493 promote path does not apply — vision doc §5).
// This component is scaffolded from specs (SPEC-04 §3 / generator-config.yml).
//
// UI structure (SPEC-04 §3):
//   - ApprovalSummaryBar (count of rascunho / aguardando_aprovacao nodes)
//   - LocaleTabs (PT | EN — locale-independent per ADR-018)
//   - ProposalList (ProposedNodeCard rows)
//     - DualConfirmationIndicator (cuidadora ✓ / paciente pending)
//     - Aprovar button (POST /aprovacoes/cuidadora — CAREGIVER ONLY, INV-1)
//     - Descartar button (DELETE /nos/{noId}?locale=)
//   - ProposalEmptyState when list is empty
//
// TODO[FE-TREI-013..023]: flesh out full UI from SPEC-04 component hierarchy.
//   This stub covers the structural skeleton: node list + approval + discard.
// =============================================================================

@Component({
  selector: 'lib-proposal-review-page',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-4xl p-6">

      <!-- Header + back navigation -->
      <div class="mb-6 flex items-center gap-3">
        <a
          routerLink="/treinamento/sessao"
          data-testid="back-to-session"
          class="flex items-center gap-1 text-sm text-ink-soft hover:text-ink">
          <span class="material-symbols-outlined text-[18px]">arrow_back</span>
          Voltar à conversa
        </a>
        <div class="h-4 w-px bg-line"></div>
        <h1 class="font-display text-xl font-bold text-ink">Propostas para aprovação</h1>
      </div>

      <!-- AI authoring affordance (TECH-007 — must be present on every TREI route) -->
      <div
        class="mb-4 flex items-center gap-2 rounded-xl border border-warning bg-warning-bg px-4 py-2.5 text-sm font-semibold text-warning-ink"
        data-testid="authoring-banner">
        <span class="material-symbols-outlined text-[18px]">psychology</span>
        <span>Treinamento — não é o app do paciente</span>
      </div>

      <!-- LocaleTabs (ADR-018) -->
      <div class="mb-4 flex gap-2" role="tablist" aria-label="Idioma de autoria">
        @for (locale of locales; track locale) {
          <button
            role="tab"
            [attr.aria-selected]="vm().activeLocale === locale"
            [attr.data-testid]="'locale-tab-' + locale"
            (click)="setLocale(locale)"
            class="rounded-lg px-4 py-1.5 text-sm font-semibold transition-colors"
            [class]="vm().activeLocale === locale
              ? 'bg-primary text-white'
              : 'bg-surface-2 text-ink-soft hover:text-ink'">
            {{ locale.toUpperCase() }}
          </button>
        }
      </div>

      <!-- ApprovalSummaryBar -->
      <div class="mb-4 flex gap-4 rounded-xl bg-surface-2 px-4 py-3 text-sm">
        <span class="text-ink-soft">
          Rascunhos: <span class="font-semibold text-ink">{{ rascunhoCount() }}</span>
        </span>
        <span class="text-ink-soft">
          Aguardando paciente: <span class="font-semibold text-ink">{{ aguardandoCount() }}</span>
        </span>
      </div>

      @if (vm().status === 'loading') {
        <!-- Loading skeleton -->
        <div class="flex flex-col gap-3" aria-busy="true">
          @for (_ of [1, 2, 3]; track $index) {
            <div class="h-24 animate-pulse rounded-2xl bg-surface-2"></div>
          }
        </div>

      } @else if (vm().status === 'error') {
        <!-- Error state -->
        <div role="alert" class="rounded-2xl border border-error bg-error-bg p-4 text-sm text-error-ink"
             data-testid="proposal-error">
          <p class="font-semibold">Erro ao carregar propostas</p>
          <p class="mt-1 text-xs">{{ vm().error?.message }}</p>
        </div>

      } @else if (vm().nodes.length === 0) {
        <!-- ProposalEmptyState (SPEC-04 §4) -->
        <div
          class="flex flex-col items-center justify-center rounded-2xl border-2 border-dashed border-line bg-surface p-12 text-center"
          data-testid="proposal-empty-state">
          <span class="material-symbols-outlined mb-3 text-4xl text-ink-faint">playlist_add_check</span>
          <p class="text-sm text-ink-soft">
            Nenhuma proposta ainda. Volte à conversa e peça ao assistente novas frases.
          </p>
          <a
            routerLink="/treinamento/sessao"
            class="mt-4 text-sm font-semibold text-primary hover:underline">
            Ir para a conversa
          </a>
        </div>

      } @else {
        <!-- ProposalList (ProposedNodeCard rows) -->
        <div class="flex flex-col gap-3" data-testid="proposal-list">
          @for (node of vm().nodes; track node.noId) {
            <!-- ProposedNodeCard (SPEC-04 §3) -->
            <div
              class="rounded-2xl border border-line bg-surface p-4 shadow-card"
              [attr.data-testid]="'proposal-card'"
              [attr.data-node-id]="node.noId">

              <div class="flex items-start justify-between gap-4">
                <!-- Node text + metadata -->
                <div>
                  <p class="font-semibold text-ink">{{ node.texto }}</p>
                  <div class="mt-1 flex items-center gap-2 text-xs text-ink-soft">
                    <span class="rounded-md bg-surface-2 px-1.5 py-0.5">{{ node.tipo }}</span>
                    <span class="rounded-md bg-surface-2 px-1.5 py-0.5">{{ node.locale.toUpperCase() }}</span>
                  </div>
                </div>

                <!-- DualConfirmationIndicator (cuidadora + paciente — SPEC-04 §3) -->
                <div class="flex shrink-0 flex-col gap-1 text-xs" data-testid="dual-confirmation">
                  <div class="flex items-center gap-1.5"
                       [attr.data-testid]="'cuidadora-' + (node.confirmadoCuidadora ? 'confirmed' : 'pending')">
                    @if (node.confirmadoCuidadora) {
                      <span class="material-symbols-outlined text-[14px] text-success-ink">check_circle</span>
                      <span class="text-success-ink font-semibold">Cuidadora ✓</span>
                    } @else {
                      <span class="material-symbols-outlined text-[14px] text-ink-faint">radio_button_unchecked</span>
                      <span class="text-ink-faint">Cuidadora pendente</span>
                    }
                  </div>
                  <div class="flex items-center gap-1.5"
                       [attr.data-testid]="'paciente-' + (node.confirmadoPaciente ? 'confirmed' : 'pending')">
                    @if (node.confirmadoPaciente) {
                      <span class="material-symbols-outlined text-[14px] text-success-ink">check_circle</span>
                      <span class="text-success-ink font-semibold">Paciente ✓</span>
                    } @else {
                      <span class="material-symbols-outlined text-[14px] text-ink-faint">radio_button_unchecked</span>
                      <span class="text-ink-faint">Paciente pendente</span>
                    }
                  </div>
                </div>
              </div>

              <!-- Actions -->
              <div class="mt-3 flex gap-2">
                @if (!node.confirmadoCuidadora) {
                  <!-- Aprovar button — CAREGIVER SIDE ONLY (INV-1, ADR-007 FE) -->
                  <button
                    (click)="approve(node)"
                    [disabled]="node.approvalInProgress || node.discardInProgress"
                    data-testid="aprovar-btn"
                    class="rounded-xl bg-success-bg px-3 py-1.5 text-xs font-semibold text-success-ink transition-colors hover:bg-success-bg/80 disabled:opacity-50">
                    @if (node.approvalInProgress) {
                      Aprovando…
                    } @else {
                      Aprovar (minha parte)
                    }
                  </button>
                } @else {
                  <span
                    class="rounded-xl bg-success-bg px-3 py-1.5 text-xs font-semibold text-success-ink"
                    data-testid="aprovado-badge">
                    ✓ Aprovado por mim
                  </span>
                }

                <!-- Descartar button -->
                @if (node.subEstado === 'rascunho' || node.subEstado === 'aguardando_aprovacao') {
                  <button
                    (click)="discard(node)"
                    [disabled]="node.discardInProgress || node.approvalInProgress"
                    data-testid="descartar-btn"
                    class="rounded-xl border border-line px-3 py-1.5 text-xs text-ink-soft hover:bg-surface-2 disabled:opacity-50">
                    @if (node.discardInProgress) {
                      Descartando…
                    } @else {
                      Descartar
                    }
                  </button>
                }
              </div>
            </div>
          }
        </div>
      }

    </div>
  `,
})
export class ProposalReviewPageComponent implements OnInit {
  private readonly facade = inject(ProposalReviewFacade);

  protected readonly locales: TreiLocale[] = ['pt', 'en'];
  protected readonly vm = this.facade.screenVm;

  ngOnInit(): void {
    // Nodes are loaded reactively as they are materialized via the session facade.
    // No explicit load() needed here — ProposalReviewFacade accumulates nodes
    // as materialize() is called during the conversation.
    // Layer 2 (FE-TREI-013): will add loadAll() if session resume is needed.
  }

  protected setLocale(locale: TreiLocale): void {
    this.facade.setActiveLocale(locale);
  }

  protected approve(node: DraftNodeVm): void {
    void this.facade.approve(node.noId, node.locale);
  }

  protected discard(node: DraftNodeVm): void {
    void this.facade.discard(node.noId, node.locale);
  }

  protected rascunhoCount(): number {
    return this.vm().nodes.filter((n) => n.subEstado === 'rascunho').length;
  }

  protected aguardandoCount(): number {
    return this.vm().nodes.filter((n) => n.subEstado === 'aguardando_aprovacao').length;
  }
}
