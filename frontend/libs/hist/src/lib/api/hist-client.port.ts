import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AuditRecordResponse,
  ChainVerificationResult,
  HistoryQueryParams,
  PagedResponse,
} from '../hist.types';

// =============================================================================
// HistClientPort — SPEC-04 §2 (history read surface)
// Abstraction over the hist-service History API (read-only, §3 api-contract).
// The FE never calls the ingestion surface (POST /audit-facts).
// =============================================================================

export interface HistClientPort {
  /**
   * GET /api/v1/hist/history — paginated timeline query.
   * Facade maps Period → from/to before calling this (SPEC-03 §5).
   */
  list(params: HistoryQueryParams): Observable<PagedResponse<AuditRecordResponse>>;

  /**
   * GET /api/v1/hist/audit-facts/{id} — single record (post-MVP detail).
   * Returns 404 if absent in caller's tenant.
   */
  get(id: string): Observable<AuditRecordResponse>;

  /**
   * GET /api/v1/hist/history/verify — chain verification (ADR-006).
   * Visible only to callers with hist:read:all scope.
   */
  verify(subjectType: string, subjectId: string): Observable<ChainVerificationResult>;
}

export const HIST_CLIENT_PORT = new InjectionToken<HistClientPort>('HistClientPort');
