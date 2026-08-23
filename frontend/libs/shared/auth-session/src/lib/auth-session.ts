// @neuroassist/shared/auth-session — types (P1 FE-E2E-003)
//
// AuthClaims are derived from the ACS JWT (TokenIssuer.java):
//   sub         = caregiver UUID
//   empresaId   = tenant UUID (claim "empresaId" — alias for tenant_id/tenant)
//   scopes      = string[] from "authorities" claim (NOT "scope")
//
// Storage decision (LGPD/health data):
//   sessionStorage is chosen over localStorage — tokens are cleared on tab close
//   and never persist across browser sessions (health data must not outlive the
//   session). In-memory-only would be cleared on page refresh (unacceptable UX).
//   Cookie httpOnly + backend session endpoint is not yet available. sessionStorage
//   is the pragmatic LGPD-compliant choice for this phase.

export interface AuthTokens {
  readonly accessToken: string;
  readonly refreshToken: string;
  readonly expiresAt: number; // Unix timestamp (ms)
}

export interface AuthClaims {
  readonly sub: string;              // caregiver UUID from JWT "sub" claim
  readonly empresaId: string;        // tenant UUID from JWT "empresaId" claim
  readonly scopes: readonly string[]; // from JWT "authorities" claim (e.g. "hist:read", "pac.paciente.read")
}
