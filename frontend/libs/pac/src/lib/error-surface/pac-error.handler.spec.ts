import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { PacErrorHandler } from './pac-error.handler';
import { PacFeedbackService } from './pac-feedback.service';

// =============================================================================
// PacErrorHandler — unit tests (PAC-FE-021 / P7 UX Polish)
// Covers all 12 ProblemDetail error codes → correct UX surface routing.
//
// Acceptance criteria per SPEC-07 §1/§2/§3/§4:
//   - 'emergency-not-detectable' → showModal({ blocking: true }) SAFETY-CRITICAL
//   - 'eligibility-gate-unavailable' → showToast({ retryable: true })
//   - 'missing-tenant' → showBanner persistent
//   - 'conflict-precondition' → showModal with reload action
//   - Advisory rotina warnings (default) → dismissible banner
//   - All 12 error types covered; no unhandled case.
// =============================================================================

function buildError(code: string, message = `Message for ${code}`) {
  return { code, message };
}

describe('PacErrorHandler', () => {
  let handler: PacErrorHandler;
  let mockFeedback: {
    showToast: jest.Mock;
    showBanner: jest.Mock;
    showModal: jest.Mock;
    dismissToast: jest.Mock;
    dismissBanner: jest.Mock;
    dismissModal: jest.Mock;
  };
  let mockRouter: { navigate: jest.Mock };

  beforeEach(() => {
    mockFeedback = {
      showToast: jest.fn(),
      showBanner: jest.fn(),
      showModal: jest.fn(),
      dismissToast: jest.fn(),
      dismissBanner: jest.fn(),
      dismissModal: jest.fn(),
    };
    mockRouter = { navigate: jest.fn().mockResolvedValue(true) };

    TestBed.configureTestingModule({
      providers: [
        PacErrorHandler,
        { provide: PacFeedbackService, useValue: mockFeedback },
        { provide: Router, useValue: mockRouter },
      ],
    });

    handler = TestBed.inject(PacErrorHandler);
  });

  afterEach(() => TestBed.resetTestingModule());

  // -------------------------------------------------------------------------
  // SAFETY-CRITICAL: emergency-not-detectable → blocking modal (fail-closed)
  // -------------------------------------------------------------------------

  describe('emergency-not-detectable (safety-critical)', () => {
    it('deve chamar showModal com blocking: true', () => {
      handler.handle(buildError('emergency-not-detectable'), 'editor');
      expect(mockFeedback.showModal).toHaveBeenCalledWith(
        expect.objectContaining({ blocking: true }),
      );
    });

    it('deve incluir a mensagem de erro no modal', () => {
      const msg = 'Gesto não detectável pelo motor de visão.';
      handler.handle({ code: 'emergency-not-detectable', message: msg }, 'editor');
      expect(mockFeedback.showModal).toHaveBeenCalledWith(
        expect.objectContaining({ message: msg }),
      );
    });

    it('não deve chamar showToast nem showBanner para emergency-not-detectable', () => {
      handler.handle(buildError('emergency-not-detectable'), 'editor');
      expect(mockFeedback.showToast).not.toHaveBeenCalled();
      expect(mockFeedback.showBanner).not.toHaveBeenCalled();
    });

    it('deve retornar "handled"', () => {
      const result = handler.handle(buildError('emergency-not-detectable'), 'editor');
      expect(result).toBe('handled');
    });
  });

  // -------------------------------------------------------------------------
  // eligibility-gate-unavailable → toast retryable
  // -------------------------------------------------------------------------

  describe('eligibility-gate-unavailable', () => {
    it('deve chamar showToast com retryable: true', () => {
      handler.handle(buildError('eligibility-gate-unavailable'), 'editor');
      expect(mockFeedback.showToast).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({ retryable: true }),
      );
    });

    it('não deve abrir modal para eligibility-gate-unavailable', () => {
      handler.handle(buildError('eligibility-gate-unavailable'), 'editor');
      expect(mockFeedback.showModal).not.toHaveBeenCalled();
    });

    it('deve retornar "handled"', () => {
      expect(handler.handle(buildError('eligibility-gate-unavailable'), 'editor')).toBe('handled');
    });
  });

  // -------------------------------------------------------------------------
  // missing-tenant / tenant-mismatch → banner persistente
  // -------------------------------------------------------------------------

  describe('missing-tenant', () => {
    it('deve chamar showBanner com persistent: true', () => {
      handler.handle(buildError('missing-tenant'), 'catalog');
      expect(mockFeedback.showBanner).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({ persistent: true }),
      );
    });

    it('deve retornar "handled"', () => {
      expect(handler.handle(buildError('missing-tenant'), 'catalog')).toBe('handled');
    });
  });

  describe('tenant-mismatch', () => {
    it('deve chamar showBanner com persistent: true', () => {
      handler.handle(buildError('tenant-mismatch'), 'catalog');
      expect(mockFeedback.showBanner).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({ persistent: true }),
      );
    });
  });

  // -------------------------------------------------------------------------
  // unauthorized → navigate /login
  // -------------------------------------------------------------------------

  describe('unauthorized', () => {
    it('deve navegar para /login', () => {
      handler.handle(buildError('unauthorized'), 'catalog');
      expect(mockRouter.navigate).toHaveBeenCalledWith(['/login']);
    });

    it('não deve chamar showModal nem showToast', () => {
      handler.handle(buildError('unauthorized'), 'catalog');
      expect(mockFeedback.showModal).not.toHaveBeenCalled();
      expect(mockFeedback.showToast).not.toHaveBeenCalled();
    });

    it('deve retornar "handled"', () => {
      expect(handler.handle(buildError('unauthorized'), 'catalog')).toBe('handled');
    });
  });

  // -------------------------------------------------------------------------
  // not-found → navigate /pac
  // -------------------------------------------------------------------------

  describe('not-found', () => {
    it('deve navegar para /pac', () => {
      handler.handle(buildError('not-found'), 'editor');
      expect(mockRouter.navigate).toHaveBeenCalledWith(['/pac']);
    });

    it('deve retornar "handled"', () => {
      expect(handler.handle(buildError('not-found'), 'editor')).toBe('handled');
    });
  });

  // -------------------------------------------------------------------------
  // insufficient-scope → toast
  // -------------------------------------------------------------------------

  describe('insufficient-scope', () => {
    it('deve chamar showToast sem retryable', () => {
      handler.handle(buildError('insufficient-scope'), 'catalog');
      expect(mockFeedback.showToast).toHaveBeenCalled();
      const callArgs = mockFeedback.showToast.mock.calls[0];
      // Options (second arg) should NOT include retryable: true
      expect(callArgs[1]?.retryable).toBeFalsy();
    });

    it('deve retornar "handled"', () => {
      expect(handler.handle(buildError('insufficient-scope'), 'catalog')).toBe('handled');
    });
  });

  // -------------------------------------------------------------------------
  // conflict-precondition / idempotency-mismatch → modal with reload action
  // -------------------------------------------------------------------------

  describe('conflict-precondition', () => {
    it('deve chamar showModal com action: reload', () => {
      handler.handle(buildError('conflict-precondition'), 'editor');
      expect(mockFeedback.showModal).toHaveBeenCalledWith(
        expect.objectContaining({ action: 'reload' }),
      );
    });

    it('não deve ser blocking (não é safety-critical)', () => {
      handler.handle(buildError('conflict-precondition'), 'editor');
      const callArgs = mockFeedback.showModal.mock.calls[0][0];
      expect(callArgs.blocking).toBeFalsy();
    });

    it('deve retornar "handled"', () => {
      expect(handler.handle(buildError('conflict-precondition'), 'editor')).toBe('handled');
    });
  });

  describe('idempotency-mismatch', () => {
    it('deve chamar showModal com action: reload', () => {
      handler.handle(buildError('idempotency-mismatch'), 'editor');
      expect(mockFeedback.showModal).toHaveBeenCalledWith(
        expect.objectContaining({ action: 'reload' }),
      );
    });
  });

  // -------------------------------------------------------------------------
  // invalid-lifecycle-transition → toast
  // -------------------------------------------------------------------------

  describe('invalid-lifecycle-transition', () => {
    it('deve chamar showToast', () => {
      handler.handle(buildError('invalid-lifecycle-transition'), 'editor');
      expect(mockFeedback.showToast).toHaveBeenCalled();
    });

    it('deve retornar "handled"', () => {
      expect(handler.handle(buildError('invalid-lifecycle-transition'), 'editor')).toBe('handled');
    });
  });

  // -------------------------------------------------------------------------
  // validation → inline (caller handles)
  // -------------------------------------------------------------------------

  describe('validation', () => {
    it('deve retornar "inline" sem chamar nenhum método de feedback', () => {
      const result = handler.handle(buildError('validation'), 'editor');
      expect(result).toBe('inline');
      expect(mockFeedback.showToast).not.toHaveBeenCalled();
      expect(mockFeedback.showBanner).not.toHaveBeenCalled();
      expect(mockFeedback.showModal).not.toHaveBeenCalled();
      expect(mockRouter.navigate).not.toHaveBeenCalled();
    });
  });

  // -------------------------------------------------------------------------
  // duplicate-active-mapping → inline (gesture picker handles)
  // -------------------------------------------------------------------------

  describe('duplicate-active-mapping', () => {
    it('deve retornar "inline" sem chamar nenhum método de feedback', () => {
      const result = handler.handle(buildError('duplicate-active-mapping'), 'editor');
      expect(result).toBe('inline');
      expect(mockFeedback.showToast).not.toHaveBeenCalled();
      expect(mockFeedback.showBanner).not.toHaveBeenCalled();
      expect(mockFeedback.showModal).not.toHaveBeenCalled();
    });
  });

  // -------------------------------------------------------------------------
  // Advisory rotina warnings (default case) → dismissible banner
  // -------------------------------------------------------------------------

  describe('advisory rotina warnings (código desconhecido)', () => {
    it('deve chamar showBanner com advisory: true e dismissible: true', () => {
      handler.handle(buildError('rotina-advisory-warning'), 'catalog');
      expect(mockFeedback.showBanner).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({ advisory: true, dismissible: true }),
      );
    });

    it('não deve ser blocking (advisory não é safety-critical)', () => {
      handler.handle(buildError('rotina-advisory-warning'), 'catalog');
      expect(mockFeedback.showModal).not.toHaveBeenCalled();
    });

    it('deve retornar "handled"', () => {
      expect(handler.handle(buildError('rotina-advisory-warning'), 'catalog')).toBe('handled');
    });
  });

  // -------------------------------------------------------------------------
  // Cobertura total — todos os 12 tipos mapeados (tabela de sanidade)
  // -------------------------------------------------------------------------

  describe('cobertura: todos os 12 tipos de ProblemDetail mapeados', () => {
    const ALL_CODES = [
      'validation',
      'missing-tenant',
      'tenant-mismatch',
      'unauthorized',
      'insufficient-scope',
      'not-found',
      'conflict-precondition',
      'idempotency-mismatch',
      'invalid-lifecycle-transition',
      'duplicate-active-mapping',
      'emergency-not-detectable',
      'eligibility-gate-unavailable',
    ];

    ALL_CODES.forEach((code) => {
      it(`deve retornar "inline" ou "handled" para código: ${code}`, () => {
        mockFeedback.showToast.mockClear();
        mockFeedback.showBanner.mockClear();
        mockFeedback.showModal.mockClear();
        mockRouter.navigate.mockClear();

        const result = handler.handle(buildError(code), 'editor');
        expect(['inline', 'handled']).toContain(result);
      });
    });
  });
});
