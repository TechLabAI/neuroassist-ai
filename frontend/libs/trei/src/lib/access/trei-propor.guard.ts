import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { TREI_ACCESS_DECISION } from './trei-access-tokens';

// =============================================================================
// treiProporGuard — TREI-FE-023 (G1 crítico)
//
// Verifies that the authenticated caregiver holds the `treinamento.no.propor`
// authority before allowing access to the proposal review sub-route
// `/treinamento/sessao/propostas` (SPEC-01 §3 table).
//
// On failure: redirects to /treinamento/sessao — NOT to /dashboard, NOT to
// false. The session route stays reachable; this guard blocks only the
// materialize/edit proposal route (G1 invariant — SPEC-01 §3 note, SPEC-01
// §5 criteria).
//
// Authority name: `treinamento.no.propor` — the materialize/edit authority
// emitted by acs-service (empty-prefix format). Verified against SPEC-01 §3
// and TREI_MODULE_PERMISSION_MAP ('treinamento.propor' alias resolves to this
// literal — trei.descriptor.ts).
//
// Authorization design:
//   - Reads TREI_ACCESS_DECISION.can() (fail-closed default: always false).
//   - No Wave-0 bypass — can() → false → redirect to /treinamento/sessao (G1).
//   - caregiverSessionGuard + treiGuard run before this on the parent route.
//
// Dual-authorization context (ADR-007):
//   - `treinamento.no.propor` gates the proposal creation/editing route.
//   - `treinamento.no.aprovar.cuidadora` gates the approve action (in-surface).
//   - `treinamento.no.aprovar.paciente` is NEVER checked here — that belongs
//     to the patient shell / CONV (INV-1, ADR-007).
// =============================================================================

/**
 * Guards `/treinamento/sessao/propostas` — requires `treinamento.no.propor`.
 *
 * G1 invariant: redirect to /treinamento/sessao (not /dashboard, not false).
 */
export const treiProporGuard: CanActivateFn = () => {
  const access = inject(TREI_ACCESS_DECISION);
  const router = inject(Router);

  // G1: redirect to /treinamento/sessao — never 403, never false, never
  // /dashboard. The session route stays reachable when propor scope is absent.
  return access.can('treinamento.no.propor')
    ? true
    : router.createUrlTree(['/treinamento/sessao']);
};
