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
  LlmProposal,
  TreiLocale,
  TrainingSessionScreenVm,
  AppError,
  StartSessionRequest,
  ProposeRequest,
} from './trei.types';

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

  // ---------------------------------------------------------------------------
  // Private writable signals (SPEC-03 §3 / ADR-007 FE)
  // ---------------------------------------------------------------------------
  readonly #session = signal<TrainingSession | null>(null);
  readonly #messages = signal<ChatMessage[]>([]);
  readonly #activeLocale = signal<TreiLocale>('pt');
  readonly #status = signal<'idle' | 'loading' | 'active' | 'error'>('idle');
  readonly #isAssistantThinking = signal<boolean>(false);
  readonly #error = signal<AppError | null>(null);

  // ---------------------------------------------------------------------------
  // Public readonly signals
  // ---------------------------------------------------------------------------
  readonly session = this.#session.asReadonly();
  readonly messages = this.#messages.asReadonly();
  readonly activeLocale = this.#activeLocale.asReadonly();
  readonly status = this.#status.asReadonly();
  readonly isAssistantThinking = this.#isAssistantThinking.asReadonly();
  readonly error = this.#error.asReadonly();

  /** Assembled screen VM (SPEC-03 §3). */
  readonly screenVm = computed<TrainingSessionScreenVm>(() => ({
    session: this.#session(),
    messages: this.#messages(),
    activeLocale: this.#activeLocale(),
    status: this.#status() === 'active' ? 'loaded' : this.#status() === 'idle' ? 'idle' : this.#status() === 'error' ? 'error' : 'loading',
    isAssistantThinking: this.#isAssistantThinking(),
    error: this.#error(),
  }));

  // ---------------------------------------------------------------------------
  // Public methods (SPEC-03 §4)
  // ---------------------------------------------------------------------------

  /** startSession — initialize a new LLM authoring session (empty-state CTA). */
  async startSession(pacienteId: string): Promise<void> {
    this.#status.set('loading');
    this.#error.set(null);
    this.#messages.set([]);

    const request: StartSessionRequest = {
      pacienteId,
      localeAutoria: this.#activeLocale(),
    };

    try {
      const session = await lastValueFrom(this.sessionPort.start(request));
      this.#session.set(session);
      this.#status.set('active');
    } catch (err) {
      this.#error.set(mapError(err, 'Erro ao iniciar sessão de treinamento'));
      this.#status.set('error');
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
    } catch (err) {
      this.#isAssistantThinking.set(false);
      const appErr = mapError(err, 'Erro ao gerar propostas — tente novamente');
      this.#error.set(appErr);
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

  /** retry — retry after a startSession error. */
  async retry(pacienteId: string): Promise<void> {
    await this.startSession(pacienteId);
  }
}
