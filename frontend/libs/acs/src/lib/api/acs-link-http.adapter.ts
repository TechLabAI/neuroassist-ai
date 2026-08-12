import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AcsLinkPort, CaregiverLinkDto } from './acs-link.port';

// =============================================================================
// AcsLinkHttpAdapter — production adapter (stub compilable)
// Base path: /api/v1/acs/caregivers/{cid}/links
// 409 from BE = last-covering-block (ADR-005) — facade maps to user-visible modal.
// TODO[INT-FE-ACS-003]: wire once acs-service is deployed.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class AcsLinkHttpAdapter implements AcsLinkPort {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/acs/caregivers';

  listForCaregiver(cuidadorId: string): Observable<CaregiverLinkDto[]> {
    return this.http.get<CaregiverLinkDto[]>(`${this.base}/${cuidadorId}/links`);
  }

  link(cuidadorId: string, pacienteId: string): Observable<CaregiverLinkDto> {
    return this.http.post<CaregiverLinkDto>(`${this.base}/${cuidadorId}/links`, { pacienteId });
  }

  revoke(cuidadorId: string, linkId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${cuidadorId}/links/${linkId}`);
  }
}
