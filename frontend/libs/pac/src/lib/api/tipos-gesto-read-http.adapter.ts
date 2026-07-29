import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TiposGestoReadPort, TipoGesto } from './tipos-gesto-read.port';

// ---------------------------------------------------------------------------
// TiposGestoReadHttpAdapter — stub compilável (Layer 2 implementa os corpos).
// Base path (via GWY): /api/gest/v1/tipos-gesto
// Cross-module read — GEST owns these primitives; PAC delegates naming to GEST.
// Degradation: if GEST unreachable, the facade catches the error and falls
// back to an empty list with a visible "tipos indisponíveis" hint (SPEC-07).
// ---------------------------------------------------------------------------

@Injectable()
export class TiposGestoReadHttpAdapter implements TiposGestoReadPort {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/gest/v1/tipos-gesto';

  list(): Observable<TipoGesto[]> {
    // TODO[PAC-INT-001]: implement — GEST endpoint (read-only cross-module call via GWY)
    return this.http.get<TipoGesto[]>(this.base);
  }

  get(id: string): Observable<TipoGesto> {
    // TODO[PAC-INT-001]: implement
    return this.http.get<TipoGesto>(`${this.base}/${id}`);
  }

  requestNew(nome: string): Observable<TipoGesto> {
    // TODO[PAC-INT-002]: delegate to GEST (POST /api/gest/v1/tipos-gesto)
    // Naming ≠ detectability — GEST accepts the name but decides if/when to support it.
    return this.http.post<TipoGesto>(this.base, { nome });
  }
}
