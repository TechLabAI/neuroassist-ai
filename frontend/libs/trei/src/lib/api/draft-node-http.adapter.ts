import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DraftNodePort } from './draft-node.port';
import {
  MaterializeRequest,
  DraftNodeCreated,
  NodeDetail,
  TreiLocale,
} from '../trei.types';

// =============================================================================
// DraftNodeHttpAdapter — production adapter (stub)
// TODO[INT-FE-TREI-003]: activate when trei-service is deployed.
// api-contract: §2.3 (POST /nos), §2.6 (GET /nos/{noId}), §2.7 (DELETE /nos/{noId}?locale=)
// =============================================================================

@Injectable({ providedIn: 'root' })
export class DraftNodeHttpAdapter implements DraftNodePort {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/trei/nos';

  materialize(request: MaterializeRequest): Observable<DraftNodeCreated> {
    return this.http.post<DraftNodeCreated>(this.base, request);
  }

  inspect(noId: string): Observable<NodeDetail> {
    return this.http.get<NodeDetail>(`${this.base}/${noId}`);
  }

  discard(noId: string, locale: TreiLocale): Observable<{ noId: string; locale: TreiLocale; subEstado: string }> {
    return this.http.delete<{ noId: string; locale: TreiLocale; subEstado: string }>(
      `${this.base}/${noId}`,
      { params: { locale } },
    );
  }
}
