// =============================================================================
// comando.contract.spec.ts — CTR-07 (FE-E2E-005 / P3)
//
// Verifica que ComandoMockAdapter e ComandoHttpAdapter satisfazem
// PacComandoPort com o mesmo shape de dados.
//
// Base path (normalizado em P2 / ADR-008): /api/v1/pac/pacientes/{pid}/comandos
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient, HttpResponse } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { PacComandoPort, Comando, ComandoCreate } from './comando.port';
import { ComandoHttpAdapter } from './comando-http.adapter';
import { ComandoMockAdapter } from './comando-mock.adapter';

const STUB_COMANDO: Comando = {
  id: 'cmd-test-001',
  pacienteId: 'pac-marcio',
  rotulo: 'Ajuda',
  urgencia: 'emergencia',
  primitivoPrincipal: 'tg-sobrancelha',
  primitivoSecundario: null,
  sensibilidade: 'media',
  tempoMinimoGestoMs: 400,
  tempoMaximoGestoMs: 1500,
  estado: 'published',
  etag: 'etag-test-v1',
};

const CREATE_DTO: ComandoCreate = {
  rotulo: 'Novo Comando',
  urgencia: 'rotina',
  primitivoPrincipal: 'tg-piscar-duplo',
  primitivoSecundario: null,
  sensibilidade: 'alta',
  tempoMinimoGestoMs: 300,
  tempoMaximoGestoMs: 1200,
};

describe.each([
  ['mock' as const, () => new ComandoMockAdapter()],
  ['http' as const, () => TestBed.inject(ComandoHttpAdapter)],
])('%s satisfaz PacComandoPort', (label, adapterFactory) => {
  let adapter: PacComandoPort;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ComandoHttpAdapter,
        ComandoMockAdapter,
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
  // list()
  // ---------------------------------------------------------------------------

  it('list() retorna Comando[] com shape correto', fakeAsync(() => {
    let result: Comando[] | undefined;
    let error: unknown;

    adapter.list('pac-marcio').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne(r =>
        r.url.startsWith('/api/v1/pac/pacientes/pac-marcio/comandos') && r.method === 'GET',
      );
      req.flush([STUB_COMANDO]);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(Array.isArray(result)).toBe(true);
    if (result!.length > 0) {
      expect(result![0]).toMatchObject({
        id: expect.any(String),
        pacienteId: expect.any(String),
        rotulo: expect.any(String),
        urgencia: expect.stringMatching(/^(emergencia|rotina)$/),
        primitivoPrincipal: expect.any(String),
        sensibilidade: expect.stringMatching(/^(baixa|media|alta)$/),
        tempoMinimoGestoMs: expect.any(Number),
        tempoMaximoGestoMs: expect.any(Number),
        estado: expect.stringMatching(/^(drafted|validated|published|retired)$/),
        etag: expect.any(String),
      });
    }
  }));

  // ---------------------------------------------------------------------------
  // get()
  // ---------------------------------------------------------------------------

  it('get() retorna Comando pelo id', fakeAsync(() => {
    let result: Comando | undefined;
    let error: unknown;

    adapter.get('pac-marcio', 'cmd-ajuda').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/pac/pacientes/pac-marcio/comandos/cmd-ajuda');
      expect(req.request.method).toBe('GET');
      req.flush(STUB_COMANDO);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      id: expect.any(String),
      pacienteId: expect.any(String),
      rotulo: expect.any(String),
      urgencia: expect.any(String),
      estado: expect.any(String),
    });
  }));

  // ---------------------------------------------------------------------------
  // create()
  // ---------------------------------------------------------------------------

  it('create() retorna HttpResponse<Comando>', fakeAsync(() => {
    let result: HttpResponse<Comando> | undefined;
    let error: unknown;

    adapter.create('pac-marcio', CREATE_DTO).subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/pac/pacientes/pac-marcio/comandos');
      expect(req.request.method).toBe('POST');
      req.flush({ ...STUB_COMANDO, id: 'cmd-new', estado: 'drafted' }, { status: 201, statusText: 'Created' });
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toBeDefined();
    expect(result!.body).toMatchObject({
      id: expect.any(String),
      rotulo: expect.any(String),
      urgencia: expect.any(String),
      estado: expect.any(String),
    });
  }));
});
