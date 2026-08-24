import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { PacComandoFacade, ComandoRowVM } from './pac-comando.facade';
import { PAC_COMANDO_PORT, Comando } from './api/comando.port';
import { EdgeDeviceAckStubAdapter } from './api/edge-device-ack-stub.adapter';
import { PacErrorHandler } from './error-surface/pac-error.handler';

// =============================================================================
// PacComandoFacade — unit tests (P6 / PAC-FE-011)
// Covers:
//   1. load() → catalog populated; loadState transitions idle → loading → loaded
//   2. ADR-005: syncState NEVER transitions to 'active-on-device' —
//      EdgeDeviceAckStubAdapter ALWAYS returns 'pending-sync' (central contract).
//   3. save() (new command) → editor.saveState = 'saved-pending-sync'
//   4. openNew() / openEdit() / cancelEdit() — editor state management
//   5. patchEditor() → dirty flag
//   6. generateIdempotencyKey() called on every save(); keys are unique between
//      consecutive calls (idempotency contract).
//   7. Error handling — load failure and save failure (including emergency gate 422).
// =============================================================================

function buildComando(overrides?: Partial<Comando>): Comando {
  return {
    id: 'c1',
    pacienteId: 'p1',
    rotulo: 'Preciso de ajuda',
    urgencia: 'rotina',
    primitivoPrincipal: 'g1',
    primitivoSecundario: null,
    sensibilidade: 'media',
    tempoMinimoGestoMs: 400,
    tempoMaximoGestoMs: 1500,
    estado: 'drafted',
    etag: 'etag-v1',
    ...overrides,
  };
}

function createMockPort() {
  return {
    list: jest.fn().mockReturnValue(of([])),
    get: jest.fn().mockReturnValue(of(buildComando())),
    create: jest.fn().mockReturnValue(
      of(new HttpResponse<Comando>({ status: 201, body: buildComando() })),
    ),
    updateDraft: jest.fn().mockReturnValue(of(buildComando({ etag: 'etag-v2' }))),
    validate: jest.fn().mockReturnValue(of(buildComando({ estado: 'validated' }))),
    publish: jest.fn().mockReturnValue(of(buildComando({ estado: 'published' }))),
    recalibrate: jest.fn().mockReturnValue(of(buildComando({ sensibilidade: 'alta' }))),
    retire: jest.fn().mockReturnValue(of(buildComando({ estado: 'retired' }))),
  };
}

describe('PacComandoFacade', () => {
  let facade: PacComandoFacade;
  let mockPort: ReturnType<typeof createMockPort>;

  beforeEach(() => {
    mockPort = createMockPort();

    TestBed.configureTestingModule({
      providers: [
        PacComandoFacade,
        { provide: PAC_COMANDO_PORT, useValue: mockPort },
        EdgeDeviceAckStubAdapter,
      ],
    });

    facade = TestBed.inject(PacComandoFacade);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  // -------------------------------------------------------------------------
  // Estado inicial
  // -------------------------------------------------------------------------

  describe('estado inicial', () => {
    it('catalog() deve ser []', () => {
      expect(facade.catalog()).toEqual([]);
    });

    it('loadState() deve ser "idle"', () => {
      expect(facade.loadState()).toBe('idle');
    });

    it('isEmpty() deve ser false (não carregado ainda)', () => {
      // isEmpty só é true quando loadState === 'loaded' E catalog vazio
      expect(facade.isEmpty()).toBe(false);
    });

    it('isEditing() deve ser false', () => {
      expect(facade.isEditing()).toBe(false);
    });
  });

  // -------------------------------------------------------------------------
  // load() — happy path e transição de loadState
  // Facades usam from(lastValueFrom(...)) que resolve via Promise microtask;
  // fakeAsync + tick() é necessário para sincronizar os signals após load.
  // -------------------------------------------------------------------------

  describe('load()', () => {
    it('deve popular catalog() com a lista retornada pelo port', fakeAsync(() => {
      const comandos = [buildComando({ id: 'c1' }), buildComando({ id: 'c2' })];
      mockPort.list.mockReturnValue(of(comandos));

      facade.load('p1');
      tick();

      expect(facade.catalog().length).toBe(2);
    }));

    it('loadState() deve ser "loaded" após load() bem-sucedido', fakeAsync(() => {
      mockPort.list.mockReturnValue(of([buildComando()]));

      facade.load('p1');
      tick();

      expect(facade.loadState()).toBe('loaded');
    }));

    it('isEmpty() deve ser true quando load() retorna lista vazia', fakeAsync(() => {
      mockPort.list.mockReturnValue(of([]));

      facade.load('p1');
      tick();

      expect(facade.loadState()).toBe('loaded');
      expect(facade.isEmpty()).toBe(true);
    }));

    it('loadState() deve ser "error" e error() populado quando port rejeita', fakeAsync(() => {
      mockPort.list.mockReturnValue(throwError(() => new Error('Timeout')));

      facade.load('p1');
      tick();

      expect(facade.loadState()).toBe('error');
      expect(facade.error()).not.toBeNull();
      expect(facade.loading()).toBe(false);
    }));
  });

  // -------------------------------------------------------------------------
  // ADR-005: syncState NUNCA transita para 'active-on-device'
  // Contrato central: EdgeDeviceAckStubAdapter sempre retorna 'pending-sync'.
  // Um comando NÃO pode ser marcado como ativo no dispositivo sem device-ack.
  // -------------------------------------------------------------------------

  describe('ADR-005 — contrato de syncState (central)', () => {
    it('todo item do catalog deve ter syncState === "pending-sync" (nunca "active-on-device")', fakeAsync(() => {
      const comandos = [
        buildComando({ id: 'c1', estado: 'drafted' }),
        buildComando({ id: 'c2', estado: 'validated' }),
        buildComando({ id: 'c3', estado: 'published' }),
      ];
      mockPort.list.mockReturnValue(of(comandos));

      facade.load('p1');
      tick();

      expect(facade.catalog().length).toBe(3);
      for (const row of facade.catalog()) {
        expect(row.syncState).toBe('pending-sync');
        expect(row.syncState).not.toBe('active-on-device');
      }
    }));

    it('syncState permanece "pending-sync" mesmo após save() bem-sucedido (ADR-005)', fakeAsync(() => {
      const criado = buildComando({ id: 'c-novo', estado: 'drafted' });
      mockPort.create.mockReturnValue(
        of(new HttpResponse<Comando>({ status: 201, body: criado })),
      );
      mockPort.list.mockReturnValue(of([criado]));

      facade.openNew('p1', 'g1');
      facade.save();
      tick(); // flush create Promise
      tick(); // flush reload (load() is called inside save() success handler)

      // Catalog deve refletir pending-sync para todos os itens
      expect(facade.catalog().every((r) => r.syncState === 'pending-sync')).toBe(true);
    }));

    it('syncState permanece "pending-sync" para comando published — nenhum ack EDGE presente no MVP', fakeAsync(() => {
      const publishedCmd = buildComando({ id: 'c-pub', estado: 'published' });
      mockPort.list.mockReturnValue(of([publishedCmd]));

      facade.load('p1');
      tick();

      const publishedRow = facade.catalog().find((r) => r.id === 'c-pub');
      expect(publishedRow).toBeDefined();
      expect(publishedRow!.syncState).toBe('pending-sync');
    }));
  });

  // -------------------------------------------------------------------------
  // save() — novo comando (create)
  // -------------------------------------------------------------------------

  describe('save() — criação de novo comando', () => {
    it('deve transitar editor.saveState para "saved-pending-sync" no happy path', fakeAsync(() => {
      mockPort.create.mockReturnValue(
        of(new HttpResponse<Comando>({ status: 201, body: buildComando({ id: 'c-novo' }) })),
      );
      mockPort.list.mockReturnValue(of([]));

      facade.openNew('p1', 'g1');
      facade.save();
      tick(); // flush create Promise
      tick(); // flush reload triggered inside save handler

      expect(facade.editor().saveState).toBe('saved-pending-sync');
    }));

    it('deve chamar port.create com um idempotencyKey único por chamada', () => {
      // idempotencyKey é gerado de forma síncrona antes da subscricao async;
      // pode ser verificado sem fakeAsync.
      mockPort.list.mockReturnValue(of([]));
      mockPort.create.mockReturnValue(
        of(new HttpResponse<Comando>({ status: 201, body: buildComando() })),
      );

      facade.openNew('p1', 'g1');
      facade.patchEditor({ rotulo: 'Cmd A' });
      facade.save();

      facade.openNew('p1', 'g1');
      facade.patchEditor({ rotulo: 'Cmd B' });
      facade.save();

      expect(mockPort.create).toHaveBeenCalledTimes(2);
      const key1: string = mockPort.create.mock.calls[0][2];
      const key2: string = mockPort.create.mock.calls[1][2];

      expect(typeof key1).toBe('string');
      expect(key1.length).toBeGreaterThan(0);
      // Dois saves consecutivos devem gerar chaves distintas
      expect(key1).not.toBe(key2);
    });

    it('editor.saveState deve ser "error" quando port.create falha', fakeAsync(() => {
      facade.openNew('p1', 'g1');
      mockPort.create.mockReturnValue(throwError(() => new Error('Server error')));

      facade.save();
      tick();

      expect(facade.editor().saveState).toBe('error');
    }));
  });

  // -------------------------------------------------------------------------
  // Emergency gate — 422 emergency-not-detectable
  // -------------------------------------------------------------------------

  describe('emergency gate (ADR-005 BE / SPEC-07)', () => {
    it('deve popular emergencyGateError quando save() recebe erro 422 emergency-not-detectable', fakeAsync(() => {
      // A forma REAL do erro, copiada do que o produtor emite: o
      // `ComandoMockAdapter` (e o backend) lançam `HttpErrorResponse` com o
      // ProblemDetail no corpo, em `error.type`.
      //
      // Antes esta fixture era `Object.assign(new Error('emergency-not-detectable'),
      // {status: 422})` — texto na mensagem e nada de ProblemDetail. Ela casava
      // com a classificação por substring que a facade fazia, e as duas estavam
      // erradas juntas: o teste concordava com o consumidor e discordava do
      // produtor, então ficava verde justamente enquanto o código não sabia ler
      // o erro de verdade.
      const emergencyError = {
        status: 422,
        error: {
          type: 'https://neuroassist.ai/problems/emergency-not-detectable',
          title: 'Emergency gate: gesture not detectable',
          status: 422,
          detail:
            'O gesto escolhido não é detectável pelo motor atual. Escolha outro gesto ou mude a urgência para Rotina.',
        },
      };
      mockPort.create.mockReturnValue(throwError(() => emergencyError));

      facade.openNew('p1', 'g1');
      facade.patchEditor({ urgencia: 'emergencia', rotulo: 'Emergência' });
      facade.save();
      tick();

      const ed = facade.editor();
      expect(ed.saveState).toBe('error');
      expect(ed.emergencyGateError).not.toBeNull();
    }));

    it('encaminha o erro ao PacErrorHandler — a ligação que faltava', fakeAsync(() => {
      // O `PacErrorHandler` era exportado no barrel e NÃO TINHA um único ponto de
      // invocação no repositório: uma superfície de erro completa, com testes, e
      // jamais chamada pelo código de tela. Este teste é o que impede a ligação
      // de ser desfeita em silêncio.
      const handler = TestBed.inject(PacErrorHandler);
      const espiao = jest.spyOn(handler, 'handle');

      mockPort.create.mockReturnValue(
        throwError(() => ({
          status: 403,
          error: {
            type: 'https://neuroassist.ai/problems/missing-tenant',
            title: 'Missing tenant',
            status: 403,
            detail: 'Empresa não identificada nesta sessão.',
          },
        })),
      );

      facade.openNew('p1', 'g1');
      facade.patchEditor({ rotulo: 'Água' });
      facade.save();
      tick();

      // Chegou classificado: o código veio do ProblemDetail, e não de procurar
      // substring na mensagem.
      expect(espiao).toHaveBeenCalledWith(
        expect.objectContaining({ code: 'missing-tenant' }),
        'editor',
      );
    }));

    it('erro já mostrado pelo handler não é repetido inline', fakeAsync(() => {
      // A mesma frase em dois lugares faz a pessoa procurar dois problemas.
      mockPort.create.mockReturnValue(
        throwError(() => ({
          status: 403,
          error: {
            type: 'https://neuroassist.ai/problems/missing-tenant',
            status: 403,
            detail: 'Empresa não identificada nesta sessão.',
          },
        })),
      );

      facade.openNew('p1', 'g1');
      facade.patchEditor({ rotulo: 'Água' });
      facade.save();
      tick();

      expect(facade.error()).toBeNull();
    }));

    it('emergencyGateError deve ser null quando o erro não é do tipo 422', fakeAsync(() => {
      mockPort.create.mockReturnValue(throwError(() => new Error('Network error')));

      facade.openNew('p1', 'g1');
      facade.save();
      tick();

      expect(facade.editor().emergencyGateError).toBeNull();
      expect(facade.editor().saveState).toBe('error');
    }));
  });

  // -------------------------------------------------------------------------
  // openNew() / openEdit() / cancelEdit()
  // -------------------------------------------------------------------------

  describe('openNew()', () => {
    it('deve setar pid e primitivoPrincipal no editor', () => {
      facade.openNew('p1', 'g-first');

      expect(facade.editor().pid).toBe('p1');
      expect(facade.editor().primitivoPrincipal).toBe('g-first');
      expect(facade.editor().cid).toBeNull();
    });

    it('isEditing() deve ser true após openNew()', () => {
      facade.openNew('p1', 'g1');

      expect(facade.isEditing()).toBe(true);
    });
  });

  describe('openEdit()', () => {
    it('deve popular o editor com os dados da row selecionada', () => {
      const row: ComandoRowVM = {
        id: 'c1',
        rotulo: 'Beber Água',
        urgencia: 'rotina',
        primitivoPrincipalNome: 'g1',
        primitivoSecundarioNome: null,
        sensibilidade: 'baixa',
        estado: 'drafted',
        syncState: 'pending-sync',
        testedState: 'not-tested',
        etag: 'etag-v1',
      };

      facade.openEdit('p1', row);

      expect(facade.editor().rotulo).toBe('Beber Água');
      expect(facade.editor().cid).toBe('c1');
      expect(facade.editor().pid).toBe('p1');
    });
  });

  describe('cancelEdit()', () => {
    it('deve resetar o editor para o estado inicial após cancelEdit()', () => {
      facade.openNew('p1', 'g1');
      expect(facade.isEditing()).toBe(true);

      facade.cancelEdit();

      expect(facade.editor().pid).toBe('');
      expect(facade.isEditing()).toBe(false);
    });
  });

  // -------------------------------------------------------------------------
  // patchEditor() — dirty flag
  // -------------------------------------------------------------------------

  describe('patchEditor()', () => {
    it('deve marcar dirty = true ao atualizar campo do editor', () => {
      facade.openNew('p1', 'g1');
      expect(facade.editor().dirty).toBe(false);

      facade.patchEditor({ rotulo: 'Novo Rótulo' });

      expect(facade.editor().rotulo).toBe('Novo Rótulo');
      expect(facade.editor().dirty).toBe(true);
    });
  });

  // -------------------------------------------------------------------------
  // markTested()
  // -------------------------------------------------------------------------

  describe('markTested()', () => {
    it('deve atualizar testedState para "tested" na row do catalog', fakeAsync(() => {
      const comando = buildComando({ id: 'c1' });
      mockPort.list.mockReturnValue(of([comando]));
      facade.load('p1');
      tick();

      expect(facade.catalog()[0].testedState).toBe('not-tested');

      facade.markTested('c1');

      expect(facade.catalog()[0].testedState).toBe('tested');
    }));
  });
});
