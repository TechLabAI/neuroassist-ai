// =============================================================================
// hist-feedback.contract.spec.ts — CTR-05 (FE-E2E-005 / P3)
//
// Verifica que HistFeedbackMockAdapter e HistFeedbackHttpAdapter satisfazem
// HistFeedbackPort com o mesmo shape de dados.
//
// NUANCE 4: HistFeedbackHttpAdapter posta em /api/v1/evnt/events/{id}/feedback
// Esse é um endpoint do EVNT (não do HIST). O interceptor deve capturar a
// URL do EVNT, nunca /api/v1/hist/**.
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import {
  provideHttpClient,
} from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { HistFeedbackPort } from './hist-feedback.port';
import { HistFeedbackHttpAdapter } from './hist-feedback-http.adapter';
import { HistFeedbackMockAdapter } from './hist-feedback-mock.adapter';
import type { FeedbackWriteDto, FeedbackResponse } from '../hist.types';

const FEEDBACK_DTO: FeedbackWriteDto = { verdict: 'real' };

const STUB_RESPONSE: FeedbackResponse = {
  eventId: 'evt-test-001',
  verdict: 'real',
  confirmedAt: '2026-08-22T10:30:00Z',
};

describe.each([
  ['mock' as const, () => new HistFeedbackMockAdapter()],
  ['http' as const, () => TestBed.inject(HistFeedbackHttpAdapter)],
])('%s satisfaz HistFeedbackPort', (label, adapterFactory) => {
  let adapter: HistFeedbackPort;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        HistFeedbackHttpAdapter,
        HistFeedbackMockAdapter,
      ],
    });
    controller = TestBed.inject(HttpTestingController);
    adapter = adapterFactory();
  });

  afterEach(() => {
    controller.verify();
    TestBed.resetTestingModule();
  });

  // ---------------------------------------------------------------------------
  // markFeedback()
  // NUANCE 4: endpoint é /api/v1/evnt/events/{id}/feedback (EVNT, não HIST)
  // ---------------------------------------------------------------------------

  it('markFeedback() retorna FeedbackResponse com shape correto', fakeAsync(() => {
    let result: FeedbackResponse | undefined;
    let error: unknown;

    adapter.markFeedback('evt-test-001', FEEDBACK_DTO).subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      // NUANCE 4: interceptar URL do EVNT, não do HIST
      const req = controller.expectOne(
        '/api/v1/evnt/events/evt-test-001/feedback',
      );
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ verdict: 'real' });
      req.flush(STUB_RESPONSE);
    } else {
      tick(1000);
    }

    if (label === 'http') {
      expect(error).toBeUndefined();
      expect(result).toMatchObject({
        eventId: expect.any(String),
        verdict: expect.stringMatching(/^(real|falso_positivo)$/),
        confirmedAt: expect.any(String),
      });
    } else {
      // mock: sem erro (nenhum flag de e2e ativo)
      expect(error).toBeUndefined();
      expect(result).toMatchObject({
        eventId: expect.any(String),
        verdict: expect.stringMatching(/^(real|falso_positivo)$/),
        confirmedAt: expect.any(String),
      });
    }
  }));

  it('markFeedback() body contém verdict', fakeAsync(() => {
    let captured: { body: unknown } | undefined;

    adapter.markFeedback('evt-x', { verdict: 'falso_positivo' }).subscribe();

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/evnt/events/evt-x/feedback');
      captured = { body: req.request.body };
      req.flush({ eventId: 'evt-x', verdict: 'falso_positivo', confirmedAt: new Date().toISOString() });
      expect(captured.body).toMatchObject({ verdict: 'falso_positivo' });
    } else {
      tick(1000);
    }
  }));
});
