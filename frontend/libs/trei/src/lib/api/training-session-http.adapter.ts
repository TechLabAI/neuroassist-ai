import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TrainingSessionPort } from './training-session.port';
import { StartSessionRequest, TrainingSession } from '../trei.types';

// =============================================================================
// TrainingSessionHttpAdapter — production adapter
// Active when provideTrei({ adapter: 'http' }) — requires trei-service + GWY.
// Base: /api/v1/trei/sessoes (GWY route, api-contract §2.1)
// X-Empresa-Id and X-Correlation-Id are injected by accessHttpInterceptor
// (@neuroassist/shared/access-http-base) — no manual header setup needed here.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class TrainingSessionHttpAdapter implements TrainingSessionPort {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/trei/sessoes';

  start(request: StartSessionRequest): Observable<TrainingSession> {
    return this.http.post<TrainingSession>(this.base, request);
  }
}
