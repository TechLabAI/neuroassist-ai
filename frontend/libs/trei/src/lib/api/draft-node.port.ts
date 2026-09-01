import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import {
  MaterializeRequest,
  DraftNodeCreated,
  NodeDetail,
  TreiLocale,
} from '../trei.types';

// =============================================================================
// DraftNodePort — §2.3, §2.6, §2.7 of api-contract
// Covers: materialize (POST /nos), inspect (GET /nos/{noId}), discard (DELETE /nos/{noId}?locale=)
// Caregiver scopes: treinamento.no.propor (materialize), treinamento.sessao.conduzir (inspect),
//                   treinamento.no.descartar (discard).
// INV-1: NO method for patient approval — that is CONV's domain (ADR-003/ADR-007 FE).
// =============================================================================

export interface DraftNodePort {
  /**
   * POST /api/v1/trei/nos — materialize a proposal (or hand-typed phrase) into a draft node.
   * 201 → DraftNodeCreated; node structure is locale-invariant (ADR-018).
   */
  materialize(request: MaterializeRequest): Observable<DraftNodeCreated>;

  /**
   * GET /api/v1/trei/nos/{noId} — inspect full authoring state (all locales, approval flags, G5).
   * 200 → NodeDetail; 404 → node-not-found.
   */
  inspect(noId: string): Observable<NodeDetail>;

  /**
   * DELETE /api/v1/trei/nos/{noId}?locale={locale} — discard a draft locale.
   * 200 → discarded; 409 → already-published (cannot discard an ativo locale).
   * Soft delete: retained for audit (ORG-001), conv_no_conteudo.status stays 'pendente'.
   */
  discard(noId: string, locale: TreiLocale): Observable<{ noId: string; locale: TreiLocale; subEstado: string }>;
}

export const DRAFT_NODE_PORT = new InjectionToken<DraftNodePort>('DraftNodePort');
