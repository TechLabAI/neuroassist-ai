import { TestBed } from '@angular/core/testing';
import { ObservabilityService } from '@neuroassist/shared/observability';
import { PacTelemetryService } from './pac-telemetry.service';

// =============================================================================
// PacTelemetryService — unit tests (PAC-FE-022 / P8 Observability)
//
// Verifies that each telemetry method emits exactly the expected event payload
// via ObservabilityService.emit.
//
// Strategy: ObservabilityService is replaced by a jest.fn() spy — zero real I/O,
// no Elasticsearch, no analytics backend.
//
// Acceptance criteria (from PAC-FE-022):
//   - trackCatalogLoaded(5) → emit({ event: 'pac.catalog.loaded', count: 5 })
//   - trackEmergencyGateBlocked(id) → emit({ event: 'pac.error.emergency_gate_blocked', comandoId: id })
// =============================================================================

describe('PacTelemetryService', () => {
  let service: PacTelemetryService;
  let emitSpy: jest.Mock;

  beforeEach(() => {
    emitSpy = jest.fn();

    TestBed.configureTestingModule({
      providers: [
        PacTelemetryService,
        {
          provide: ObservabilityService,
          useValue: { emit: emitSpy } as Pick<ObservabilityService, 'emit'>,
        },
      ],
    });

    service = TestBed.inject(PacTelemetryService);
  });

  afterEach(() => TestBed.resetTestingModule());

  // ---------------------------------------------------------------------------
  // trackCatalogLoaded
  // ---------------------------------------------------------------------------

  describe('trackCatalogLoaded', () => {
    it('deve emitir pac.catalog.loaded com count correto', () => {
      service.trackCatalogLoaded(5);

      expect(emitSpy).toHaveBeenCalledTimes(1);
      expect(emitSpy).toHaveBeenCalledWith({ event: 'pac.catalog.loaded', count: 5 });
    });

    it('deve emitir count=0 quando catálogo retorna vazio', () => {
      service.trackCatalogLoaded(0);
      expect(emitSpy).toHaveBeenCalledWith({ event: 'pac.catalog.loaded', count: 0 });
    });

    it('deve emitir contagem exata quando catálogo tem múltiplos itens', () => {
      service.trackCatalogLoaded(42);
      expect(emitSpy).toHaveBeenCalledWith({ event: 'pac.catalog.loaded', count: 42 });
    });
  });

  // ---------------------------------------------------------------------------
  // trackComandoSaved
  // ---------------------------------------------------------------------------

  describe('trackComandoSaved', () => {
    it('deve emitir pac.comando.saved com urgencia rotina', () => {
      service.trackComandoSaved('rotina');
      expect(emitSpy).toHaveBeenCalledWith({ event: 'pac.comando.saved', urgencia: 'rotina' });
    });

    it('deve emitir pac.comando.saved com urgencia emergencia', () => {
      service.trackComandoSaved('emergencia');
      expect(emitSpy).toHaveBeenCalledWith({ event: 'pac.comando.saved', urgencia: 'emergencia' });
    });
  });

  // ---------------------------------------------------------------------------
  // trackLifecycleTransition
  // ---------------------------------------------------------------------------

  describe('trackLifecycleTransition', () => {
    it('deve emitir pac.comando.published com comandoId', () => {
      service.trackLifecycleTransition('published', 'c-123');
      expect(emitSpy).toHaveBeenCalledWith({ event: 'pac.comando.published', comandoId: 'c-123' });
    });

    it('deve emitir pac.comando.recalibrated com comandoId', () => {
      service.trackLifecycleTransition('recalibrated', 'c-456');
      expect(emitSpy).toHaveBeenCalledWith({ event: 'pac.comando.recalibrated', comandoId: 'c-456' });
    });

    it('deve emitir pac.comando.retired com comandoId', () => {
      service.trackLifecycleTransition('retired', 'c-789');
      expect(emitSpy).toHaveBeenCalledWith({ event: 'pac.comando.retired', comandoId: 'c-789' });
    });

    it('deve emitir pac.comando.validated com comandoId', () => {
      service.trackLifecycleTransition('validated', 'c-999');
      expect(emitSpy).toHaveBeenCalledWith({ event: 'pac.comando.validated', comandoId: 'c-999' });
    });

    it('deve emitir pac.comando.saved com comandoId (via trackLifecycleTransition)', () => {
      service.trackLifecycleTransition('saved', 'c-001');
      expect(emitSpy).toHaveBeenCalledWith({ event: 'pac.comando.saved', comandoId: 'c-001' });
    });

    it('deve emitir apenas um evento por transição', () => {
      service.trackLifecycleTransition('retired', 'c-100');
      expect(emitSpy).toHaveBeenCalledTimes(1);
    });
  });

  // ---------------------------------------------------------------------------
  // trackEmergencyGateBlocked (safety-critical)
  // ---------------------------------------------------------------------------

  describe('trackEmergencyGateBlocked (safety-critical — PAC-FE-022 acceptance criteria)', () => {
    it('deve emitir pac.error.emergency_gate_blocked com comandoId', () => {
      service.trackEmergencyGateBlocked('c-456');

      expect(emitSpy).toHaveBeenCalledTimes(1);
      expect(emitSpy).toHaveBeenCalledWith({
        event: 'pac.error.emergency_gate_blocked',
        comandoId: 'c-456',
      });
    });

    it('deve emitir com "unknown" quando comandoId é "unknown" (novo draft sem ID ainda)', () => {
      service.trackEmergencyGateBlocked('unknown');
      expect(emitSpy).toHaveBeenCalledWith({
        event: 'pac.error.emergency_gate_blocked',
        comandoId: 'unknown',
      });
    });
  });

  // ---------------------------------------------------------------------------
  // trackConflictPrecondition
  // ---------------------------------------------------------------------------

  describe('trackConflictPrecondition', () => {
    it('deve emitir pac.error.conflict_precondition com comandoId', () => {
      service.trackConflictPrecondition('c-789');

      expect(emitSpy).toHaveBeenCalledTimes(1);
      expect(emitSpy).toHaveBeenCalledWith({
        event: 'pac.error.conflict_precondition',
        comandoId: 'c-789',
      });
    });
  });
});
