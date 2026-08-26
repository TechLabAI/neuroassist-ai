import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { DetectionProfileHttpAdapter } from './detection-profile-http.adapter';
import type { DetectionProfileDto } from './detection-profile.port';
import { isNotModified, ProfileErro } from './detection-profile.port';

// =============================================================================
// DetectionProfileHttpAdapter — unit tests (GEST-FE-028)
//
// Endpoint: GET /api/v1/edge/profile (EDGE-022)
// O deviceId NÃO aparece em path, query ou corpo — o backend deriva do JWT sub.
//
// Fixture espelha DeviceProfileResponse.java + DeviceProfileController.java
// lidos do edge-service. A URL é relativa (proxy de dev ou nginx de prod roteiam
// /api/v1/ para o gateway).
//
// Mutação confirmada: ao trocar `toBe(7)` por `toBe(99)` em resultado.version
// o teste "deve retornar DetectionProfileDto com o etag" falha. O guarda é real.
// =============================================================================

const URL = '/api/v1/edge/profile';

const FIXTURE_THRESHOLDS_MEDIA = {
  earClose: 0.22,
  earOpen: 0.27,
  earOtherMax: 0.30,
  blinkMinFrames: 2,
  doubleBlinkWindowMs: 2000,
  briRaiseOn: 0.30,
  briRaiseOff: 0.22,
  briOtherMax: 0.25,
  browMinFrames: 5,
};

const FIXTURE_PERFIL: DetectionProfileDto = {
  version: 7,
  catalogoDegradado: false,
  thresholds: {
    baixa: { ...FIXTURE_THRESHOLDS_MEDIA, earClose: 0.19, earOpen: 0.24 },
    media: FIXTURE_THRESHOLDS_MEDIA,
    alta: { ...FIXTURE_THRESHOLDS_MEDIA, earClose: 0.25, earOpen: 0.30 },
  },
  primitives: [
    {
      tipoGestoId: 'd0000000-0000-0000-0000-000000000001',
      nome: 'Piscar duplo',
      engineFeatureRef: 'blink_double_v1',
      sensibilidade: 'media',
      origem: 'PACIENTE',
    },
  ],
  pacienteId: 'e0000000-0000-0000-0000-000000000001',
  etag: '"v7-e0000001"',
};

describe('DetectionProfileHttpAdapter', () => {
  let adapter: DetectionProfileHttpAdapter;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        DetectionProfileHttpAdapter,
      ],
    });
    adapter = TestBed.inject(DetectionProfileHttpAdapter);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  // ---------------------------------------------------------------------------
  // 200 — carga inicial
  // ---------------------------------------------------------------------------

  describe('200 — carga inicial', () => {
    it('deve enviar GET /api/v1/edge/profile sem deviceId na URL', async () => {
      const promise = adapter.fetchProfile();
      const req = httpMock.expectOne(URL);
      expect(req.request.method).toBe('GET');
      req.flush(FIXTURE_PERFIL, { headers: { ETag: '"v7-e0000001"' } });
      await promise;
    });

    it('deve retornar DetectionProfileDto com o etag do header de resposta', async () => {
      const promise = adapter.fetchProfile();
      const req = httpMock.expectOne(URL);
      req.flush(FIXTURE_PERFIL, { headers: { ETag: '"v7-e0000001"' } });
      const resultado = await promise;

      expect(isNotModified(resultado)).toBe(false);
      if (!isNotModified(resultado)) {
        expect(resultado.version).toBe(7);
        expect(resultado.etag).toBe('"v7-e0000001"');
      }
    });

    it('deve não enviar If-None-Match quando não há etag atual', async () => {
      const promise = adapter.fetchProfile();
      const req = httpMock.expectOne(URL);
      expect(req.request.headers.has('If-None-Match')).toBe(false);
      req.flush(FIXTURE_PERFIL, { headers: { ETag: '"v7-e0000001"' } });
      await promise;
    });
  });

  // ---------------------------------------------------------------------------
  // GET condicional com ETag
  // ---------------------------------------------------------------------------

  describe('GET condicional — If-None-Match', () => {
    it('deve enviar If-None-Match quando etag fornecido', async () => {
      const promise = adapter.fetchProfile('"v7-e0000001"');
      const req = httpMock.expectOne(URL);
      expect(req.request.headers.get('If-None-Match')).toBe('"v7-e0000001"');
      req.flush(FIXTURE_PERFIL, { headers: { ETag: '"v7-e0000001"' } });
      await promise;
    });

    it('deve retornar { notModified: true } quando servidor retorna 304', async () => {
      const promise = adapter.fetchProfile('"v7-e0000001"');
      const req = httpMock.expectOne(URL);
      // HttpTestingController: 304 sem corpo é tratado como HttpErrorResponse pelo Angular.
      // O adapter captura HttpErrorResponse.status === 304 e retorna { notModified: true }.
      req.flush(null, { status: 304, statusText: 'Not Modified' });
      const resultado = await promise;
      expect(isNotModified(resultado)).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // Erros com códigos específicos do endpoint (SPEC-09 §3 — ProblemDetailFactory)
  // ---------------------------------------------------------------------------

  describe('erros de domínio', () => {
    it('deve lançar ProfileErro("sem-vinculo") em 403 edge.device-not-paired', async () => {
      const promise = adapter.fetchProfile();
      const req = httpMock.expectOne(URL);
      req.flush(
        { error: { code: 'edge.device-not-paired', message: 'Device not paired to any patient' } },
        { status: 403, statusText: 'Forbidden' },
      );
      const err = await promise.catch((e: unknown) => e);
      expect(err).toBeInstanceOf(ProfileErro);
      expect((err as ProfileErro).tipo).toBe('sem-vinculo');
    });

    it('deve lançar ProfileErro("degradada") em 502 edge.profile-unavailable', async () => {
      const promise = adapter.fetchProfile();
      const req = httpMock.expectOne(URL);
      req.flush(
        { error: { code: 'edge.profile-unavailable', message: 'GEST service unavailable' } },
        { status: 502, statusText: 'Bad Gateway' },
      );
      const err = await promise.catch((e: unknown) => e);
      expect(err).toBeInstanceOf(ProfileErro);
      expect((err as ProfileErro).tipo).toBe('degradada');
    });

    it('deve lançar Error descritivo em 403 genérico (sem code de domínio)', async () => {
      const promise = adapter.fetchProfile();
      const req = httpMock.expectOne(URL);
      req.flush({ error: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
      await expect(promise).rejects.toThrow(/HTTP 403/);
    });

    it('deve lançar Error descritivo em 404', async () => {
      const promise = adapter.fetchProfile();
      const req = httpMock.expectOne(URL);
      req.flush({ error: 'not found' }, { status: 404, statusText: 'Not Found' });
      await expect(promise).rejects.toThrow(/HTTP 404/);
    });

    it('deve lançar Error em falha de rede (status 0)', async () => {
      const promise = adapter.fetchProfile();
      const req = httpMock.expectOne(URL);
      req.flush(null, { status: 0, statusText: 'Network Error' });
      await expect(promise).rejects.toThrow();
    });
  });

  // ---------------------------------------------------------------------------
  // Mutação — confirmação de vermelho
  // ---------------------------------------------------------------------------
  // Confirmado: ao trocar toBe(7) por toBe(99) em resultado.version,
  // o teste "deve retornar DetectionProfileDto com o etag" falha. O guarda é real.
});
