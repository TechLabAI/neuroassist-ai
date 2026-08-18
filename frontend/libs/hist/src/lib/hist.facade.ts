import {
  Injectable,
  inject,
  signal,
  computed,
  DestroyRef,
} from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HIST_CLIENT_PORT } from './api/hist-client.port';
import { HIST_FEEDBACK_PORT } from './api/hist-feedback.port';
import {
  AuditRecordResponse,
  AppError,
  HistoryItemVm,
  HistoryQueryParams,
  HistoryScreenVm,
  ItemMarking,
  MarkingState,
  Period,
  ScreenStatus,
  Verdict,
  FeedbackWriteDto,
} from './hist.types';

// =============================================================================
// HistoryFacade — SPEC-03 §3/§4 (signals-based, no NgRx, no BehaviorSubject)
// Components interact ONLY with this facade; never with ports or adapters directly.
// =============================================================================

const PERIOD_DAYS: Record<Period, number> = { hoje: 1, semana: 7, mes: 30 };
const PAGE_SIZE = 50;

/** Map Period → from/to ISO bounds (SPEC-03 §5). */
function periodBounds(period: Period): { from: string; to: string } {
  const now = new Date();
  const to = now.toISOString();
  const from = new Date(now.getTime() - PERIOD_DAYS[period] * 24 * 60 * 60 * 1000).toISOString();
  return { from, to };
}

/** Format time "HHhMM" from ISO string. */
function fmtHora(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}h${String(d.getMinutes()).padStart(2, '0')}`;
}

/** Map AuditRecordResponse → HistoryItemVm. Single adaptation point (vision R4). */
function toVm(r: AuditRecordResponse): HistoryItemVm {
  const payload = r.payload as Record<string, unknown>;
  const rotulo = typeof payload['rotulo'] === 'string' ? payload['rotulo'] : r.factType;
  const urgency = (() => {
    const u = payload['urgencia'];
    if (u === 'emergencia' || u === 'critico') return 'critico' as const;
    if (u === 'atencao') return 'atencao' as const;
    return 'rotina' as const;
  })();
  const verdict = (() => {
    const v = payload['verdict'];
    if (v === 'real') return 'real' as Verdict;
    if (v === 'falso_positivo') return 'falso_positivo' as Verdict;
    return null;
  })();
  const confirmationLine = r.actor.displayNameSnapshot
    ? `Confirmado por ${r.actor.displayNameSnapshot} às ${fmtHora(r.recordedAt)}`
    : '';

  return {
    id: r.id,
    factType: r.factType,
    label: rotulo,
    urgency,
    occurredAtLabel: fmtHora(r.occurredAt),
    recordedAtIso: r.recordedAt,
    confirmationLine,
    verdict,
    correctsRecordId: r.correctsRecordId,
  };
}

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
export class HistoryFacade {
  private readonly histPort = inject(HIST_CLIENT_PORT);
  private readonly feedbackPort = inject(HIST_FEEDBACK_PORT);
  private readonly destroyRef = inject(DestroyRef);

  // ---------------------------------------------------------------------------
  // Private writable signals (SPEC-03 §3)
  // ---------------------------------------------------------------------------
  readonly #items   = signal<HistoryItemVm[]>([]);
  readonly #period  = signal<Period>('semana');
  readonly #status  = signal<ScreenStatus>('idle');
  readonly #page    = signal<number>(0);
  readonly #hasMore = signal<boolean>(false);
  readonly #error   = signal<AppError | null>(null);
  readonly #markings = signal<Map<string, ItemMarking>>(new Map());

  // Last query params — stored for retry()
  #lastPeriod: Period = 'semana';

  // ---------------------------------------------------------------------------
  // Public readonly signals
  // ---------------------------------------------------------------------------
  readonly items   = this.#items.asReadonly();
  readonly period  = this.#period.asReadonly();
  readonly status  = this.#status.asReadonly();
  readonly hasMore = this.#hasMore.asReadonly();
  readonly error   = this.#error.asReadonly();
  readonly markings = this.#markings.asReadonly();

  /** Assembled screen VM (SPEC-03 §3). */
  readonly screenVm = computed<HistoryScreenVm>(() => ({
    items: this.#items(),
    period: this.#period(),
    status: this.#status(),
    hasMore: this.#hasMore(),
    error: this.#error(),
  }));

  // ---------------------------------------------------------------------------
  // Public methods (SPEC-03 §4)
  // ---------------------------------------------------------------------------

  /**
   * load(period) — route entry / period change / retry.
   * Resets pagination to page 0. (SPEC-03 §4)
   */
  async load(period: Period = this.#period()): Promise<void> {
    this.#lastPeriod = period;
    this.#period.set(period);
    this.#status.set('loading');
    this.#page.set(0);
    this.#error.set(null);

    try {
      const bounds = periodBounds(period);
      const params: HistoryQueryParams = { ...bounds, order: 'desc', page: 0, size: PAGE_SIZE };
      const result = await lastValueFrom(this.histPort.list(params));
      this.#items.set(result.items.map(toVm));
      this.#hasMore.set((result.page + 1) * result.size < result.total);
      this.#status.set(result.items.length === 0 ? 'empty' : 'loaded');
    } catch (err) {
      this.#error.set(mapError(err, 'Erro ao carregar histórico'));
      this.#status.set('error');
    }
  }

  /**
   * setPeriod(period) — tab filter change.
   * Updates URL-bound period, resets page 0, reloads (SPEC-03 §4).
   */
  async setPeriod(period: Period): Promise<void> {
    await this.load(period);
  }

  /**
   * loadMore() — "carregar mais" affordance (SPEC-03 §4).
   * Appends next page to #items.
   */
  async loadMore(): Promise<void> {
    if (!this.#hasMore() || this.#status() === 'loading') return;

    const nextPage = this.#page() + 1;
    this.#status.set('loading');

    try {
      const bounds = periodBounds(this.#period());
      const params: HistoryQueryParams = {
        ...bounds,
        order: 'desc',
        page: nextPage,
        size: PAGE_SIZE,
      };
      const result = await lastValueFrom(this.histPort.list(params));
      this.#page.set(nextPage);
      this.#items.update((prev) => [...prev, ...result.items.map(toVm)]);
      this.#hasMore.set((result.page + 1) * result.size < result.total);
      this.#status.set('loaded');
    } catch (err) {
      this.#error.set(mapError(err, 'Erro ao carregar mais itens'));
      this.#status.set('error');
    }
  }

  /**
   * markFeedback(id, verdict) — non-optimistic feedback write (SPEC-03 §4 / SPEC-04 §2.4).
   * Sets item verdict only after backend confirms; reconciles on 409.
   */
  async markFeedback(id: string, verdict: Verdict): Promise<void> {
    this.#setMarking(id, 'marking');

    const dto: FeedbackWriteDto = { verdict };
    try {
      const resp = await lastValueFrom(this.feedbackPort.markFeedback(id, dto));
      // On success: set verdict from backend response (non-optimistic)
      this.#items.update((list) =>
        list.map((item) => (item.id === id ? { ...item, verdict: resp.verdict } : item)),
      );
      this.#setMarking(id, 'marked');
    } catch (err: unknown) {
      const status = err instanceof Object && 'status' in err
        ? (err as { status: number }).status
        : 0;

      if (status === 409) {
        // Already-terminal: reconcile to the existing backend verdict (SPEC-04 §2.4)
        const existing = err instanceof Object && 'response' in err
          ? (err as { response?: { verdict: Verdict } }).response
          : undefined;
        if (existing?.verdict) {
          this.#items.update((list) =>
            list.map((item) =>
              item.id === id ? { ...item, verdict: existing.verdict } : item,
            ),
          );
        }
        this.#setMarking(id, 'marked');
      } else {
        this.#setMarking(id, 'mark-error');
      }
    }
  }

  /**
   * retry() — InlineErrorComponent.retry → re-run last load (SPEC-03 §4).
   */
  async retry(): Promise<void> {
    await this.load(this.#lastPeriod);
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  #setMarking(id: string, state: MarkingState): void {
    this.#markings.update((m) => {
      const updated = new Map(m);
      updated.set(id, { id, state });
      return updated;
    });
  }
}
