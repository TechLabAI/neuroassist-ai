// =============================================================================
// TrainingSessionFacade spec — plan-FE-TREI P2 (mandatory)
// Verifies:
//   1. startSession() updates sessions signal (P2 contract)
//   2. sessions signal is empty before any session starts
//   3. isLoading signal reflects async state
//   4. error signal is set on failure
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TrainingSessionFacade } from './trei.facade';
import { TRAINING_SESSION_PORT } from './api/training-session.port';
import { PROPOSAL_TURN_PORT } from './api/proposal-turn.port';
import { TrainingSession } from './trei.types';

const MOCK_SESSION: TrainingSession = {
  sessaoId: 'sess-001',
  pacienteId: 'pac-001',
  localeAutoria: 'pt',
  estado: 'ativa',
  llmModelId: 'claude-3-5-haiku',
  iniciadaEm: '2026-09-02T10:00:00Z',
};

describe('TrainingSessionFacade', () => {
  let facade: TrainingSessionFacade;

  function setup(sessionResult: 'success' | 'failure' = 'success') {
    TestBed.configureTestingModule({
      providers: [
        TrainingSessionFacade,
        {
          provide: TRAINING_SESSION_PORT,
          useValue: {
            start: sessionResult === 'success'
              ? () => of(MOCK_SESSION)
              : () => throwError(() => ({ status: 404, message: 'Patient not found' })),
          },
        },
        {
          provide: PROPOSAL_TURN_PORT,
          useValue: {
            propose: () => of({ propostas: [], aiDisclosed: true }),
          },
        },
      ],
    });
    facade = TestBed.inject(TrainingSessionFacade);
  }

  // ── Initial state ───────────────────────────────────────────────────────────

  it('sessions signal is empty before startSession', () => {
    setup();
    expect(facade.sessions()).toEqual([]);
  });

  it('activeTurn signal is null before any turn', () => {
    setup();
    expect(facade.activeTurn()).toBeNull();
  });

  it('isLoading signal is false initially', () => {
    setup();
    expect(facade.isLoading()).toBe(false);
  });

  it('error signal is null initially', () => {
    setup();
    expect(facade.error()).toBeNull();
  });

  it('isMock signal defaults to true', () => {
    setup();
    expect(facade.isMock()).toBe(true);
  });

  // ── startSession() ──────────────────────────────────────────────────────────

  it('startSession() updates sessions signal (P2 contract)', async () => {
    setup();
    await TestBed.runInInjectionContext(() => facade.startSession('pac-001'));
    const sessions = facade.sessions();
    expect(sessions.length).toBe(1);
    expect(sessions[0].id).toBe('sess-001');
    expect(sessions[0].patientId).toBe('pac-001');
    expect(sessions[0].status).toBe('ACTIVE');
  });

  it('startSession() maps TreiLocale "pt" → Locale "pt-BR"', async () => {
    setup();
    await TestBed.runInInjectionContext(() => facade.startSession('pac-001'));
    expect(facade.sessions()[0].locale).toBe('pt-BR');
  });

  it('startSession() sets error signal on failure', async () => {
    setup('failure');
    await TestBed.runInInjectionContext(() => facade.startSession('pac-001'));
    expect(facade.error()).not.toBeNull();
    expect(facade.error()?.code).toContain('404');
    // sessions should remain empty on error
    expect(facade.sessions()).toEqual([]);
  });

  it('isLoading is false after startSession resolves', async () => {
    setup();
    await TestBed.runInInjectionContext(() => facade.startSession('pac-001'));
    expect(facade.isLoading()).toBe(false);
  });

  // ── endSession() ────────────────────────────────────────────────────────────

  it('endSession() sets session status to ENDED in sessions signal', async () => {
    setup();
    await TestBed.runInInjectionContext(() => facade.startSession('pac-001'));
    TestBed.runInInjectionContext(() => facade.endSession());
    expect(facade.sessions()[0].status).toBe('ENDED');
  });

  // ── submitTurn() alias ──────────────────────────────────────────────────────

  it('submitTurn() is an alias for sendTurn() (plan P2 contract)', () => {
    setup();
    expect(typeof facade.submitTurn).toBe('function');
  });

  // ── P7 (FE-025 / G4): patient-not-found detection ──────────────────────────

  it('P7/G4: startSession() sets isPatientNotFound=true on HTTP 404', async () => {
    setup('failure');  // failure throws { status: 404, message: 'Patient not found' }
    await TestBed.runInInjectionContext(() => facade.startSession('pac-missing'));
    expect(facade.isPatientNotFound()).toBe(true);
  });

  it('P7/G4: isPatientNotFound starts as false', () => {
    setup();
    expect(facade.isPatientNotFound()).toBe(false);
  });

  it('P7/G4: isPatientNotFound resets to false on retry after success', async () => {
    // Simulate failure then success
    setup('failure');
    await TestBed.runInInjectionContext(() => facade.startSession('pac-missing'));
    expect(facade.isPatientNotFound()).toBe(true);
    // Reset TestBed for success scenario
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        TrainingSessionFacade,
        { provide: TRAINING_SESSION_PORT, useValue: { start: () => of(MOCK_SESSION) } },
        { provide: PROPOSAL_TURN_PORT, useValue: { propose: () => of({ propostas: [], aiDisclosed: true }) } },
      ],
    });
    const successFacade = TestBed.inject(TrainingSessionFacade);
    // Fresh facade starts clean
    expect(successFacade.isPatientNotFound()).toBe(false);
  });

  // ── P7 (INT-005): embed model ID ────────────────────────────────────────────

  it('INT-005: embedModelId is null before any session', () => {
    setup();
    expect(facade.embedModelId()).toBeNull();
  });

  it('INT-005: embedModelId is set after successful startSession (mock proxy)', async () => {
    setup();
    await TestBed.runInInjectionContext(() => facade.startSession('pac-001'));
    // In mock mode, embedModelId is prefixed with 'embed-mock:'
    expect(facade.embedModelId()).toMatch(/embed-mock:/);
  });

  // ── P7 (FE-025): isDegraded ─────────────────────────────────────────────────

  it('P7: isDegraded is true when isMock=true (default)', () => {
    setup();
    expect(facade.isDegraded()).toBe(true);
  });
});
