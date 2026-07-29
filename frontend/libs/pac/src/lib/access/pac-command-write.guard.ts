import { CanActivateFn } from '@angular/router';

// ---------------------------------------------------------------------------
// pacCommandWriteGuard — SPEC-01 §3
// Verifies that the authenticated session has the pac.comando.write scope
// before allowing access to the command editor routes.
// BE also enforces scope — this guard provides UX-level early rejection.
//
// TODO[PAC-FE-015]: integrate with @neuroassist/shared/access-decision to
// evaluate the pac.comando.write permission against the active JWT scopes.
// For now, always allows (dev default; BE enforcement is the safety net).
// ---------------------------------------------------------------------------

export const pacCommandWriteGuard: CanActivateFn = () => {
  // TODO[PAC-FE-015]: inject AccessDecisionService
  // const decision = inject(AccessDecisionService);
  // return decision.hasScope('pac.comando.write') ? true : inject(Router).createUrlTree(['/pac']);
  return true;
};
