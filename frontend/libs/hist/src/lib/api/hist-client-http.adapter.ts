import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { HistClientPort } from './hist-client.port';
import {
  AuditRecordResponse,
  ChainVerificationResult,
  HistoryQueryParams,
  PagedResponse,
} from '../hist.types';

// =============================================================================
// HistClientHttpAdapter — production adapter (stub, compilable)
// Base: /api/v1/hist — routed through GWY, discovered via REG (api-contract §1).
// Switch via provideHist({ adapter: 'http' }) in app.config.ts.
// TODO[INT-FE-HIST-001]: activate once hist-service + GWY route is deployed.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class HistClientHttpAdapter implements HistClientPort {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/hist';

  list(params: HistoryQueryParams): Observable<PagedResponse<AuditRecordResponse>> {
    let httpParams = new HttpParams()
      .set('from', params.from)
      .set('to', params.to)
      .set('order', params.order ?? 'desc')
      .set('page', String(params.page ?? 0))
      .set('size', String(params.size ?? 50));

    if (params.subjectType && params.subjectId) {
      httpParams = httpParams
        .set('subjectType', params.subjectType)
        .set('subjectId', params.subjectId);
    }
    if (params.factType) {
      httpParams = httpParams.set('factType', params.factType);
    }
    if (params.actorCaregiverId) {
      httpParams = httpParams.set('actorCaregiverId', params.actorCaregiverId);
    }

    return this.http.get<PagedResponse<AuditRecordResponse>>(`${this.base}/history`, {
      params: httpParams,
    });
  }

  get(id: string): Observable<AuditRecordResponse> {
    return this.http.get<AuditRecordResponse>(`${this.base}/audit-facts/${id}`);
  }

  verify(
    subjectType: string,
    subjectId: string,
  ): Observable<ChainVerificationResult> {
    const params = new HttpParams()
      .set('subjectType', subjectType)
      .set('subjectId', subjectId);
    return this.http.get<ChainVerificationResult>(`${this.base}/history/verify`, { params });
  }
}
