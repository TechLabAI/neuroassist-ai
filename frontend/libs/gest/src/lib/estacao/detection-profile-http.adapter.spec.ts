import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { DetectionProfileHttpAdapter } from './detection-profile-http.adapter';
import { GEST_SERVICE_BASE_URL } from '../api/gest-service.config';
import type { DetectionProfileDto } from './detection-profile.port';
import { isNotModified } from './detection-profile.port';

// =============================================================================
// DetectionProfileHttpAdapter — unit tests (GEST-FE-028)
//
// TODO[GEST-FE-028][blocked] — estes testes cobrem o comportamento do adapter
// (GET condicional, 200, 304, 404). O token de dispositivo (`edge.device`)
// recebe 403 neste endpoint em produção — a autoridade `gest.detection-profile.read`
// não foi concedida ao DeviceTokenService. Os testes são válidos para quando
// o backend conceder a autoridade.
//
// NÃO existe teste que simula 403 como sucesso — seria plantar o defeito E2E
// catalogado em CLAUDE.md: stub correto para premissa errada.
//
// Fixture lida de DetectionProfileController.findForDevice() + DetectionProfileResponse.java.
// =============================================================================

const DEVICE_ID = 'b0000000-0000-0000-0000-000000000042';
const BASE_URL = 'http://localhost:8084';
const URL = `${BASE_URL}/gest-service/detection-profile/devices/${DEVICE_ID}`;

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
  id: 'c0000000-0000-0000-0000-000000000001',
  version: 7,
  active: true,
  contractVersion: '1.0.0',
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
  catalogoVersao: 3,
  catalogoDegradado: false,
  publishedAt: '2026-08-25T10:00:00Z',
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
        { provide: GEST_SERVICE_BASE_URL, useValue: BASE_URL },
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
    it('deve enviar GET para o endpoint correto', async () => {
      const promise = adapter.fetchProfile(DEVICE_ID);
      const req = httpMock.expectOne(URL);
      expect(req.request.method).toBe('GET');
      req.flush(FIXTURE_PERFIL, { headers: { ETag: '"v7-e0000001"' } });
      await promise;
    });

    it('deve retornar DetectionProfileDto com o etag do header de resposta', async () => {
      const promise = adapter.fetchProfile(DEVICE_ID);
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
      const promise = adapter.fetchProfile(DEVICE_ID);
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
      const promise = adapter.fetchProfile(DEVICE_ID, '"v7-e0000001"');
      const req = httpMock.expectOne(URL);
      expect(req.request.headers.get('If-None-Match')).toBe('"v7-e0000001"');
      req.flush(FIXTURE_PERFIL, { headers: { ETag: '"v7-e0000001"' } });
      await promise;
    });

    it('deve retornar { notModified: true } quando servidor retorna 304', async () => {
      const promise = adapter.fetchProfile(DEVICE_ID, '"v7-e0000001"');
      const req = httpMock.expectOne(URL);
      // HttpTestingController: 304 sem corpo é tratado como HttpErrorResponse pelo Angular.
      // O adapter captura HttpErrorResponse.status === 304 e retorna { notModified: true }.
      req.flush(null, { status: 304, statusText: 'Not Modified' });
      const resultado = await promise;
      expect(isNotModified(resultado)).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // Erros
  // ---------------------------------------------------------------------------

  describe('erros', () => {
    it('deve lançar Error descritivo em 404 (dispositivo sem perfil publicado)', async () => {
      const promise = adapter.fetchProfile(DEVICE_ID);
      const req = httpMock.expectOne(URL);
      req.flush({ error: 'profile not found' }, { status: 404, statusText: 'Not Found' });
      await expect(promise).rejects.toThrow(/HTTP 404/);
    });

    it('deve lançar Error descritivo em 403 (token sem autoridade — estado atual em produção)', async () => {
      // TODO[GEST-FE-028][blocked]: este é o caso real em produção hoje.
      // token `edge.device` recebe 403 porque falta `gest.detection-profile.read`.
      const promise = adapter.fetchProfile(DEVICE_ID);
      const req = httpMock.expectOne(URL);
      req.flush({ error: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
      await expect(promise).rejects.toThrow(/HTTP 403/);
    });

    it('deve lançar Error em falha de rede (status 0)', async () => {
      const promise = adapter.fetchProfile(DEVICE_ID);
      const req = httpMock.expectOne(URL);
      req.flush(null, { status: 0, statusText: 'Network Error' });
      await expect(promise).rejects.toThrow();
    });
  });

  // ---------------------------------------------------------------------------
  // Mutação — confirmação de vermelho
  // ---------------------------------------------------------------------------
  // Confirmado manualmente: ao trocar toBe(7) por toBe(99) em resultado.version,
  // o teste "deve retornar DetectionProfileDto com o etag" falha. O guarda é real.
});
