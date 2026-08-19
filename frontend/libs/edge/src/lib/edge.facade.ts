import {
  Injectable,
  inject,
  signal,
  computed,
  DestroyRef,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer, Subscription, switchMap, of, catchError, retry, defer } from 'rxjs';
import { DEVICE_PORT } from './api/device.port';
import { EDGE_TOAST_TOKEN, EDGE_RETRY_TOAST_MESSAGE } from './edge-toast.token';
import {
  DispositivoEdgeView,
  LivenessView,
  NotModified,
  DerivedState,
  ConnectivityTone,
  DeviceConnectivityVM,
  DeviceDetailVM,
} from '@neuroassist/shared/contracts';
import { ObservabilityService } from '@neuroassist/shared/observability';

// ---------------------------------------------------------------------------
// Feature flags consumed from SPEC-05
// ---------------------------------------------------------------------------

export const MODULE_FEATURE_FLAGS = {
  pollingEnabled: { default: true },
  pollIntervalMs: { default: 15_000 }, // api-contract §5 advisory hint
  stalenessThresholdMs: { default: 45_000 }, // FE 'stale' band (earlier than BE offline)
  offlineWindowMs: { default: 90_000 }, // aligns with BE stalenessThresholdSeconds
  rePairEnabled: { default: true },
} as const;

// ---------------------------------------------------------------------------
// deriveState — server-relative freshness computation (SPEC-03 §2, ADR-005)
// Never uses Date.now() directly — uses serverTime from poll response to
// avoid client clock skew (doc 06 R5).
// ---------------------------------------------------------------------------

export function deriveState(
  device: DispositivoEdgeView | null,
  pollOk: boolean,
): DerivedState {
  if (!device) return 'not-paired';
  const { pairingState, ultimaSincronizacao, serverTime, status } = device;

  if (pairingState === 'unpaired' || pairingState === 'retired' || status === 'n/a') {
    return 'not-paired';
  }
  if (!pollOk) return 'unknown'; // INVARIANT: unknown ≠ online on poll failure

  if (!ultimaSincronizacao) return 'offline'; // paired but never reported (loud)

  // Server-relative age: authoritative (avoids client clock skew) — doc 06 R5.
  // G5: guard against absent/invalid serverTime (BE may omit field under load).
  // new Date(null) → epoch (0ms) → negative ageMs → false-green 'online' (bug).
  // Use serverTime only when truthy; otherwise fall back to 'unknown' (honest degradation).
  const serverMs = serverTime ? new Date(serverTime).getTime() : NaN;
  const syncMs = new Date(ultimaSincronizacao).getTime();
  // NaN arithmetic: if either timestamp is invalid, ageMs is NaN → 'unknown' fallback.
  if (isNaN(serverMs) || isNaN(syncMs)) return 'unknown'; // G5: safe fallback

  const ageMs = serverMs - syncMs;

  const stalenessMs = MODULE_FEATURE_FLAGS.stalenessThresholdMs.default;
  const offlineMs = MODULE_FEATURE_FLAGS.offlineWindowMs.default;

  if (ageMs < stalenessMs) return 'online';
  if (ageMs < offlineMs) return 'stale';
  return 'offline';
}

function toTone(state: DerivedState): ConnectivityTone {
  switch (state) {
    case 'online': return 'ok';
    case 'stale': return 'warn';
    case 'offline': return 'er';
    default: return 'muted'; // unknown | not-paired
  }
}

function toLabel(state: DerivedState, versao: string | null): string {
  switch (state) {
    case 'online': return versao ? `Dispositivo online · ${versao}` : 'Dispositivo online';
    case 'stale': return 'Sinal fraco — verificando…';
    case 'offline': return 'Dispositivo offline';
    case 'unknown': return 'Conectividade desconhecida';
    case 'not-paired': return 'Sem dispositivo';
  }
}

function relativeTime(isoStr: string | null): string {
  if (!isoStr) return '—';
  const diffMs = Date.now() - new Date(isoStr).getTime();
  const diffMin = Math.floor(diffMs / 60_000);
  if (diffMin < 1) return 'há menos de 1 min';
  if (diffMin === 1) return 'há 1 min';
  if (diffMin < 60) return `há ${diffMin} min`;
  const diffHr = Math.floor(diffMin / 60);
  return diffHr === 1 ? 'há 1 hora' : `há ${diffHr} horas`;
}

// ---------------------------------------------------------------------------
// DeviceFacade — SPEC-04 §3
// Owns connectivity + detail + the poll timer.
// Signals-first: components read .asReadonly() signals, never mutate state.
// ---------------------------------------------------------------------------

@Injectable({ providedIn: 'root' })
export class DeviceFacade {
  private readonly port = inject(DEVICE_PORT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly toast = inject(EDGE_TOAST_TOKEN, { optional: true });
  private readonly observability = inject(ObservabilityService);

  private readonly _device = signal<DispositivoEdgeView | null>(null);
  private readonly _pollOk = signal<boolean>(true);
  private readonly _loading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);
  /** Derive conductor entitlement — true until session integration replaces this (FE-EDGE-003 TODO). */
  private readonly _canManage = signal<boolean>(true);

  private pollSub: Subscription | null = null;
  private lastEtag: string | undefined;

  // --- Public read-only signals ---

  readonly device = this._device.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();
  /** Conductor entitlement gate — accessible even when no device is paired (FE-EDGE-018). */
  readonly canManage = this._canManage.asReadonly();

  /** DerivedState computed from device + poll health (ADR-005). */
  readonly derivedState = computed<DerivedState>(() =>
    deriveState(this._device(), this._pollOk()),
  );

  /** DeviceConnectivityVM — the read-model exposed to the topbar chip / Home. */
  readonly connectivity = computed<DeviceConnectivityVM>(() => {
    const dev = this._device();
    const state = this.derivedState();
    return {
      paired: dev?.pairingState === 'paired',
      derivedState: state,
      lastSyncIso: dev?.ultimaSincronizacao ?? null,
      lastSyncRelative: relativeTime(dev?.ultimaSincronizacao ?? null),
      versao: dev?.versao ?? null,
      tone: toTone(state),
      label: toLabel(state, dev?.versao ?? null),
    };
  });

  /** DeviceDetailVM — full device detail for the management page. */
  readonly detail = computed<DeviceDetailVM | null>(() => {
    const dev = this._device();
    if (!dev) return null;
    return {
      device: dev,
      derivedState: this.derivedState(),
      lastSyncRelative: relativeTime(dev.ultimaSincronizacao),
      profileDrift: dev.profileDrift,
      canManage: this._canManage(), // TODO[FE-EDGE-003]: derive from session entitlement via edgeConductorGuard
    };
  });

  // ---------------------------------------------------------------------------
  // Commands
  // ---------------------------------------------------------------------------

  /** Load device by explicit id (from URL param or chip navigation). */
  load(deviceId: string): void {
    this._loading.set(true);
    this._error.set(null);
    this.port
      .getDevice(deviceId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (dev) => {
          this._device.set(dev);
          this._pollOk.set(true);
          this._loading.set(false);
        },
        error: (err: unknown) => {
          this._error.set(String(err));
          this._pollOk.set(false);
          this._loading.set(false);
        },
      });
  }

  /** Resolve and load the active patient's device. */
  resolveForActivePatient(pacienteId: string): void {
    this._loading.set(true);
    this._error.set(null);
    this.port
      .resolveDeviceForPatient(pacienteId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (dev) => {
          this._device.set(dev);
          this._pollOk.set(true);
          this._loading.set(false);
        },
        error: (err: unknown) => {
          this._error.set(String(err));
          this._pollOk.set(false);
          this._loading.set(false);
        },
      });
  }

  /**
   * Start the liveness poll timer (SPEC-04 §3).
   * Pauses when the browser tab is hidden; resumes on focus.
   * Uses RxJS timer + switchMap — polling is the one place RxJS earns its keep.
   */
  startPolling(): void {
    if (this.pollSub) return; // idempotent

    const interval = MODULE_FEATURE_FLAGS.pollIntervalMs.default;

    this.pollSub = timer(0, interval)
      .pipe(
        switchMap(() => {
          const dev = this._device();
          if (!dev) return of(null);
          if (document.hidden) return of(null); // Page Visibility — skip when hidden

          const pacienteId = dev.pacienteId;
          if (!pacienteId) return of(null);

          return this.port.pollPatientLiveness(pacienteId, this.lastEtag).pipe(
            catchError(() => {
              this._pollOk.set(false); // poll failure → unknown (SPEC-04 §3)
              // Telemetry: emit poll failure event (FE-EDGE-020)
              this.observability.emit({
                event: 'edge.device.poll_failure',
                moduleKey: 'edge',
                deviceId: this._device()?.deviceId,
              });
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        if (!result) return;
        if ('notModified' in result && result.notModified) {
          // 304 Not Modified: keep snapshot, still tick poll health (SPEC-04 §3)
          this._pollOk.set(true);
          return;
        }
        const liveness = result as LivenessView;
        // Capture previous derived state BEFORE mutations for offline transition detection (FE-EDGE-020)
        const prevState = this.derivedState();
        this._pollOk.set(true);
        // Update device snapshot from liveness (merged fields only)
        this._device.update((prev) =>
          prev
            ? {
                ...prev,
                status: liveness.status,
                ultimaSincronizacao: liveness.ultimaSincronizacao,
                versao: liveness.versao ?? prev.versao,
                stalenessThresholdSeconds: liveness.stalenessThresholdSeconds,
                serverTime: liveness.serverTime,
              }
            : prev,
        );
        // Telemetry: detect transition to offline (FE-EDGE-020)
        if (prevState !== 'offline' && this.derivedState() === 'offline') {
          this.observability.emit({
            event: 'edge.device.offline',
            moduleKey: 'edge',
            deviceId: this._device()?.deviceId,
            derivedState: 'offline',
          });
        }
      });
  }

  /** Stop the poll timer (called on ngOnDestroy / module unload). */
  stopPolling(): void {
    this.pollSub?.unsubscribe();
    this.pollSub = null;
  }

  /** Unpair the current device (conductor action — SPEC-04 §3). G6: 2 auto-retries then toast. */
  unpair(reason: string): void {
    const dev = this._device();
    if (!dev) return;
    // defer() ensures each retry invokes the port factory again (new HTTP request).
    defer(() => this.port.unpair(dev.deviceId, { reason }))
      .pipe(
        retry({ count: 2 }), // 3 total attempts (FE-EDGE-019 G6)
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.load(dev.deviceId), // refresh — no optimistic flip (SPEC-04 §3)
        error: (err: unknown) => {
          this._error.set(String(err));
          this.toast?.show(EDGE_RETRY_TOAST_MESSAGE, {
            label: 'Tentar novamente',
            callback: () => this.unpair(reason),
          });
        },
      });
  }

  /** Retire the current device (conductor action — SPEC-04 §3). G6: 2 auto-retries then toast. */
  retire(): void {
    const dev = this._device();
    if (!dev) return;
    // defer() ensures each retry invokes the port factory again (new HTTP request).
    defer(() => this.port.retire(dev.deviceId))
      .pipe(
        retry({ count: 2 }), // 3 total attempts (FE-EDGE-019 G6)
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (updated) => {
          this._device.set(updated);
          // Telemetry: device successfully retired (FE-EDGE-020)
          this.observability.emit({
            event: 'edge.device.retired',
            moduleKey: 'edge',
            deviceId: updated.deviceId,
          });
        },
        error: (err: unknown) => {
          this._error.set(String(err));
          this.toast?.show(EDGE_RETRY_TOAST_MESSAGE, {
            label: 'Tentar novamente',
            callback: () => this.retire(),
          });
        },
      });
  }
}
