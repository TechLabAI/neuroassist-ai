import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { PacPacienteFacade } from './pac-paciente.facade';
import { PAC_PACIENTE_PORT, Paciente, Page } from './api/paciente.port';

// =============================================================================
// PacPacienteFacade — unit tests (P6 / PAC-FE-010)
// Port is mocked via overrideProvider. Covers:
//   - loadCurrent() → hasPatient() transita false → true
//   - save() → idempotencyKey emitida ao port; saveState idle → saving → saved
//   - update() → patient reflectido no signal; saveState ciclo completo
//   - Erros → saveState = 'error'
// =============================================================================

function buildPaciente(overrides?: Partial<Paciente>): Paciente {
  return {
    id: 'p1',
    tenantId: 't1',
    nome: 'Paciente Teste',
    dispositivo: null,
    ...overrides,
  };
}

function buildPage(content: Paciente[]): Page<Paciente> {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    number: 0,
    size: 1,
  };
}

function createMockPort() {
  return {
    list: jest.fn().mockReturnValue(of(buildPage([]))),
    get: jest.fn(),
    create: jest.fn().mockReturnValue(
      of(new HttpResponse<Paciente>({ status: 201, body: buildPaciente() })),
    ),
    update: jest.fn().mockReturnValue(of(buildPaciente({ nome: 'Atualizado' }))),
  };
}

describe('PacPacienteFacade', () => {
  let facade: PacPacienteFacade;
  let mockPort: ReturnType<typeof createMockPort>;

  beforeEach(() => {
    mockPort = createMockPort();

    TestBed.configureTestingModule({
      providers: [
        PacPacienteFacade,
        { provide: PAC_PACIENTE_PORT, useValue: mockPort },
      ],
    });

    facade = TestBed.inject(PacPacienteFacade);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  // -------------------------------------------------------------------------
  // Estado inicial
  // -------------------------------------------------------------------------

  describe('estado inicial', () => {
    it('patient() deve ser null', () => {
      expect(facade.patient()).toBeNull();
    });

    it('hasPatient() deve ser false quando não há paciente', () => {
      expect(facade.hasPatient()).toBe(false);
    });

    it('saveState() deve ser "idle"', () => {
      expect(facade.saveState()).toBe('idle');
    });

    it('loading() deve ser false', () => {
      expect(facade.loading()).toBe(false);
    });

    it('error() deve ser null', () => {
      expect(facade.error()).toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // loadCurrent() — hasPatient transita false → true
  // Facades usam from(lastValueFrom(...)) que resolve via Promise microtask;
  // fakeAsync + tick() é necessário para sincronizar os signals após load.
  // -------------------------------------------------------------------------

  describe('loadCurrent()', () => {
    it('deve setar hasPatient() para true quando port retorna paciente', fakeAsync(() => {
      mockPort.list.mockReturnValue(of(buildPage([buildPaciente()])));
      expect(facade.hasPatient()).toBe(false);

      facade.loadCurrent();
      tick();

      expect(facade.hasPatient()).toBe(true);
    }));

    it('patient() deve refletir os dados do paciente carregado', fakeAsync(() => {
      const paciente = buildPaciente({ nome: 'Maria', id: 'p-maria' });
      mockPort.list.mockReturnValue(of(buildPage([paciente])));

      facade.loadCurrent();
      tick();

      expect(facade.patient()?.id).toBe('p-maria');
      expect(facade.patient()?.nome).toBe('Maria');
    }));

    it('hasPatient() deve permanecer false quando port retorna lista vazia', fakeAsync(() => {
      mockPort.list.mockReturnValue(of(buildPage([])));

      facade.loadCurrent();
      tick();

      expect(facade.hasPatient()).toBe(false);
      expect(facade.patient()).toBeNull();
    }));

    it('loading() deve ser false após resolução', fakeAsync(() => {
      mockPort.list.mockReturnValue(of(buildPage([buildPaciente()])));

      facade.loadCurrent();
      tick();

      expect(facade.loading()).toBe(false);
    }));

    it('error() deve ser populado e loading false em caso de falha', fakeAsync(() => {
      mockPort.list.mockReturnValue(throwError(() => new Error('Server error')));

      facade.loadCurrent();
      tick();

      expect(facade.error()).not.toBeNull();
      expect(facade.loading()).toBe(false);
    }));
  });

  // -------------------------------------------------------------------------
  // save() — idempotência e ciclo saveState idle → saving → saved
  // -------------------------------------------------------------------------

  describe('save()', () => {
    it('deve transitar saveState de "idle" para "saved" no happy path', fakeAsync(() => {
      mockPort.create.mockReturnValue(
        of(new HttpResponse<Paciente>({ status: 201, body: buildPaciente() })),
      );

      facade.save({ nome: 'Novo Paciente' });
      tick();

      expect(facade.saveState()).toBe('saved');
    }));

    it('deve chamar port.create com o dto fornecido', () => {
      facade.save({ nome: 'Jamilly' });

      expect(mockPort.create).toHaveBeenCalledTimes(1);
      const [dto] = mockPort.create.mock.calls[0];
      expect(dto.nome).toBe('Jamilly');
    });

    it('deve emitir um idempotencyKey único ao port.create', () => {
      // idempotencyKey é gerado antes da chamada async, pode checar sincronamente
      facade.save({ nome: 'Jamilly' });
      facade.save({ nome: 'Jamilly' });

      const key1: string = mockPort.create.mock.calls[0][1];
      const key2: string = mockPort.create.mock.calls[1][1];

      expect(typeof key1).toBe('string');
      expect(key1.length).toBeGreaterThan(0);
      expect(key1).not.toBe(key2);
    });

    it('deve popular patient() com o body da resposta 201', fakeAsync(() => {
      const pacienteCriado = buildPaciente({ id: 'p-novo', nome: 'Paciente Criado' });
      mockPort.create.mockReturnValue(
        of(new HttpResponse<Paciente>({ status: 201, body: pacienteCriado })),
      );

      facade.save({ nome: 'Paciente Criado' });
      tick();

      expect(facade.patient()?.id).toBe('p-novo');
      expect(facade.saveState()).toBe('saved');
    }));

    it('saveState() deve transitar para "error" quando port.create falha', fakeAsync(() => {
      mockPort.create.mockReturnValue(throwError(() => new Error('Network error')));

      facade.save({ nome: 'X' });
      tick();

      expect(facade.saveState()).toBe('error');
    }));
  });

  // -------------------------------------------------------------------------
  // update() — patient reflectido no signal; saveState ciclo completo
  // -------------------------------------------------------------------------

  describe('update()', () => {
    it('deve transitar saveState para "saved" no happy path', fakeAsync(() => {
      mockPort.update.mockReturnValue(of(buildPaciente({ nome: 'Nome Atualizado' })));

      facade.update('p1', { nome: 'Nome Atualizado' }, 'etag-v1');
      tick();

      expect(facade.saveState()).toBe('saved');
    }));

    it('deve reflectir o patient() atualizado após update bem-sucedido', fakeAsync(() => {
      mockPort.update.mockReturnValue(of(buildPaciente({ nome: 'Nome Atualizado', id: 'p1' })));

      facade.update('p1', { nome: 'Nome Atualizado' }, 'etag-v1');
      tick();

      expect(facade.patient()?.nome).toBe('Nome Atualizado');
    }));

    it('saveState() deve transitar para "error" quando port.update falha', fakeAsync(() => {
      mockPort.update.mockReturnValue(throwError(() => new Error('Conflict')));

      facade.update('p1', { nome: 'X' }, 'wrong-etag');
      tick();

      expect(facade.saveState()).toBe('error');
    }));
  });
});
