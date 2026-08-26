import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ReportIngressHttpAdapter } from './report-ingress-http.adapter';
import type { ReportRequest, ReportResponse } from './report-ingress.port';

// =============================================================================
// ReportIngressHttpAdapter — unit tests (GEST-FE-030)
//
// Foco: garantir as invariantes de privacidade e contrato do endpoint.
//
// O que estes testes protegem:
//   1. Corpo nunca contém rawFramePayload (EVNT-023: backend rejeita com 400)
//   2. Corpo nunca contém deviceTenant (sobrescrito pelo servidor)
//   3. Campos obrigatórios estão presentes e corretos
//   4. 201 e 200 são processados como ReportResponse válida
//
// Fixture espelha ReportIngressController + ReportIngressUseCase.java.
//
// Mutação confirmada: ao remover 'pacienteId' do body e verificar que o teste
// "deve enviar pacienteId no corpo" falha — o guarda é real.
// =============================================================================

const REPORT_URL = '/api/v1/reports';

const FIXTURE_REQUEST: ReportRequest = {
  reportId: 'b0000001-0000-4000-a000-000000000042',
  pacienteId: 'e0000000-0000-0000-0000-000000000001',
  commandRef: 'd0000000-0000-0000-0000-000000000001',
  primitives: [
    { tipoGestoId: 'd0000000-0000-0000-0000-000000000001', confidence: 0.91 },
  ],
  confidence: 0.91,
  profileVersion: 7,
  recognizedAt: '2026-08-25T10:30:00Z',
  deviceId: 'b0000000-0000-0000-0000-000000000042',
};

const FIXTURE_RESPONSE_CREATED: ReportResponse = {
  eventoId: 'f0000000-0000-0000-0000-000000000001',
  status: 'created',
  notification: { tier: 'URGENTE', caregiverId: 'c0000000-0000-0000-0000-000000000001' },
  createdAt: '2026-08-25T10:30:00.123Z',
};

const FIXTURE_RESPONSE_IDEMPOTENT: ReportResponse = {
  eventoId: 'f0000000-0000-0000-0000-000000000001',
  status: 'idempotent',
  notification: null,
  createdAt: '2026-08-25T10:30:00.123Z',
};

describe('ReportIngressHttpAdapter', () => {
  let adapter: ReportIngressHttpAdapter;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ReportIngressHttpAdapter,
      ],
    });
    adapter = TestBed.inject(ReportIngressHttpAdapter);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  // ---------------------------------------------------------------------------
  // Invariantes de privacidade — o que NUNCA deve estar no corpo
  // ---------------------------------------------------------------------------

  describe('invariantes de privacidade (EVNT-023)', () => {
    it('não deve incluir rawFramePayload no corpo da requisição', async () => {
      const promise = adapter.submit(FIXTURE_REQUEST);
      const req = httpMock.expectOne({ method: 'POST', url: REPORT_URL });

      const body = req.request.body as Record<string, unknown>;
      expect(body).not.toHaveProperty('rawFramePayload');

      req.flush(FIXTURE_RESPONSE_CREATED, { status: 201, statusText: 'Created' });
      await promise;
    });

    it('não deve incluir deviceTenant no corpo da requisição', async () => {
      const promise = adapter.submit(FIXTURE_REQUEST);
      const req = httpMock.expectOne({ method: 'POST', url: REPORT_URL });

      const body = req.request.body as Record<string, unknown>;
      expect(body).not.toHaveProperty('deviceTenant');

      req.flush(FIXTURE_RESPONSE_CREATED, { status: 201, statusText: 'Created' });
      await promise;
    });
  });

  // ---------------------------------------------------------------------------
  // Campos obrigatórios presentes
  // ---------------------------------------------------------------------------

  describe('campos obrigatórios', () => {
    it('deve enviar reportId, pacienteId, commandRef e deviceId no corpo', async () => {
      const promise = adapter.submit(FIXTURE_REQUEST);
      const req = httpMock.expectOne({ method: 'POST', url: REPORT_URL });

      const body = req.request.body as Record<string, unknown>;
      expect(body['reportId']).toBe(FIXTURE_REQUEST.reportId);
      expect(body['pacienteId']).toBe(FIXTURE_REQUEST.pacienteId);
      expect(body['commandRef']).toBe(FIXTURE_REQUEST.commandRef);
      expect(body['deviceId']).toBe(FIXTURE_REQUEST.deviceId);

      req.flush(FIXTURE_RESPONSE_CREATED, { status: 201, statusText: 'Created' });
      await promise;
    });

    it('deve mapear primitives com tipoGestoId e confidence', async () => {
      const promise = adapter.submit(FIXTURE_REQUEST);
      const req = httpMock.expectOne({ method: 'POST', url: REPORT_URL });

      const body = req.request.body as { primitives: Array<Record<string, unknown>> };
      expect(body.primitives).toHaveLength(1);
      expect(body.primitives[0]['tipoGestoId']).toBe('d0000000-0000-0000-0000-000000000001');
      expect(body.primitives[0]['confidence']).toBe(0.91);

      req.flush(FIXTURE_RESPONSE_CREATED, { status: 201, statusText: 'Created' });
      await promise;
    });
  });

  // ---------------------------------------------------------------------------
  // Respostas 201 e 200
  // ---------------------------------------------------------------------------

  describe('respostas HTTP', () => {
    it('deve retornar ReportResponse com status "created" em 201', async () => {
      const promise = adapter.submit(FIXTURE_REQUEST);
      const req = httpMock.expectOne({ method: 'POST', url: REPORT_URL });
      req.flush(FIXTURE_RESPONSE_CREATED, { status: 201, statusText: 'Created' });

      const resultado = await promise;
      expect(resultado.status).toBe('created');
      expect(resultado.eventoId).toBe('f0000000-0000-0000-0000-000000000001');
    });

    it('deve retornar ReportResponse com status "idempotent" em 200', async () => {
      const promise = adapter.submit(FIXTURE_REQUEST);
      const req = httpMock.expectOne({ method: 'POST', url: REPORT_URL });
      req.flush(FIXTURE_RESPONSE_IDEMPOTENT, { status: 200, statusText: 'OK' });

      const resultado = await promise;
      expect(resultado.status).toBe('idempotent');
    });
  });
});
