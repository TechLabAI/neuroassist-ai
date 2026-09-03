// =============================================================================
// TreiErrorMapper spec — SPEC-07 G5 / plan-FE-TREI P2 (mandatory)
// Verifies that:
//   1. correlationId NEVER appears in AppError.message or AppError.code (G5)
//   2. Known type suffixes map to the correct FE code and caregiver-facing message
//   3. Unknown types produce a safe fallback (no internals exposed)
// =============================================================================

import { mapProblemDetailToAppError, mapHttpErrorToAppError } from './trei-error.mapper';
import { ProblemDetail } from '../trei.types';

describe('mapProblemDetailToAppError', () => {
  const correlationId = 'corr-12345-sensitive';

  function makeProblem(overrides: Partial<ProblemDetail> = {}): ProblemDetail {
    return {
      type: 'https://neuroassist.ai/problems/llm-unavailable',
      title: 'LLM service unavailable',
      status: 503,
      detail: 'Anthropic returned 503',
      correlationId,
      ...overrides,
    };
  }

  // ── G5 invariant ────────────────────────────────────────────────────────────

  it('G5: correlationId MUST NOT appear in AppError.message', () => {
    const error = mapProblemDetailToAppError(makeProblem());
    expect(error.message).not.toContain(correlationId);
  });

  it('G5: correlationId MUST NOT appear in AppError.code', () => {
    const error = mapProblemDetailToAppError(makeProblem());
    expect(error.code).not.toContain(correlationId);
  });

  it('G5: correlationId IS stored in AppError.correlationId for support', () => {
    const error = mapProblemDetailToAppError(makeProblem());
    expect(error.correlationId).toBe(correlationId);
  });

  it('G5: upstream detail (Anthropic/ES internals) MUST NOT appear in message', () => {
    const error = mapProblemDetailToAppError(
      makeProblem({ detail: 'Anthropic returned 503: rate limit exceeded on gpt-4-turbo' }),
    );
    expect(error.message).not.toContain('Anthropic');
    expect(error.message).not.toContain('gpt-4-turbo');
  });

  // ── Known type mappings ─────────────────────────────────────────────────────

  it('maps llm-unavailable → LLM_UNAVAILABLE with retriable caregiver message', () => {
    const error = mapProblemDetailToAppError(makeProblem({
      type: 'https://neuroassist.ai/problems/llm-unavailable',
    }));
    expect(error.code).toBe('LLM_UNAVAILABLE');
    expect(error.message).toContain('tente novamente');
  });

  it('maps patient-not-found → PATIENT_NOT_FOUND', () => {
    const error = mapProblemDetailToAppError(makeProblem({
      type: 'https://neuroassist.ai/problems/patient-not-found',
      status: 404,
    }));
    expect(error.code).toBe('PATIENT_NOT_FOUND');
    expect(error.message).toContain('Paciente não encontrado');
  });

  it('maps forbidden-scope → FORBIDDEN_SCOPE', () => {
    const error = mapProblemDetailToAppError(makeProblem({
      type: 'https://neuroassist.ai/problems/forbidden-scope',
      status: 403,
    }));
    expect(error.code).toBe('FORBIDDEN_SCOPE');
    expect(error.message).toContain('permissão');
  });

  it('maps already-published → ALREADY_PUBLISHED', () => {
    const error = mapProblemDetailToAppError(makeProblem({
      type: 'https://neuroassist.ai/problems/already-published',
      status: 409,
    }));
    expect(error.code).toBe('ALREADY_PUBLISHED');
    expect(error.message).toContain('publicada');
  });

  it('maps node-not-found → NODE_NOT_FOUND', () => {
    const error = mapProblemDetailToAppError(makeProblem({
      type: 'https://neuroassist.ai/problems/node-not-found',
      status: 404,
    }));
    expect(error.code).toBe('NODE_NOT_FOUND');
  });

  it('maps invalid-request → INVALID_REQUEST', () => {
    const error = mapProblemDetailToAppError(makeProblem({
      type: 'https://neuroassist.ai/problems/invalid-request',
      status: 400,
    }));
    expect(error.code).toBe('INVALID_REQUEST');
  });

  // ── Unknown type fallback ───────────────────────────────────────────────────

  it('falls back to TREI_<status> code for unknown type suffix', () => {
    const error = mapProblemDetailToAppError(makeProblem({
      type: 'https://neuroassist.ai/problems/some-new-unknown-error',
      status: 500,
    }));
    expect(error.code).toBe('TREI_500');
  });

  it('uses title for message when type is unknown (no internal detail)', () => {
    const error = mapProblemDetailToAppError(makeProblem({
      type: 'https://neuroassist.ai/problems/unknown',
      title: 'Internal server error',
      status: 500,
    }));
    // must use title (safe), not detail (may contain internals)
    expect(error.message).toBe('Internal server error');
    expect(error.message).not.toContain('detail');
  });

  // ── Status propagation ──────────────────────────────────────────────────────

  it('propagates HTTP status to AppError.status', () => {
    const error = mapProblemDetailToAppError(makeProblem({ status: 404 }));
    expect(error.status).toBe(404);
  });
});

describe('mapHttpErrorToAppError', () => {
  it('delegates to mapProblemDetailToAppError when body has title', () => {
    const body: ProblemDetail = {
      type: 'https://neuroassist.ai/problems/llm-unavailable',
      title: 'LLM unavailable',
      status: 503,
      correlationId: 'corr-abc',
    };
    const error = mapHttpErrorToAppError(503, body);
    expect(error.code).toBe('LLM_UNAVAILABLE');
    // G5: correlationId stays in correlationId, not message
    expect(error.message).not.toContain('corr-abc');
  });

  it('falls back to HTTP_<status> when body is not a ProblemDetail', () => {
    const error = mapHttpErrorToAppError(502, '<html>Bad Gateway</html>');
    expect(error.code).toBe('HTTP_502');
    expect(error.message).toBe('Ocorreu um erro inesperado.');
  });

  it('uses custom fallback message', () => {
    const error = mapHttpErrorToAppError(500, null, 'Serviço indisponível');
    expect(error.message).toBe('Serviço indisponível');
  });
});
