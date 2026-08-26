// @neuroassist/shared/error-model — Wave-0 stub (D-328/FE-4); flesh out in the implement cycle.
export interface AppError {
  readonly code: string;
  readonly message: string;
  /** ProblemDetail `type` URI (RFC 7807) — discriminant for typed error handling.
   * Present when the error originates from an HttpErrorResponse with a ProblemDetail body.
   * Examples: '/errors/tenant-mismatch', '/errors/forbidden', '/errors/event-not-found'.
   * INT-FE-EVNT-006 / P6-C. */
  readonly type?: string;
  /**
   * Erros por CAMPO, quando o backend os manda.
   *
   * <p>Um ProblemDetail de validação carrega `fieldErrors: [{field, message}]`, e
   * essa lista é o que permite dizer "Dispositivo desconhecido no catálogo EDGE"
   * ao lado do campo dispositivo, em vez de uma frase genérica no rodapé do
   * formulário. Nada no frontend modelava isso: a informação chegava e era
   * jogada fora, então toda falha de validação virava a mesma mensagem.
   */
  readonly fieldErrors?: readonly FieldError[];
}

/** Um erro atribuído a um campo do formulário. */
export interface FieldError {
  readonly field: string;
  readonly message: string;
}
