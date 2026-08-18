import { Injectable, inject, signal, computed, DestroyRef } from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DASH_SUMMARY_PORT } from './api/dash-summary.port';
import {
  DashSummaryResponse,
  HomeScreenVm,
  PatientSummaryVm,
  DeviceVm,
  LatestEventVm,
  AppError,
  ScreenStatus,
} from './dash.types';

// =============================================================================
// DashHomeFacade — SPEC-03 §3/§4 (signals-based, no NgRx, no BehaviorSubject)
// Aggregates GET /panel/summary for the HomeStatusComponent.
// Components interact ONLY with this facade; never with ports/adapters directly.
// SSE stream integration handled by DashStreamClient (scope:web layer) — out of DASH scope.
// TODO[INT-FE-DASH-001]: activeEvent from EVNT injected at scope:web (not a DASH dep).
// TODO[INT-FE-DASH-002]: deviceBadge from EDGE injected at scope:web (not a DASH dep).
// =============================================================================

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
export class DashHomeFacade {
  private readonly summaryPort = inject(DASH_SUMMARY_PORT);
  private readonly destroyRef = inject(DestroyRef);

  // ---------------------------------------------------------------------------
  // Private writable signals
  // ---------------------------------------------------------------------------
  readonly #status  = signal<ScreenStatus>('idle');
  readonly #summary = signal<DashSummaryResponse | null>(null);
  readonly #error   = signal<AppError | null>(null);

  // ---------------------------------------------------------------------------
  // Public readonly signals
  // ---------------------------------------------------------------------------
  readonly status  = this.#status.asReadonly();
  readonly summary = this.#summary.asReadonly();
  readonly error   = this.#error.asReadonly();
  readonly loading = computed(() => this.#status() === 'loading');

  /** Primary patient (scope-resolved by BE; FE shows patients[0] in MVP). */
  readonly patient = computed<PatientSummaryVm | null>(
    () => this.#summary()?.patients[0] ?? null,
  );

  /** Caregiver greeting name. */
  readonly caregiverName = computed<string>(
    () => this.#summary()?.caregiver.name ?? '',
  );

  /** Device for the primary patient. */
  readonly device = computed<DeviceVm | null>(
    () => this.patient()?.device ?? null,
  );

  /** Latest event for the primary patient. */
  readonly latestEvent = computed<LatestEventVm | null>(
    () => this.patient()?.latestEvent ?? null,
  );

  /** Unacknowledged notification count for the primary patient. */
  readonly unacknowledgedCount = computed<number>(
    () => this.patient()?.unacknowledgedCount ?? 0,
  );

  /** True if any upstream section is degraded. */
  readonly hasDegradedSections = computed<boolean>(
    () => (this.#summary()?.degradedSections.length ?? 0) > 0,
  );

  /** Assembled screen VM. */
  readonly screenVm = computed<HomeScreenVm>(() => ({
    status: this.#status(),
    summary: this.#summary(),
    error: this.#error(),
  }));

  // ---------------------------------------------------------------------------
  // Public methods
  // ---------------------------------------------------------------------------

  /** load() — entry point; called from HomeStatusComponent.ngOnInit(). */
  async load(): Promise<void> {
    this.#status.set('loading');
    this.#error.set(null);

    try {
      const result = await lastValueFrom(
        this.summaryPort.getSummary().pipe(takeUntilDestroyed(this.destroyRef)),
      );
      this.#summary.set(result);
      this.#status.set(result.patients.length === 0 ? 'empty' : 'loaded');
    } catch (err) {
      this.#error.set(mapError(err, 'Erro ao carregar status do painel'));
      this.#status.set('error');
    }
  }

  /** retry() — InlineErrorComponent.retry → re-run load. */
  async retry(): Promise<void> {
    await this.load();
  }
}
