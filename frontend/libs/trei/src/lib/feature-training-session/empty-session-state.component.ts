import { ChangeDetectionStrategy, Component, EventEmitter, Output } from '@angular/core';

// =============================================================================
// EmptySessionStateComponent — SPEC-02 §2 (FE-TREI-015)
//
// Presentational: shown when status = 'idle' (no active session).
// CTA "Iniciar treinamento" triggers output → smart parent calls facade.startSession().
// =============================================================================

@Component({
  selector: 'trei-empty-session-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="flex flex-col items-center justify-center rounded-2xl border-2 border-dashed
             border-line bg-surface p-12 text-center"
      data-testid="empty-state">
      <span class="material-symbols-outlined mb-3 text-4xl text-ink-faint">chat</span>
      <h2 class="font-display text-xl font-bold text-ink">Iniciar treinamento</h2>
      <p class="mt-2 max-w-sm text-sm text-ink-soft">
        Converse com o assistente para propor novas categorias e frases
        para a árvore de fala de Márcio.
      </p>
      <button
        (click)="sessionStart.emit()"
        data-testid="start-session-btn"
        class="mt-6 rounded-xl bg-primary px-6 py-2.5 text-sm font-semibold text-white
               transition-colors hover:bg-primary-dark">
        Iniciar sessão
      </button>
    </div>
  `,
})
export class EmptySessionStateComponent {
  @Output() readonly sessionStart = new EventEmitter<void>();
}
