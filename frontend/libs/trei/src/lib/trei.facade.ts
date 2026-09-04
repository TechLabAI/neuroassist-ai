import {
  Injectable,
  inject,
  signal,
  computed,
  DestroyRef,
} from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TRAINING_SESSION_PORT } from './api/training-session.port';
import { PROPOSAL_TURN_PORT } from './api/proposal-turn.port';
import {
  TrainingSession,
  ChatMessage,
  TreiLocale,
  TrainingSessionScreenVm,
  AppError,
  StartSessionRequest,
  ProposeRequest,
  SessionVM,
  TurnVM,
  SessionStatus,
  Locale,
} from './trei.types';
import { TreiTelemetryService } from './trei-telemetry.service';

// =============================================================================
// TrainingSessionFacade — SPEC-03 §3/§4 (training session + conversation)
// Manages:
//   - session lifecycle (start / end in FE only)
//   - conversation messages (caregiver turns + assistant proposals)
//   - active locale (PT/EN selector — locale-independent per ADR-018)
//   - assistant-thinking state (isAssistantThinking signal)
//
// INVARIANT: the FE never holds an Anthropic API key and never calls the model
// directly. Every generated token arrives via this facade's propose() through
// trei-service (ADR-005 local, TECH-007). This is enforced by DI — PROPOSAL_TURN_PORT
// is the only path to LLM content.
// =============================================================================

let messageCounter = 0;

function mapError(err: unknown, fallback: string): AppError {
  if (err instanceof Object && 'status' in err) {
    const e = err as { status: number; message?: string };
    return { code: `HTTP_${e.status}`, message: e.message ?? fallback, status: e.status };
  }
  if (err instanceof Error) {
    return { code: 'UNKNOWN', message: err.message };
  }
  return { code: 'UNKNOWN', message: fallback };
}

@Injectable({ providedIn: 'root' })
export class TrainingSessionFacade {
  private readonly sessionPort = inject(TRAINING_SESSION_PORT);
  private readonly proposalPort = inject(PROPOSAL_TURN_PORT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly telemetry = inject(TreiTelemetryService);

  // ---------------------------------------------------------------------------
  // Private writable signals (SPEC-03 §3 / ADR-007 FE)
  // ---------------------------------------------------------------------------
  readonly #session = signal<TrainingSession | null>(null);
  readonly #messages = signal<ChatMessage[]>([]);
  readonly #activeLocale = signal<TreiLocale>('pt');
  readonly #status = signal<'idle' | 'loading' | 'active' | 'error'>('idle');
  readonly #isAssistantThinking = signal<boolean>(false);
  readonly #error = signal<AppError | null>(null);

  // P7 (FE-025 / G4): patient-not-found detection
  readonly #isPatientNotFound = signal<boolean>(false);

  // P7 (INT-005 / G5-link): embed model ID — set on session start; null = not yet known
  // In mock mode this is pre-seeded to reflect the mock environment.
  // When trei-service is online, this will come from the health endpoint (deferred to P8/P9).
  readonly #embedModelId = signal<string | null>(null);

  // ---------------------------------------------------------------------------
  // Public readonly signals — existing API (preserved for backward compat)
  // ---------------------------------------------------------------------------
  readonly session = this.#session.asReadonly();
  readonly messages = this.#messages.asReadonly();
  readonly activeLocale = this.#activeLocale.asReadonly();
  readonly status = this.#status.asReadonly();
  readonly isAssistantThinking = this.#isAssistantThinking.asReadonly();
  readonly error = this.#error.asReadonly();

  // P7 new public signals
  readonly isPatientNotFound = this.#isPatientNotFound.asReadonly();
  readonly embedModelId = this.#embedModelId.asReadonly();

  // ---------------------------------------------------------------------------
  // Plan P2 required signals (SPEC-03 §2 / plan-FE-TREI §P2 contract)
  // ---------------------------------------------------------------------------

  /**
   * isLoading — true while any async operation (startSession / sendTurn) is in-flight.
   * Computed from #status; preserves backward compat with status signal.
   */
  readonly isLoading = computed<boolean>(() => this.#status() === 'loading');

  /**
   * isMock — true when the mock adapter is active (trio-service not yet deployed).
   * Switch to false in provideTrei({ adapter: 'http' }) when trei-service is available.
   */
  readonly isMock = signal<boolean>(true);

  /**
   * isDegraded — P7 (FE-025): health-degraded affordance.
   * Currently computed from isMock: mock mode = degraded (no real AI pipeline).
   * When trei-service is online, this will reflect the real health status (P8/P9).
   */
  readonly isDegraded = computed<boolean>(() => this.isMock());

  /**
   * sessions — assembled list of SessionVMs from the current session.
   * During P1-P8, at most one session is active (session lifecycle is single-session).
   * Returns [] when no session is active; [sessionVM] when one is.
   */
  readonly sessions = computed<SessionVM[]>(() => {
    const session = this.#session();
    if (!session) return [];

    const status = this.#mapEstadoToStatus(session.estado);
    const locale = this.#mapLocaleToVM(session.localeAutoria);
    const turns = this.#messages().map<TurnVM>((msg) => ({
      id: msg.id,
      role: msg.role === 'caregiver' ? 'user' : 'assistant',
      content: msg.content,
      createdAt: msg.timestamp,
    }));

    return [{ id: session.sessaoId, patientId: session.pacienteId, status, locale, turns }];
  });

  /**
   * activeTurn — the most recent assistant turn (the latest assistant message).
   * null when no assistant has responded yet or the session is idle.
   */
  readonly activeTurn = computed<TurnVM | null>(() => {
    const msgs = this.#messages();
    for (let i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].role === 'assistant') {
        return {
          id: msgs[i].id,
          role: 'assistant',
          content: msgs[i].content,
          createdAt: msgs[i].timestamp,
        };
      }
    }
    return null;
  });

  /** Assembled screen VM (SPEC-03 §3). */
  readonly screenVm = computed<TrainingSessionScreenVm>(() => ({
    session: this.#session(),
    messages: this.#messages(),
    activeLocale: this.#activeLocale(),
    status: this.#status() === 'active' ? 'loaded' : this.#status() === 'idle' ? 'idle' : this.#status() === 'error' ? 'error' : 'loading',
    isAssistantThinking: this.#isAssistantThinking(),
    error: this.#error(),
    // P7 (FE-025 / G4): patient-not-found and degraded state
    isPatientNotFound: this.#isPatientNotFound(),
    isDegraded: this.isDegraded(),
    // P7 (INT-005): embed model ID for CONV readiness display
    embedModelId: this.#embedModelId(),
  }));

  // ---------------------------------------------------------------------------
  // Public methods (SPEC-03 §4)
  // ---------------------------------------------------------------------------

  /** startSession — initialize a new LLM authoring session (empty-state CTA). */
  async startSession(pacienteId: string): Promise<void> {
    this.#status.set('loading');
    this.#error.set(null);
    this.#messages.set([]);
    // Reset patient-not-found flag on each attempt (P7/FE-025/G4)
    this.#isPatientNotFound.set(false);

    const request: StartSessionRequest = {
      pacienteId,
      localeAutoria: this.#activeLocale(),
    };

    try {
      const session = await lastValueFrom(this.sessionPort.start(request));
      this.#session.set(session);
      this.#status.set('active');
      // P7 (INT-005): set embed model ID from session response — llmModelId acts as
      // proxy for AI pipeline availability until health endpoint is wired (P8/P9).
      // In mock mode a static embed model value is used; HTTP mode derives from session.
      this.#embedModelId.set(
        this.isMock()
          ? `embed-mock:${session.llmModelId}`
          : session.llmModelId,
      );
      // P8 (FE-026): telemetry — session started (opaque sessaoId only; no patientId per SEC-002)
      this.telemetry.trackSessionStarted(session.sessaoId);
    } catch (err) {
      const appError = mapError(err, 'Erro ao iniciar sessão de treinamento');
      this.#error.set(appError);
      this.#status.set('error');
      // P8 (FE-026): telemetry — session error (errorCode only; no sensitive data)
      const errorCode = err instanceof Object && 'status' in err
        ? `HTTP_${(err as { status: number }).status}`
        : 'UNKNOWN';
      this.telemetry.trackSessionError(errorCode);

      // P7 (FE-025 / G4): detect patient-not-found (HTTP 404) — show specific banner
      const is404 =
        err instanceof Object &&
        'status' in err &&
        (err as { status: number }).status === 404;
      if (is404) {
        this.#isPatientNotFound.set(true);
      }
    }
  }

  /**
   * sendTurn — send a caregiver instruction and receive LLM proposals.
   * Adds a caregiver message, then an assistant message with proposals.
   * Sets isAssistantThinking during the LLM call (first-token ≤ 2s signal from vision §7).
   */
  async sendTurn(instrucao: string, parentNoId: string, tipo: 'frase' | 'categoria'): Promise<void> {
    const session = this.#session();
    if (!session) return;

    // Add caregiver turn to the conversation
    const caregiverMsg: ChatMessage = {
      id: `msg-${++messageCounter}`,
      role: 'caregiver',
      content: instrucao,
      timestamp: new Date().toISOString(),
    };
    this.#messages.update((msgs) => [...msgs, caregiverMsg]);
    this.#isAssistantThinking.set(true);
    this.#error.set(null);

    const request: ProposeRequest = { instrucao, parentNoId, tipo };

    try {
      const response = await lastValueFrom(
        this.proposalPort.propose(session.sessaoId, request),
      );

      // Add assistant message with proposals
      const assistantMsg: ChatMessage = {
        id: `msg-${++messageCounter}`,
        role: 'assistant',
        content: `Aqui estão ${response.propostas.length} proposta(s) para "${instrucao}":`,
        timestamp: new Date().toISOString(),
        proposals: response.propostas,
        aiDisclosed: response.aiDisclosed,
      };
      this.#messages.update((msgs) => [...msgs, assistantMsg]);
      this.#isAssistantThinking.set(false);
      // P8 (FE-026): telemetry — turn submitted (opaque sessaoId only; no patient data per SEC-002)
      this.telemetry.trackTurnSubmitted(session.sessaoId);
    } catch (err) {
      this.#isAssistantThinking.set(false);
      const appErr = mapError(err, 'Erro ao gerar propostas — tente novamente');
      this.#error.set(appErr);
      // P8 (FE-026): telemetry — turn error (opaque sessaoId only; no patient data)
      this.telemetry.trackTurnError(session.sessaoId);
    }
  }

  /** setActiveLocale — switch the authoring locale (ADR-018: locale-independent). */
  setActiveLocale(locale: TreiLocale): void {
    this.#activeLocale.set(locale);
  }

  /**
   * endSession — end the training session (FE-side only; proposals stay as drafts).
   * No-loss guarantee (SPEC-05 success signal): pending proposals are NOT discarded.
   */
  endSession(): void {
    const session = this.#session();
    if (!session) return;
    this.#session.set({ ...session, estado: 'encerrada' });
    this.#status.set('idle');
  }

  /** retry — retry after a startSession error (P7: also resets isPatientNotFound). */
  async retry(pacienteId: string): Promise<void> {
    await this.startSession(pacienteId);
  }

  /**
   * submitTurn — alias for sendTurn (plan P2 contract).
   * Sends a caregiver instruction and receives LLM proposals.
   */
  async submitTurn(instrucao: string, parentNoId: string, tipo: 'frase' | 'categoria'): Promise<void> {
    return this.sendTurn(instrucao, parentNoId, tipo);
  }

  // ---------------------------------------------------------------------------
  // Private mapping helpers (P2 view model computation)
  // ---------------------------------------------------------------------------

  #mapEstadoToStatus(estado: 'ativa' | 'encerrada'): SessionStatus {
    return estado === 'ativa' ? 'ACTIVE' : 'ENDED';
  }

  #mapLocaleToVM(locale: TreiLocale): Locale {
    return locale === 'pt' ? 'pt-BR' : 'en-US';
  }
}
