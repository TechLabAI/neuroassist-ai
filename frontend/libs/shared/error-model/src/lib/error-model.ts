// @neuroassist/shared/error-model — Wave-0 stub (D-328/FE-4); flesh out in the implement cycle.
export interface AppError {
  readonly code: string;
  readonly message: string;
  /** ProblemDetail `type` URI (RFC 7807) — discriminant for typed error handling.
   * Present when the error originates from an HttpErrorResponse with a ProblemDetail body.
   * Examples: '/errors/tenant-mismatch', '/errors/forbidden', '/errors/event-not-found'.
   * INT-FE-EVNT-006 / P6-C. */
  readonly type?: string;
}
