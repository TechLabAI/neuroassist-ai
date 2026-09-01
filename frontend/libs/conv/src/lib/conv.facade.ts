import { Injectable, inject, signal, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { from, lastValueFrom } from 'rxjs';
import { NO_ARVORE_FALA_PORT } from './api/no-arvore-fala.port';
import { PROPOSAL_PORT } from './api/proposal.port';
import { NoArvoreFala, Locale } from './conv.types';

// =============================================================================
// ConvFacade — application layer for the CONV module.
// Signals-based (ADR-007 / architectural principle §Signals-first).
// Injected by ConvArvoreComponent and ConfirmacaoTreinamentoComponent.
// ConvFacade is the ONLY dependency the UI components know about.
//
// Port topology:
//   NoArvoreFalaPort → tree retrieval (conv-service, ADR-015)
//   ProposalPort     → patient proposals (TREI, deferred — MockAdapter only, G-2)
// =============================================================================

@Injectable({ providedIn: 'root' })
export class ConvFacade {
  private readonly treePort = inject(NO_ARVORE_FALA_PORT);
  private readonly proposalPort = inject(PROPOSAL_PORT);
  private readonly destroyRef = inject(DestroyRef);

  // ---------------------------------------------------------------------------
  // State signals
  // ---------------------------------------------------------------------------
  private readonly _items = signal<NoArvoreFala[]>([]);
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);

  readonly items = this._items.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  // ---------------------------------------------------------------------------
  // Load (tree)
  // ---------------------------------------------------------------------------

  /** Load (or refresh) the speech tree for the given locale.
   *  Called by ConvArvoreComponent on init and on locale switch. */
  load(locale?: Locale): void {
    this._loading.set(true);
    this._error.set(null);
    from(lastValueFrom(this.treePort.list(locale)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: nodes => {
          this._items.set(nodes);
          this._loading.set(false);
        },
        error: err => {
          this._error.set(err?.message ?? 'Erro ao carregar a árvore de falas.');
          this._loading.set(false);
        },
      });
  }

  // ---------------------------------------------------------------------------
  // Domain helpers (used by computed() in components)
  // ---------------------------------------------------------------------------

  /** A node is "active" (displayed in the navigable tree) when both
   *  Dual-Authorization flags are true for the given locale (ADR-015/ADR-018). */
  estaAtivo(no: NoArvoreFala, locale: Locale): boolean {
    const c = no.conteudo[locale];
    return c.confirmadoCuidadora && c.confirmadoPaciente;
  }

  // ---------------------------------------------------------------------------
  // Commands — delegated to ProposalPort (TREI, deferred)
  // ---------------------------------------------------------------------------

  /** Patient confirms a pending phrase (Dual Authorization, Tela 09 — ADR-016).
   *  On success, refreshes the local items list to reflect the new active state.
   *  The real backend triggers ES indexing + ElevenLabs synthesis (ADR-015/017). */
  confirmarPaciente(id: string, locale: Locale): void {
    from(lastValueFrom(this.proposalPort.confirmarPaciente(id, locale)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          // Optimistic update: mark the node as patient-confirmed locally.
          this._items.update(list =>
            list.map(n => {
              if (n.id !== id) return n;
              const prev = n.conteudo[locale];
              return {
                ...n,
                conteudo: {
                  ...n.conteudo,
                  [locale]: {
                    ...prev,
                    confirmadoPaciente: true,
                    audioRef: prev.confirmadoCuidadora
                      ? prev.audioRef ?? `audio/${locale}/${prev.texto.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.mp3`
                      : null,
                  },
                },
              };
            }),
          );
        },
        error: err => this._error.set(err?.message ?? 'Erro ao confirmar frase.'),
      });
  }

  /** Patient rejects a proposal for the given locale (ADR-016/ADR-018). */
  recusar(id: string, locale: Locale): void {
    from(lastValueFrom(this.proposalPort.recusar(id, locale)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          // Optimistic update: reset Dual-Authorization flags for this locale.
          this._items.update(list =>
            list.map(n =>
              n.id === id
                ? {
                    ...n,
                    conteudo: {
                      ...n.conteudo,
                      [locale]: { ...n.conteudo[locale], confirmadoCuidadora: false, confirmadoPaciente: false, audioRef: null },
                    },
                  }
                : n,
            ),
          );
        },
        error: err => this._error.set(err?.message ?? 'Erro ao recusar frase.'),
      });
  }

  /** Emit a node-selection audit event (fire-and-forget, non-blocking).
   *  Voice playback is the caller's responsibility (CONV-FE-013/ADR-017). */
  selectNode(nodeId: string, locale: Locale): void {
    this.treePort.selectNode(nodeId, locale)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ error: () => { /* audit failure is non-blocking */ } });
  }
}
