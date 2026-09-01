import { Injectable } from '@angular/core';
import { Observable, of, delay } from 'rxjs';
import { ProposalPort } from './proposal.port';
import { NoArvoreFala, Locale } from '../conv.types';
import { MOCK_NOS_ARVORE_FALA } from './no-arvore-fala-mock.adapter';

// =============================================================================
// ProposalMockAdapter — MockAdapter only (CONV-INT-006 / G-2).
// The real endpoint belongs to TREI (future module). HttpAdapter is deferred.
// Simulates the patient-confirmation flow on the shared mock dataset.
// =============================================================================

@Injectable()
export class ProposalMockAdapter implements ProposalPort {
  /** Shared mutable reference to the mock dataset for within-session consistency.
   *  In a real app, a NgRx/signal store or re-fetch would sync state.
   *  Here the facade keeps the signal; mock just drives the mutations. */
  private nodes = MOCK_NOS_ARVORE_FALA;

  listPendingPatient(locale: Locale): Observable<NoArvoreFala[]> {
    const pending = this.nodes.filter(n => {
      const c = n.conteudo[locale];
      return c.confirmadoCuidadora && !c.confirmadoPaciente;
    });
    return of(pending.map(n => ({ ...n }))).pipe(delay(400));
  }

  confirmarPaciente(id: string, locale: Locale): Observable<void> {
    this.nodes = this.nodes.map(n => {
      if (n.id !== id) return n;
      const prev = n.conteudo[locale];
      const updated: typeof prev = {
        ...prev,
        confirmadoPaciente: true,
        // Simulate async audio-ref assignment on Dual-Authorization completion.
        audioRef: prev.confirmadoCuidadora
          ? `audio/${locale}/${prev.texto.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.mp3`
          : null,
      };
      return { ...n, conteudo: { ...n.conteudo, [locale]: updated } };
    });
    return of(undefined).pipe(delay(200));
  }

  recusar(id: string, locale: Locale): Observable<void> {
    this.nodes = this.nodes.map(n =>
      n.id === id
        ? {
            ...n,
            conteudo: {
              ...n.conteudo,
              [locale]: { ...n.conteudo[locale], confirmadoCuidadora: false, confirmadoPaciente: false, audioRef: null },
            },
          }
        : n,
    );
    return of(undefined).pipe(delay(200));
  }
}
