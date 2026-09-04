import {
  ChangeDetectionStrategy,
  Component,
} from '@angular/core';
import { RouterLink } from '@angular/router';

// =============================================================================
// ProposalEmptyStateComponent — SPEC-02 §3 (FE-TREI-020)
//
// Presentational: shown when there are no draft nodes for the active locale.
// Links back to the training session (no Output — navigates via routerLink).
// No business logic — display-only.
// =============================================================================

@Component({
  selector: 'trei-proposal-empty-state',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="flex flex-col items-center justify-center rounded-2xl border-2 border-dashed
             border-line bg-surface p-12 text-center"
      data-testid="proposal-empty-state">
      <span class="material-symbols-outlined mb-3 text-4xl text-ink-faint">playlist_add_check</span>
      <h2 class="font-display text-xl font-bold text-ink">Nenhuma proposta ainda</h2>
      <p class="mt-2 max-w-sm text-sm text-ink-soft">
        Volte à conversa e peça ao assistente novas frases e categorias para a árvore de fala.
      </p>
      <a
        routerLink="/treinamento/sessao"
        data-testid="go-to-session-link"
        class="mt-6 rounded-xl bg-primary px-6 py-2.5 text-sm font-semibold text-white
               transition-colors hover:bg-primary-dark">
        Ir para a conversa
      </a>
    </div>
  `,
})
export class ProposalEmptyStateComponent {}
