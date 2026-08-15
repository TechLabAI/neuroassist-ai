import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TiposGestoReadFacade } from './tipos-gesto-read.facade';
import { TIPOS_GESTO_READ_PORT } from './api/tipos-gesto-read.port';

// =============================================================================
// TiposGestoReadFacade — unit tests (P6 / PAC-FE-012)
// Port is mocked; tests cover happy path, fallback, addNew signal, and
// graceful degradation when GEST is unavailable (SPEC-07).
// =============================================================================

function createMockPort() {
  return {
    list: jest.fn().mockReturnValue(of([])),
    get: jest.fn().mockReturnValue(of(null)),
    requestNew: jest.fn().mockReturnValue(of({ id: 'g-new', nome: 'Novo Gesto' })),
  };
}

describe('TiposGestoReadFacade', () => {
  let facade: TiposGestoReadFacade;
  let mockPort: ReturnType<typeof createMockPort>;

  beforeEach(() => {
    mockPort = createMockPort();

    TestBed.configureTestingModule({
      providers: [
        TiposGestoReadFacade,
        { provide: TIPOS_GESTO_READ_PORT, useValue: mockPort },
      ],
    });

    facade = TestBed.inject(TiposGestoReadFacade);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  // -------------------------------------------------------------------------
  // Sinal inicial
  // -------------------------------------------------------------------------

  describe('estado inicial', () => {
    it('tipos() deve ser [] antes de load()', () => {
      expect(facade.tipos()).toEqual([]);
    });

    it('loading() deve ser false antes de load()', () => {
      expect(facade.loading()).toBe(false);
    });

    it('error() deve ser null antes de load()', () => {
      expect(facade.error()).toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // load() — happy path
  // Facades usam from(lastValueFrom(...)) que resolve via Promise microtask;
  // fakeAsync + tick() é necessário para sincronizar o resultado nos signals.
  // -------------------------------------------------------------------------

  describe('load()', () => {
    it('deve popular tipos() com a lista retornada pelo port', fakeAsync(() => {
      const tipos = [
        { id: 'g1', nome: 'Piscar' },
        { id: 'g2', nome: 'Inclinação' },
      ];
      mockPort.list.mockReturnValue(of(tipos));

      facade.load();
      tick();

      expect(facade.tipos()).toEqual(tipos);
      expect(facade.loading()).toBe(false);
      expect(facade.error()).toBeNull();
    }));

    it('firstTipoId computed retorna "" quando lista está vazia', fakeAsync(() => {
      mockPort.list.mockReturnValue(of([]));
      facade.load();
      tick();

      expect(facade.firstTipoId()).toBe('');
    }));

    it('firstTipoId computed retorna id do primeiro tipo quando lista não está vazia', fakeAsync(() => {
      mockPort.list.mockReturnValue(of([{ id: 'g1', nome: 'Piscar' }, { id: 'g2', nome: 'Vibrar' }]));
      facade.load();
      tick();

      expect(facade.firstTipoId()).toBe('g1');
    }));
  });

  // -------------------------------------------------------------------------
  // getById computed — fallback retorna id quando não encontrado
  // -------------------------------------------------------------------------

  describe('getById computed', () => {
    it('deve retornar o nome quando o tipo existe na lista', fakeAsync(() => {
      mockPort.list.mockReturnValue(of([{ id: 'g1', nome: 'Piscar' }]));
      facade.load();
      tick();

      expect(facade.getById()('g1')).toBe('Piscar');
    }));

    it('deve retornar o id como fallback quando tipo não existe (SPEC-07)', fakeAsync(() => {
      mockPort.list.mockReturnValue(of([{ id: 'g1', nome: 'Piscar' }]));
      facade.load();
      tick();

      expect(facade.getById()('id-inexistente')).toBe('id-inexistente');
    }));
  });

  // -------------------------------------------------------------------------
  // addNew() — sinal de novo tipo
  // -------------------------------------------------------------------------

  describe('addNew()', () => {
    it('deve adicionar o novo tipo à lista quando port responde com sucesso', fakeAsync(() => {
      const novoTipo = { id: 'g-novo', nome: 'Mover Cabeça' };
      mockPort.requestNew.mockReturnValue(of(novoTipo));
      mockPort.list.mockReturnValue(of([{ id: 'g1', nome: 'Piscar' }]));
      facade.load();
      tick();

      facade.addNew('Mover Cabeça');
      tick();

      const ids = facade.tipos().map((t) => t.id);
      expect(ids).toContain('g-novo');
    }));

    it('não deve duplicar tipo já existente na lista', fakeAsync(() => {
      const existente = { id: 'g1', nome: 'Piscar' };
      mockPort.list.mockReturnValue(of([existente]));
      mockPort.requestNew.mockReturnValue(of(existente));
      facade.load();
      tick();

      facade.addNew('Piscar');
      tick();

      const matches = facade.tipos().filter((t) => t.id === 'g1');
      expect(matches.length).toBe(1);
    }));
  });

  // -------------------------------------------------------------------------
  // findByNome() e getNameById() — utilitários síncronos
  // -------------------------------------------------------------------------

  describe('findByNome()', () => {
    it('deve retornar o id quando o tipo existe pelo nome', fakeAsync(() => {
      mockPort.list.mockReturnValue(of([{ id: 'g1', nome: 'Piscar' }]));
      facade.load();
      tick();

      expect(facade.findByNome('Piscar')).toBe('g1');
    }));

    it('deve retornar null quando o tipo não existe', fakeAsync(() => {
      mockPort.list.mockReturnValue(of([{ id: 'g1', nome: 'Piscar' }]));
      facade.load();
      tick();

      expect(facade.findByNome('Inexistente')).toBeNull();
    }));
  });

  describe('getNameById()', () => {
    it('deve retornar o nome quando o tipo existe pelo id', fakeAsync(() => {
      mockPort.list.mockReturnValue(of([{ id: 'g1', nome: 'Piscar' }]));
      facade.load();
      tick();

      expect(facade.getNameById('g1')).toBe('Piscar');
    }));

    it('deve retornar o id como fallback quando o tipo não existe', fakeAsync(() => {
      mockPort.list.mockReturnValue(of([]));
      facade.load();
      tick();

      expect(facade.getNameById('id-ausente')).toBe('id-ausente');
    }));
  });

  // -------------------------------------------------------------------------
  // Degradação silenciosa — GEST indisponível (SPEC-07)
  // -------------------------------------------------------------------------

  describe('degradação silenciosa', () => {
    it('deve popular error() e manter tipos() vazio quando port rejeita', fakeAsync(() => {
      mockPort.list.mockReturnValue(throwError(() => new Error('GEST offline')));

      facade.load();
      tick();

      expect(facade.tipos()).toEqual([]);
      expect(facade.error()).not.toBeNull();
      expect(facade.loading()).toBe(false);
    }));

    it('error() deve conter a mensagem de indisponibilidade de tipos de gesto', fakeAsync(() => {
      mockPort.list.mockReturnValue(throwError(() => new Error('timeout')));

      facade.load();
      tick();

      expect(facade.error()).toContain('indisponíveis');
    }));
  });
});
