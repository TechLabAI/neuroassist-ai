import { HttpErrorResponse } from '@angular/common/http';
import { AppError } from '@neuroassist/shared/error-model';

// =============================================================================
// evnt-error-mapper — SPEC-07 (INT-FE-EVNT-006)
// Maps HttpErrorResponse or generic Error to AppError.
//
// When the response body is a ProblemDetail (RFC 7807):
//   - `code`  = pd.type (stable discriminant for callers — GUARD-04 / ERR-01 / ERR-02)
//   - `type`  = pd.type (same URI on the AppError.type field — P6-C)
//   - `message` = pd.detail ?? pd.title ?? http message
//
// The `type` field enables UI to distinguish 403 variants without string-parsing `code`:
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
 *   both `code` and `type` carry the ProblemDetail `type` URI — giving callers a
 *   stable discriminant for each EVNT error variant (SPEC-07 §1, P6-C).
 * - If `err` is a plain `Error`, code is `'EVNT_ERROR'`.
 * - Otherwise, code is `'EVNT_UNKNOWN'`.
 */
export function mapProblemDetailToAppError(err: unknown): AppError {
  if (err instanceof HttpErrorResponse && typeof err.error === 'object' && err.error !== null) {
    const pd = err.error as ProblemDetail;
    return {
      code: pd.type ?? `EVNT_HTTP_${err.status}`,
      type: pd.type,
      message: pd.detail ?? pd.title ?? err.message,
    };
  }
  if (err instanceof Error) return { code: 'EVNT_ERROR', message: err.message };
  return { code: 'EVNT_UNKNOWN', message: 'Erro desconhecido' };
}
