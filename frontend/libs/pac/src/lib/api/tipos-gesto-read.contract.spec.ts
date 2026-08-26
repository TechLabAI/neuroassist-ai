// =============================================================================
// tipos-gesto-read.contract.spec.ts — CTR-08 (FE-E2E-005 / P3)
//
// Verifica que TiposGestoReadMockAdapter e TiposGestoReadHttpAdapter satisfazem
// TiposGestoReadPort com o mesmo shape de dados.
//
// NUANCE 5: TiposGestoReadHttpAdapter chama GET /api/v1/gest/tipo-gesto
// — endpoint do GEST, não do PAC. Sem importar nenhum símbolo do módulo GEST
// (ADR-002: PAC nunca importa scope:gest). O adapter recebe apenas a URL.
//
// Base path: /api/v1/gest/tipo-gesto (cross-module via GWY, hardcoded no adapter)
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
// Conformidade ADR-002: nenhum import do módulo GEST
import { TiposGestoReadPort, TipoGesto } from './tipos-gesto-read.port';
import { TiposGestoReadHttpAdapter } from './tipos-gesto-read-http.adapter';
import { TiposGestoReadMockAdapter } from './tipos-gesto-read-mock.adapter';

const STUB_TIPO: TipoGesto = {
  id: 'tg-test',
  nome: 'Gesto Teste',
};

describe.each([
  ['mock' as const, () => new TiposGestoReadMockAdapter()],
  ['http' as const, () => TestBed.inject(TiposGestoReadHttpAdapter)],
])('%s satisfaz TiposGestoReadPort', (label, adapterFactory) => {
  let adapter: TiposGestoReadPort;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // NUANCE 5: sem ACCESS_HTTP_BASE — o adapter usa path hardcoded
        TiposGestoReadHttpAdapter,
        TiposGestoReadMockAdapter,
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
  // NUANCE 5: URL interceptada é /api/v1/gest/tipo-gesto (endpoint GEST via GWY)
  // ---------------------------------------------------------------------------

  it('list() retorna TipoGesto[] com shape correto', fakeAsync(() => {
    let result: TipoGesto[] | undefined;
    let error: unknown;

    adapter.list().subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      // NUANCE 5: interceptar URL do GEST (cross-module, hardcoded no adapter)
      const req = controller.expectOne('/api/v1/gest/tipo-gesto');
      expect(req.request.method).toBe('GET');
      req.flush([STUB_TIPO]);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(Array.isArray(result)).toBe(true);
    if (result!.length > 0) {
      expect(result![0]).toMatchObject({
        id: expect.any(String),
        nome: expect.any(String),
      });
    }
  }));

  // ---------------------------------------------------------------------------
  // get()
  // ---------------------------------------------------------------------------

  it('get() retorna TipoGesto pelo id', fakeAsync(() => {
    let result: TipoGesto | undefined;
    let error: unknown;

    // 'tg-piscar-rapido' — precisa existir no fixture do TiposGestoReadMockAdapter;
    // o lado 'http' não olha para o fixture (a resposta é STUB_TIPO), mas o lado
    // 'mock' lança se o id não estiver na lista.
    adapter.get('tg-piscar-rapido').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/gest/tipo-gesto/tg-piscar-rapido');
      expect(req.request.method).toBe('GET');
      req.flush(STUB_TIPO);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      id: expect.any(String),
      nome: expect.any(String),
    });
  }));

  // ---------------------------------------------------------------------------
  // requestNew()
  // ---------------------------------------------------------------------------

  it('requestNew() retorna TipoGesto criado', fakeAsync(() => {
    let result: TipoGesto | undefined;
    let error: unknown;

    adapter.requestNew('Fechar os lábios').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/gest/tipo-gesto');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toMatchObject({ nome: 'Fechar os lábios' });
      req.flush({ id: 'tg-fechar-os-labios', nome: 'Fechar os lábios' });
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      id: expect.any(String),
      nome: expect.any(String),
    });
  }));
});
