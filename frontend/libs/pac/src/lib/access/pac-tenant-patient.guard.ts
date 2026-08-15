import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { filter, map, take } from 'rxjs';
import { PacPacienteFacade } from '../pac-paciente.facade';

// ---------------------------------------------------------------------------
// pacTenantPatientGuard — SPEC-01 §3
// Ensures the active tenant has a registered Paciente before allowing catalog
// access. If none found, redirects to onboarding (step 2 — patient register).
//
// Async guard: returns Observable<UrlTree | true> so Angular Router waits for
// the patient fetch to complete before resolving the route. This removes the
// race condition where the mock adapter delay (400ms) caused the synchronous
// guard to see hasPatient()=false and redirect to /onboarding even when the
// patient exists.
//
// TODO[PAC-FE-014]: inject @neuroassist/shared/empresa-context to read
// the active empresa id. For now, loads the first patient and checks presence.
// ---------------------------------------------------------------------------

export const pacTenantPatientGuard: CanActivateFn = () => {
  const facade = inject(PacPacienteFacade);
  const router = inject(Router);

  // Fast path: already loaded and there is a patient — no async work needed.
  if (!facade.loading() && facade.hasPatient()) return true;

  // Trigger the patient fetch only if not already in progress.
  if (!facade.loading()) {
    facade.loadCurrent();
  }

  // Wait for loading to settle, then evaluate. toObservable() is valid here
  // because CanActivateFn runs inside an injection context.
  return toObservable(facade.loading).pipe(
    filter((loading) => !loading),
    take(1),
    map(() => (facade.hasPatient() ? true : router.createUrlTree(['/onboarding']))),
  );
};
