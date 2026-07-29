import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { PacPacienteFacade } from '../pac-paciente.facade';

// ---------------------------------------------------------------------------
// pacTenantPatientGuard — SPEC-01 §3
// Ensures the active tenant has a registered Paciente before allowing catalog
// access. If none found, redirects to onboarding (step 2 — patient register).
//
// TODO[PAC-FE-014]: inject @neuroassist/shared/empresa-context to read
// the active empresa id. For now, loads the first patient and checks presence.
// ---------------------------------------------------------------------------

export const pacTenantPatientGuard: CanActivateFn = () => {
  const facade = inject(PacPacienteFacade);
  const router = inject(Router);

  // Load current patient state if not already loaded
  facade.loadCurrent();

  // TODO[PAC-FE-014]: wait for loadCurrent() to resolve before evaluating.
  // For MVP (mock adapter), data is synchronous after construction; this will
  // need to become an async guard (Observable<UrlTree | true>) in Layer 2.
  const hasPatient = facade.hasPatient();
  return hasPatient ? true : router.createUrlTree(['/onboarding']);
};
