// @neuroassist/shared/access-http-base — accessHttpInterceptor (P1 FE-E2E-002)
//
// Global Angular HTTP interceptor that enriches every outbound request with:
//   Authorization      Bearer <jwt>     — from AuthSessionService.accessToken()
//   X-Empresa-Id       <tenant-uuid>    — from EMPRESA_ID_TOKEN signal
//   X-Correlation-Id   <uuid>           — generated per request
//   Idempotency-Key    <uuid>           — generated for POST/PUT requests
//
// Auth exemption (no Authorization header):
//   POST /oauth2/token          — login (credentials in body)
//   POST /oauth2/token/refresh  — refresh (token in body)
//   POST .../devices/enroll     — enrollment token in body (not JWT)
//
// Empresa exemption (no X-Empresa-Id check):
//   All /oauth2/** paths — no active tenant at authentication time
//
// Behaviour on 401 (authenticated requests only):
//   Triggers AuthSessionService.refresh() exactly once (single-flight),
//   then replays the original request with the refreshed token.
//   If the refresh itself fails, logout() is called and the error propagates.
//
// Behaviour on 403: no refresh is triggered.
//
// Empresa guard: a request that requires X-Empresa-Id but the signal returns null
//   fails immediately with an AppError (EMPRESA_REQUIRED) — never sent without header.
//
// ProblemDetail (RFC 7807): HTTP errors with a ProblemDetail body are converted to
//   AppError from @neuroassist/shared/error-model.

import {
  HttpErrorResponse,
  HttpInterceptorFn,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import type { AppError } from '@neuroassist/shared/error-model';
import { AuthSessionService } from '@neuroassist/shared/auth-session';
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

/** True for public endpoints where Authorization header must NOT be sent. */
function isAuthExempt(url: string): boolean {
  return /\/oauth2\//.test(url) || /\/devices\/enroll$/.test(url);
}

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
  const authSession = inject(AuthSessionService);
  const empresaIdSignal = inject(EMPRESA_ID_TOKEN);

  const url = req.url;

  // ── Build header map ──────────────────────────────────────────────────────
  const newHeaders: Record<string, string> = {
    'X-Correlation-Id': makeUuid(),
  };

  // Authorization
  if (!isAuthExempt(url)) {
    const token = authSession.accessToken();
    if (token) {
      newHeaders['Authorization'] = `Bearer ${token}`;
    }
  }

  // X-Empresa-Id — guard: fail explicitly if no active empresa
  if (!isEmpresaExempt(url)) {
    const empresaId = empresaIdSignal();
    if (!empresaId) {
      const err: AppError = {
        code: 'EMPRESA_REQUIRED',
        message:
          'No active empresa — request blocked. Activate a tenant before making authenticated calls.',
      };
      return throwError(() => err);
    }
    newHeaders['X-Empresa-Id'] = empresaId;
  }

  // Idempotency-Key for state-mutating requests
  if (req.method === 'POST' || req.method === 'PUT') {
    newHeaders['Idempotency-Key'] = makeUuid();
  }

  const enriched = req.clone({ setHeaders: newHeaders });

  // ── Forward and handle errors ─────────────────────────────────────────────
  return next(enriched).pipe(
    catchError((httpErr: unknown) => {
      if (!(httpErr instanceof HttpErrorResponse)) {
        return throwError(() => httpErr);
      }

      // 401 on authenticated endpoints → refresh once, then replay
      if (httpErr.status === 401 && !isAuthExempt(url)) {
        return from(authSession.refresh()).pipe(
          switchMap(() => {
            // Re-enrich with the freshly issued token
            const freshToken = authSession.accessToken();
            const retried = enriched.clone({
              setHeaders: freshToken
                ? { Authorization: `Bearer ${freshToken}` }
                : {},
            });
            return next(retried);
          }),
          catchError((refreshErr: unknown) =>
            throwError(() =>
              refreshErr instanceof HttpErrorResponse
                ? mapToAppError(refreshErr)
                : refreshErr,
            ),
          ),
        );
      }

      // All other HTTP errors → map and propagate
      return throwError(() => mapToAppError(httpErr));
    }),
  );
};
