// @neuroassist/shared/access-http-base — accessHttpInterceptor (P1 FE-E2E-002)
//
// Global Angular HTTP interceptor that enriches every outbound request with:
//   X-Empresa-Id       <tenant-uuid>    — from EMPRESA_ID_TOKEN signal, when resolved
//   X-Correlation-Id   <uuid>           — generated per request
//   Idempotency-Key    <uuid>           — generated for POST/PUT requests
//
// Authorization is NOT this interceptor's job — that is authBearerInterceptorFn
// (@neuroassist/shared/auth-login), which reads AuthSessionStore and only attaches the
// bearer token to same-origin requests under a known backend prefix. Having two
// interceptors independently decide "should this request carry a token" is how a token
// leaks to a URL neither one meant to authorize. Register both in withInterceptors([...]);
// order between the two does not matter — HttpRequest is immutable, each clone preserves
// the previous interceptor's headers.
//
// X-Empresa-Id exemption: all /oauth2/** paths (no active tenant at authentication time).
//
// X-Empresa-Id is sent for traceability/debug only — isolation is enforced by the JWT
// `tenant_id` claim server-side, never by this header (docs/architecture/shared/
// multi-empresa.md: "redundante com JWT, ajuda em debug/log"). A null empresa id simply
// omits the header; it does not block the request. Consistent with EligibilityHttpAdapter
// (@neuroassist/gest), which follows the same rule.
//
// ProblemDetail (RFC 7807): HTTP errors with a ProblemDetail body are converted to
//   AppError from @neuroassist/shared/error-model.
//
// 401 handling: AuthSessionStore has no refresh token / refresh() capability today (by
// design — see auth-session.ts) — a session simply expires and route guards redirect to
// /login. This interceptor does not attempt a silent refresh-and-replay; it maps the 401
// like any other HTTP error and lets it propagate. If silent refresh becomes a
// requirement, it belongs in AuthSessionStore first (it owns the token lifecycle), not here.

import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import type { AppError } from '@neuroassist/shared/error-model';
import { EMPRESA_ID_TOKEN } from './access-http-base';

// ---------------------------------------------------------------------------
// UUID generation — crypto.randomUUID() with graceful fallback for jsdom
// ---------------------------------------------------------------------------

function makeUuid(): string {
  if (
    typeof crypto !== 'undefined' &&
    typeof (crypto as Crypto).randomUUID === 'function'
  ) {
    return (crypto as Crypto).randomUUID();
  }
  // Fallback for environments that lack SubtleCrypto (test runners, older jsdom)
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

// ---------------------------------------------------------------------------
// URL exemption rules
// ---------------------------------------------------------------------------

/** True for public endpoints that do not require X-Empresa-Id. */
function isEmpresaExempt(url: string): boolean {
  return /\/oauth2\//.test(url);
}

// ---------------------------------------------------------------------------
// ProblemDetail → AppError mapping (RFC 7807)
// ---------------------------------------------------------------------------

function mapToAppError(err: HttpErrorResponse): AppError {
  const body = err.error as Record<string, unknown> | null;
  if (body && typeof body === 'object' && ('title' in body || 'detail' in body)) {
    return {
      code: String(err.status),
      message:
        (body['detail'] as string | undefined) ??
        (body['title'] as string | undefined) ??
        err.message,
      type: body['type'] as string | undefined,
    };
  }
  return { code: String(err.status), message: err.message };
}

// ---------------------------------------------------------------------------
// Interceptor
// ---------------------------------------------------------------------------

export const accessHttpInterceptor: HttpInterceptorFn = (req, next) => {
  // inject() is valid inside HttpInterceptorFn bodies — Angular provides the
  // injection context via the interceptor chain.
  const empresaIdSignal = inject(EMPRESA_ID_TOKEN);

  const url = req.url;

  // ── Build header map ──────────────────────────────────────────────────────
  const newHeaders: Record<string, string> = {
    'X-Correlation-Id': makeUuid(),
  };

  // X-Empresa-Id — traceability only; a null id simply omits the header.
  if (!isEmpresaExempt(url)) {
    const empresaId = empresaIdSignal();
    if (empresaId) {
      newHeaders['X-Empresa-Id'] = empresaId;
    }
  }

  // Idempotency-Key for state-mutating requests
  if (req.method === 'POST' || req.method === 'PUT') {
    newHeaders['Idempotency-Key'] = makeUuid();
  }

  const enriched = req.clone({ setHeaders: newHeaders });

  // ── Forward and map errors ────────────────────────────────────────────────
  return next(enriched).pipe(
    catchError((httpErr: unknown) => {
      if (!(httpErr instanceof HttpErrorResponse)) {
        return throwError(() => httpErr);
      }
      return throwError(() => mapToAppError(httpErr));
    }),
  );
};
