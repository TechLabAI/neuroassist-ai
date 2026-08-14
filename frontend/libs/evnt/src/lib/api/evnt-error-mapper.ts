import { HttpErrorResponse } from '@angular/common/http';
import { AppError } from '@neuroassist/shared/error-model';

// =============================================================================
// evnt-error-mapper — SPEC-07 (INT-FE-EVNT-006)
// Maps HttpErrorResponse or generic Error to AppError.
//
// When the response body is a ProblemDetail (RFC 7807), the `type` URI is stored
// in `code`, enabling callers to distinguish error variants:
//   '/errors/tenant-mismatch'  → GUARD-04 redirect without toast
//   '/errors/forbidden'        → ERR-02 inline message
//   '/errors/event-not-found'  → ERR-01 redirect + toast
//   '/errors/feedback-terminal'→ 409 — already confirmed (treat as success)
// =============================================================================

/** Partial shape of a ProblemDetail body (RFC 7807). */
interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
}

/**
 * Maps an unknown error (typically from HttpClient) to an AppError.
 *
 * - If `err` is an `HttpErrorResponse` whose body is a ProblemDetail object,
 *   the ProblemDetail `type` URI becomes `AppError.code` — giving callers a
 *   stable discriminant for each EVNT error variant (SPEC-07 §1).
 * - If `err` is a plain `Error`, code is `'EVNT_ERROR'`.
 * - Otherwise, code is `'EVNT_UNKNOWN'`.
 */
export function mapProblemDetailToAppError(err: unknown): AppError {
  if (err instanceof HttpErrorResponse && typeof err.error === 'object' && err.error !== null) {
    const pd = err.error as ProblemDetail;
    return {
      code: pd.type ?? `EVNT_HTTP_${err.status}`,
      message: pd.detail ?? pd.title ?? err.message,
    };
  }
  if (err instanceof Error) return { code: 'EVNT_ERROR', message: err.message };
  return { code: 'EVNT_UNKNOWN', message: 'Erro desconhecido' };
}
