// =============================================================================
// TreiErrorMapper — SPEC-07 §1-§2
// Maps RFC-7807 ProblemDetail (from trei-service via GWY) → AppError.
//
// INVARIANT (G5 / SPEC-07 §5):
//   correlationId NEVER appears in AppError.message or any caregiver-facing string.
//   It is stored in AppError.correlationId for observability (logs / support) only.
//
// Error code table follows SPEC-07 §2 (BE type suffix → FE code + message).
// =============================================================================

import { ProblemDetail, AppError } from '../trei.types';

// ---------------------------------------------------------------------------
// SPEC-07 §2 — BE type suffix → FE error code + default caregiver-facing message
// ---------------------------------------------------------------------------

const ERROR_CODE_MAP: Record<string, { code: string; message: string }> = {
  'llm-unavailable': {
    code: 'LLM_UNAVAILABLE',
    message: 'Falha ao falar com o assistente — tente novamente.',
  },
  'invalid-request': {
    code: 'INVALID_REQUEST',
    message: 'Revise a frase (não pode ficar vazia / muito longa).',
  },
  'no-biometric-ingress': {
    code: 'NO_BIOMETRIC_INGRESS',
    message: 'Conteúdo inválido para treinamento.',
  },
  'unauthenticated': {
    code: 'UNAUTHENTICATED',
    message: 'Sua sessão expirou. Entre novamente.',
  },
  'session-revoked': {
    code: 'SESSION_REVOKED',
    message: 'Sua sessão foi encerrada. Entre novamente.',
  },
  'forbidden-scope': {
    code: 'FORBIDDEN_SCOPE',
    message: 'Você não tem permissão para esta ação.',
  },
  'patient-not-found': {
    code: 'PATIENT_NOT_FOUND',
    message: 'Paciente não encontrado nesta empresa.',
  },
  'node-not-found': {
    code: 'NODE_NOT_FOUND',
    message: 'Esta proposta não está mais disponível.',
  },
  'already-published': {
    code: 'ALREADY_PUBLISHED',
    message: 'Esta frase já foi publicada e não pode ser descartada.',
  },
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Extracts the type suffix from a full RFC-7807 type URI.
 * E.g.: 'https://neuroassist.ai/problems/llm-unavailable' → 'llm-unavailable'
 */
function extractTypeSuffix(typeUri: string): string {
  const parts = typeUri.split('/');
  return parts[parts.length - 1] ?? typeUri;
}

// ---------------------------------------------------------------------------
// Public mapper function
// ---------------------------------------------------------------------------

/**
 * Maps a ProblemDetail (RFC 7807) from trei-service into an AppError suitable
 * for caregiver-facing rendering.
 *
 * SPEC-07 §5 / G5 guarantee:
 *   - correlationId is stored in AppError.correlationId for support/observability.
 *   - correlationId is NEVER interpolated into AppError.message or any visible string.
 *   - Upstream internals (Anthropic, ElevenLabs, ES) never appear in the message.
 */
export function mapProblemDetailToAppError(detail: ProblemDetail): AppError {
  const suffix = extractTypeSuffix(detail.type ?? '');
  const mapped = ERROR_CODE_MAP[suffix];

  return {
    code: mapped?.code ?? `TREI_${detail.status}`,
    // Use the mapped caregiver-facing message; fall back to title (never detail which may
    // contain internal stack traces or upstream service messages — SPEC-07 §5).
    message: mapped?.message ?? detail.title,
    status: detail.status,
    // correlationId stored here for logs/support; never rendered in the UI (G5).
    correlationId: detail.correlationId,
  };
}

/**
 * Maps an unknown HttpErrorResponse body → AppError.
 * Call this in HTTP adapters instead of mapProblemDetailToAppError when the error
 * shape is uncertain (network error, non-JSON body, etc.).
 */
export function mapHttpErrorToAppError(
  status: number,
  body: unknown,
  fallbackMessage = 'Ocorreu um erro inesperado.',
): AppError {
  if (body && typeof body === 'object' && 'title' in body) {
    return mapProblemDetailToAppError(body as ProblemDetail);
  }
  return {
    code: `HTTP_${status}`,
    message: fallbackMessage,
    status,
  };
}
