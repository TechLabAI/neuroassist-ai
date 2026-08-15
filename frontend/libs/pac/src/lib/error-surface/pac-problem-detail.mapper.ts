import { AppError } from '@neuroassist/shared/error-model';

// ---------------------------------------------------------------------------
// PacProblemDetailMapper — PAC-FE-021 (referenced as PAC-FE-013 scope)
// Maps BE ProblemDetail (RFC 7807) responses to the FE AppError model.
//
// 12 ProblemDetail types per SPEC-07 §1 and api-contract §2.
// The 'type' URI suffix identifies the error code; the mapper extracts it
// and provides a default PT-BR message.
// ---------------------------------------------------------------------------

/** RFC 7807 ProblemDetail shape (BE response body for 4xx/5xx). */
export interface ProblemDetail {
  readonly type?: string;           // URI — e.g. '/errors/emergency-not-detectable'
  readonly title?: string;
  readonly status: number;
  readonly detail?: string;
  readonly instance?: string;
}

/** Canonical PT-BR messages per error code (user-facing). */
const DEFAULT_MESSAGES: Record<string, string> = {
  'validation':                  'Os dados informados são inválidos. Verifique os campos e tente novamente.',
  'missing-tenant':              'Esta operação requer um tenant configurado. Entre em contato com o administrador.',
  'tenant-mismatch':             'Você não tem acesso a este recurso. O tenant não corresponde à sua sessão.',
  'unauthorized':                'Sessão expirada. Faça login novamente para continuar.',
  'insufficient-scope':          'Você não tem permissão para realizar esta ação.',
  'not-found':                   'O recurso solicitado não foi encontrado.',
  'conflict-precondition':       'Este recurso foi modificado por outra sessão. Recarregue a página e tente novamente.',
  'idempotency-mismatch':        'Requisição duplicada detectada. Recarregue a página e tente novamente.',
  'invalid-lifecycle-transition':'Esta ação não é permitida no estado atual do comando.',
  'duplicate-active-mapping':    'Este gesto já está mapeado para outro comando ativo.',
  'emergency-not-detectable':    'Este comando de emergência requer um gesto reconhecível pelo motor de detecção. Verifique o catálogo de gestos com a equipe técnica.',
  'eligibility-gate-unavailable':'O serviço de verificação de elegibilidade está temporariamente indisponível. Tente novamente em instantes.',
};

/**
 * Extracts the error code from a ProblemDetail type URI.
 * '/errors/emergency-not-detectable' → 'emergency-not-detectable'
 * 'emergency-not-detectable'        → 'emergency-not-detectable'
 */
export function extractErrorCode(type: string | undefined): string {
  if (!type) return 'unknown';
  const parts = type.split('/');
  return parts[parts.length - 1] || 'unknown';
}

/**
 * Maps a BE ProblemDetail to an FE AppError.
 * Uses the 'detail' field if present for user-facing message,
 * otherwise falls back to the canonical PT-BR message for the code,
 * then to 'title'.
 */
export function mapProblemDetailToAppError(pd: ProblemDetail): AppError {
  const code = extractErrorCode(pd.type);
  const message = pd.detail ?? DEFAULT_MESSAGES[code] ?? pd.title ?? 'Erro desconhecido.';
  return { code, message };
}
