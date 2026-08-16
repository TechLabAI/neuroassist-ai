import { HttpErrorResponse } from '@angular/common/http';
import { mapProblemDetailToAppError } from './evnt-error-mapper';

// =============================================================================
// evnt-error-mapper spec — INT-FE-EVNT-006
// Validates that ProblemDetail (RFC 7807) type URIs are preserved in AppError.code
// AND AppError.type so that callers can distinguish error variants (GUARD-04, ERR-01, ERR-02).
// P6-C: two new tests verify AppError.type field population.
// =============================================================================

describe('mapProblemDetailToAppError', () => {
  it('maps ProblemDetail body to AppError with type URI as code', () => {
    const err = new HttpErrorResponse({
      error: {
        type: '/errors/tenant-mismatch',
        title: 'Tenant Mismatch',
        status: 403,
        detail: 'Evento não pertence ao tenant da sessão.',
      },
      status: 403,
      statusText: 'Forbidden',
    });

    const result = mapProblemDetailToAppError(err);

    expect(result.code).toBe('/errors/tenant-mismatch');
    expect(result.message).toBe('Evento não pertence ao tenant da sessão.');
  });

  it('maps generic Error to EVNT_ERROR code', () => {
    const err = new Error('Network failure');

    const result = mapProblemDetailToAppError(err);

    expect(result.code).toBe('EVNT_ERROR');
    expect(result.message).toBe('Network failure');
  });

  it('uses EVNT_HTTP_{status} code when ProblemDetail has no type field', () => {
    const err = new HttpErrorResponse({
      error: { title: 'Internal Server Error' },
      status: 500,
      statusText: 'Internal Server Error',
    });

    const result = mapProblemDetailToAppError(err);

    expect(result.code).toBe('EVNT_HTTP_500');
    expect(result.message).toBe('Internal Server Error');
  });

  it('maps non-Error unknown to EVNT_UNKNOWN code', () => {
    const result = mapProblemDetailToAppError('unexpected string error');

    expect(result.code).toBe('EVNT_UNKNOWN');
    expect(result.message).toBe('Erro desconhecido');
  });

  // P6-C: AppError.type field — 403 tenant-mismatch vs 403 forbidden distinction

  it('populates AppError.type with ProblemDetail type URI for tenant-mismatch (P6-C)', () => {
    const err = new HttpErrorResponse({
      error: {
        type: '/errors/tenant-mismatch',
        title: 'Tenant Mismatch',
        status: 403,
        detail: 'Evento pertence a outro tenant.',
      },
      status: 403,
      statusText: 'Forbidden',
    });

    const result = mapProblemDetailToAppError(err);

    expect(result.type).toBe('/errors/tenant-mismatch');
  });

  it('populates AppError.type with ProblemDetail type URI for forbidden (P6-C)', () => {
    const err = new HttpErrorResponse({
      error: {
        type: '/errors/forbidden',
        title: 'Forbidden',
        status: 403,
        detail: 'Você não tem permissão para este evento.',
      },
      status: 403,
      statusText: 'Forbidden',
    });

    const result = mapProblemDetailToAppError(err);

    expect(result.type).toBe('/errors/forbidden');
  });
});
