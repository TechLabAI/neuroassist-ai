import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { EDGE_AUTH_SESSION } from './edge-access-tokens';

// ---------------------------------------------------------------------------
// edgeAuthGuard — SPEC-01 §3 / SPEC-05 §3 (FE-EDGE-015)
//
// Verifies that a valid session token is present before allowing access to
// any /edge/* route. On failure: redirects to / (shell root, which typically
// shows the login page or the default home for unauthenticated users).
//
// Wave-0 bypass: EDGE_AUTH_SESSION defaults to null when auth layer is not
// yet wired (factory: () => null). Guard passes through in dev and test
// environments until the auth service is promoted.
//
// To enforce authentication in production, provide the real service:
//   { provide: EDGE_AUTH_SESSION, useExisting: RealAuthService }
// ---------------------------------------------------------------------------

export const edgeAuthGuard: CanActivateFn = () => {
  const session = inject(EDGE_AUTH_SESSION, { optional: true });

  // Wave-0 bypass: auth session service not wired — allow through.
  if (session === null) {
    return true;
  }

  // Authentication check: token must be present and non-null.
  const isAuthenticated = session.token !== null;
  if (!isAuthenticated) {
    return inject(Router).createUrlTree(['/']);
  }

  return true;
};
