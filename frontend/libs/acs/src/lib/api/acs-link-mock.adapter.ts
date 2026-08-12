import { Injectable } from '@angular/core';
import { Observable, of, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { AcsLinkPort, CaregiverLinkDto } from './acs-link.port';

// =============================================================================
// AcsLinkMockAdapter — dev/test (default adapter)
// Seed: Jamilly linked to Márcio (matches prototype state).
// ADR-005: revoke of last covering link is blocked by mock (throws 409-equivalent).
// =============================================================================

const _links = new Map<string, CaregiverLinkDto>([
  ['link-001', {
    id: 'link-001',
    cuidadorId: 'cuid-jamilly',
    pacienteId: 'pac-marcio',
    active: true,
    createdAt: '2026-07-26T23:49:32Z',
  }],
]);

let _idSeq = 1;

@Injectable({ providedIn: 'root' })
export class AcsLinkMockAdapter implements AcsLinkPort {
  listForCaregiver(cuidadorId: string): Observable<CaregiverLinkDto[]> {
    return timer(400).pipe(
      switchMap(() =>
        of(Array.from(_links.values()).filter((l) => l.cuidadorId === cuidadorId)),
      ),
    );
  }

  link(cuidadorId: string, pacienteId: string): Observable<CaregiverLinkDto> {
    return timer(600).pipe(
      switchMap(() => {
        const existing = Array.from(_links.values()).find(
          (l) => l.cuidadorId === cuidadorId && l.pacienteId === pacienteId && l.active,
        );
        if (existing) return of(existing); // idempotent

        const id = `link-${Date.now()}-${++_idSeq}`;
        const newLink: CaregiverLinkDto = {
          id,
          cuidadorId,
          pacienteId,
          active: true,
          createdAt: new Date().toISOString(),
        };
        _links.set(id, newLink);
        return of(newLink);
      }),
    );
  }

  revoke(cuidadorId: string, linkId: string): Observable<void> {
    return timer(600).pipe(
      switchMap(() => {
        const link = _links.get(linkId);
        if (!link) throw new Error(`mock: link not found: ${linkId}`);

        // ADR-005: block revoke if this is the last active link for the patient
        const activeLinksForPatient = Array.from(_links.values()).filter(
          (l) => l.pacienteId === link.pacienteId && l.active && l.id !== linkId,
        );
        if (activeLinksForPatient.length === 0) {
          // Simulate 409 last-covering-block
          throw Object.assign(new Error('Não é possível remover o último vínculo ativo para este paciente.'), {
            status: 409,
            type: '/errors/last-covering-caregiver',
          });
        }

        _links.set(linkId, { ...link, active: false });
        return of(undefined as void);
      }),
    );
  }
}
