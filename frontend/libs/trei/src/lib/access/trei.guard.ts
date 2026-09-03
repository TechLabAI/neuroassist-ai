import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { TREI_ACCESS_DECISION } from './trei-access-tokens';

// =============================================================================
// treiGuard — TREI-FE-023
//
// Verifies that the authenticated caregiver holds the
// `treinamento.sessao.conduzir` authority before allowing access to the TREI
// module routes (SPEC-01 §3 table).
//
// On failure: redirects to /dashboard (caregiver home — not a patient surface).
//
// Authority name: `treinamento.sessao.conduzir` — the authoring/session
// authority emitted by acs-service as a fine-grained authority (empty-prefix
// format, CLAUDE.md §Cross-service contracts). Verified against SPEC-01 §3
// and TREI_MODULE_PERMISSION_MAP ('treinamento.conduzir' alias resolves to
// this literal — trei.descriptor.ts).
//
// Authorization design:
//   - Reads TREI_ACCESS_DECISION.can() (fail-closed default: always false).
//   - No Wave-0 bypass — can() → false → redirect to /dashboard.
//   - caregiverSessionGuard (first in chain) ensures the user is authenticated;
//     this guard only verifies the specific TREI authority.
//   - `treinamento.no.aprovar.cuidadora` and `treinamento.no.descartar` are
//     NOT route guards — they gate actions within the review surface (SPEC-01
//     §3 note). This guard only gates module entry.
// =============================================================================

/**
 * Guards the /treinamento route subtree — requires `treinamento.sessao.conduzir`.
 * Redirects to /dashboard when the authority is absent.
 */
export const treiGuard: CanActivateFn = () => {
  const access = inject(TREI_ACCESS_DECISION);
  const router = inject(Router);

  return access.can('treinamento.sessao.conduzir')
    ? true
    : router.createUrlTree(['/dashboard']);
};
