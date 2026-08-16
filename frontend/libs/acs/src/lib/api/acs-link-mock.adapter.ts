import { Injectable } from '@angular/core';
import { Observable, of, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { AcsLinkPort, CaregiverLinkDto } from './acs-link.port';

// =============================================================================
// AcsLinkMockAdapter — dev/test (default adapter)
// Seed: Jamilly linked to Márcio (matches prototype state).
// ADR-005: revoke of last covering link is blocked by mock (throws 409-equivalent).
//
// _knownPatientIds: set of patient IDs that the mock recognises as valid.
// link() rejects unknown patient IDs with a 404-equivalent error, enabling e2e
// scenario 5 ("Linking an unknown paciente reference shows an inline error").
// =============================================================================

// Known patient IDs in the e2e seed. link() rejects IDs not in this set.
const _knownPatientIds = new Set([
  'pac-marcio',
  'pac-novo',
  'pac-outro',
  'pac-sem-cuidador',
  'pac-inativo-linked',
]);

const _links = new Map<string, CaregiverLinkDto>([
  ['link-001', {
    id: 'link-001',
    cuidadorId: 'cuid-jamilly',
    pacienteId: 'pac-marcio',
    active: true,
    createdAt: '2026-07-26T23:49:32Z',
  }],
  // link-002: Maria Santos → pac-marcio
  //   Enables: non-last-covering deactivation (roster scenario 20) and
  //            non-last unlink re-derives coverage (linkage scenario 6).
  //   With both Jamilly and Maria linked to pac-marcio, deactivating/unlinking one
  //   leaves the other covering → no ADR-005 block triggered.
  ['link-002', {
    id: 'link-002',
    cuidadorId: 'cuid-maria',
    pacienteId: 'pac-marcio',
    active: true,
    createdAt: '2026-07-26T23:50:00Z',
  }],
  // link-003: Maria Santos → pac-outro
  //   Enables: last-covering-unlink blocked by ADR-005 (linkage scenario 7).
  //   pac-outro is covered only by Maria → revoking is blocked (last covering).
  //   pac-novo is intentionally left without links so scenario 2 (link a candidate)
  //   keeps its "sem cobertura initially" precondition.
  ['link-003', {
    id: 'link-003',
    cuidadorId: 'cuid-maria',
    pacienteId: 'pac-outro',
    active: true,
    createdAt: '2026-07-26T23:50:30Z',
  }],
  // link-inativo: cuid-inativo (inactive caregiver) → pac-inativo-linked
  //   Enables: e2e scenario 10 — "An inactive linked caregiver is excluded from the
  //   paciente coverage count". _refreshCoverageMap() only processes active caregivers,
  //   so cuid-inativo is skipped and pac-inativo-linked has no coverage → sem-cobertura.
  ['link-inativo', {
    id: 'link-inativo',
    cuidadorId: 'cuid-inativo',
    pacienteId: 'pac-inativo-linked',
    active: true,
    createdAt: '2026-07-26T23:51:00Z',
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
        // Validate patient ID — enables e2e scenario 5 (unknown paciente reference).
        if (!_knownPatientIds.has(pacienteId)) {
          throw Object.assign(new Error('Paciente não encontrado.'), { status: 404 });
        }

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
