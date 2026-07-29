import { CanActivateFn } from '@angular/router';

// ---------------------------------------------------------------------------
// pacAuthGuard — SPEC-01 §3
// Verifies that the session has a valid JWT before allowing PAC navigation.
// Redirects to /login if unauthenticated.
// TODO[PAC-FE-013]: integrate with @neuroassist/shared/auth-session to check
// token validity and expiry. For now, always allows (dev default).
// ---------------------------------------------------------------------------

export const pacAuthGuard: CanActivateFn = () => {
  // TODO[PAC-FE-013]: inject AuthSessionService and validate
  // const auth = inject(AuthSessionService);
  // return auth.isAuthenticated() ? true : inject(Router).createUrlTree(['/login']);
  return true;
};
