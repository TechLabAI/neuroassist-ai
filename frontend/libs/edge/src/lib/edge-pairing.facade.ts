import {
  Injectable,
  inject,
  signal,
  computed,
  DestroyRef,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer, Subscription, switchMap, of, catchError, retry, defer } from 'rxjs';
import { PAIRING_PORT } from './api/pairing.port';
import { DEVICE_PORT } from './api/device.port';
import { EDGE_TOAST_TOKEN, EDGE_RETRY_TOAST_MESSAGE } from './edge-toast.token';
import {
  PairingVM,
  PairingPhase,
  DerivedState,
  PairedDeviceOutcome,
  DispositivoEdgeView,
} from '@neuroassist/shared/contracts';
import { MODULE_FEATURE_FLAGS } from './edge.facade';

// ---------------------------------------------------------------------------
// PairingFacade — SPEC-04 §2
// Owns the pairing flow state machine.
// Signals-first; components read .asReadonly() signals, never mutate state.
// ---------------------------------------------------------------------------

function idleVm(): PairingVM {
  return {
    phase: 'idle',
    deviceId: null,
    enrollmentToken: null,
    enrollmentExpiresAtIso: null,
    pacienteId: null,
    versao: null,
    derivedState: 'not-paired',
    errorMessage: null,
  };
}

function secondsUntil(isoStr: string | null): number {
  if (!isoStr) return 0;
  return Math.max(0, Math.floor((new Date(isoStr).getTime() - Date.now()) / 1000));
}

function deriveFromDevice(dev: DispositivoEdgeView): DerivedState {
  if (!dev.ultimaSincronizacao) return 'offline';
  const ageMs =
    new Date(dev.serverTime).getTime() -
    new Date(dev.ultimaSincronizacao).getTime();
  const stalenessMs = MODULE_FEATURE_FLAGS.stalenessThresholdMs.default;
  if (ageMs < stalenessMs) return 'online';
  return 'stale';
}

@Injectable({ providedIn: 'root' })
export class PairingFacade {
  private readonly pairingPort = inject(PAIRING_PORT);
  private readonly devicePort = inject(DEVICE_PORT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly toast = inject(EDGE_TOAST_TOKEN, { optional: true });

  private readonly _vm = signal<PairingVM>(idleVm());

  /** Read-only pairing state (SPEC-03 §4). */
  readonly state = this._vm.asReadonly();
  readonly phase = computed<PairingPhase>(() => this._vm().phase);
  readonly countdown = computed<number>(() =>
    secondsUntil(this._vm().enrollmentExpiresAtIso),
  );

  /** Emitted when pairing completes with real first heartbeat (SPEC-03 §6). */
  private readonly _lastOutcome = signal<PairedDeviceOutcome | null>(null);
  readonly lastOutcome = this._lastOutcome.asReadonly();

  private claimPollSub: Subscription | null = null;
  private ttlCheckSub: Subscription | null = null;

  // ---------------------------------------------------------------------------
  // Public commands
  // ---------------------------------------------------------------------------

  /**
   * POST /devices → mint enrollment token.
   * Sets phase=awaiting-claim and starts polling for the agent claim
   * (SPEC-04 §2 — claim detection via liveness poll).
   */
  register(label: string, pacienteHint?: string): void {
    this._vm.update((vm) => ({ ...vm, phase: 'registering', errorMessage: null }));

    // defer() ensures each retry invokes the port factory again (new HTTP request).
    defer(() => this.pairingPort.register({ label, pacienteHint }))
      .pipe(
        retry({ count: 2 }), // 3 total attempts (FE-EDGE-019 G6)
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (result) => {
          this._vm.set({
            phase: 'awaiting-claim',
            deviceId: result.deviceId,
            enrollmentToken: result.enrollmentToken,
            enrollmentExpiresAtIso: result.enrollmentExpiresAt,
            pacienteId: pacienteHint ?? null,
            versao: null,
            derivedState: 'not-paired',
            errorMessage: null,
          });
          this.startClaimPoll(result.deviceId);
          this.startTtlWatch(result.enrollmentExpiresAt);
        },
        error: (_err: unknown) => {
          this._vm.update((vm) => ({
            ...vm,
            phase: 'error',
            errorMessage: 'Não foi possível registrar o dispositivo. Tente novamente.',
          }));
          this.toast?.show(EDGE_RETRY_TOAST_MESSAGE, {
            label: 'Tentar novamente',
            callback: () => this.register(label, pacienteHint),
          });
        },
      });
  }

  /**
   * POST /devices/{id}/pairings → bind to patient.
   * Called after agent claimed the token. Advances to awaiting-first-heartbeat.
   */
  pairToPatient(pacienteId: string): void {
    const deviceId = this._vm().deviceId;
    if (!deviceId) return;

    this._vm.update((vm) => ({
      ...vm,
      phase: 'awaiting-first-heartbeat',
      pacienteId,
    }));

    // defer() ensures each retry invokes the port factory again (new HTTP request).
    defer(() => this.pairingPort.pairToPatient(deviceId, { pacienteId }))
      .pipe(
        retry({ count: 2 }), // 3 total attempts (FE-EDGE-019 G6)
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dev) => this.handleFirstHeartbeat(dev),
        error: (_err: unknown) => {
          this._vm.update((vm) => ({
            ...vm,
            phase: 'error',
            errorMessage: 'Falha ao parear com o paciente. Tente novamente.',
          }));
          this.toast?.show(EDGE_RETRY_TOAST_MESSAGE, {
            label: 'Tentar novamente',
            callback: () => this.pairToPatient(pacienteId),
          });
        },
      });
  }

  /** Re-issue the last failed step. */
  retry(): void {
    const { deviceId, pacienteId } = this._vm();
    if (!deviceId) {
      this._vm.set(idleVm());
      return;
    }
    if (pacienteId) {
      this.pairToPatient(pacienteId);
    } else {
      this._vm.set(idleVm());
    }
  }

  /** Discard token + stop polling → idle. */
  reset(): void {
    this.stopPolling();
    this._vm.set(idleVm());
  }

  /** Teardown (ngOnDestroy). */
  stopPolling(): void {
    this.claimPollSub?.unsubscribe();
    this.claimPollSub = null;
    this.ttlCheckSub?.unsubscribe();
    this.ttlCheckSub = null;
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  /**
   * Poll the device liveness every 5 s while awaiting-claim.
   * A first non-null ultimaSincronizacao that is fresh means agent enrolled →
   * advance to awaiting-first-heartbeat and then call pairToPatient.
   * (SPEC-04 §2 — claim detection; never flips to paired on enrollment response alone.)
   */
  private startClaimPoll(deviceId: string): void {
    this.claimPollSub?.unsubscribe();

    this.claimPollSub = timer(2000, 5000)
      .pipe(
        switchMap(() => {
          const phase = this._vm().phase;
          if (phase !== 'awaiting-claim' && phase !== 'awaiting-first-heartbeat') {
            return of(null);
          }
          return this.devicePort.getDevice(deviceId).pipe(
            catchError(() => of(null)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((dev) => {
        if (!dev) return;
        const phase = this._vm().phase;
        if (phase === 'awaiting-claim' && dev.ultimaSincronizacao) {
          // Agent has claimed and reported first heartbeat
          const pacienteId = this._vm().pacienteId;
          if (pacienteId) {
            this.pairToPatient(pacienteId);
          } else {
            this.handleFirstHeartbeat(dev);
          }
        } else if (phase === 'awaiting-first-heartbeat') {
          this.handleFirstHeartbeat(dev);
        }
      });
  }

  private handleFirstHeartbeat(dev: DispositivoEdgeView): void {
    if (!dev.ultimaSincronizacao) return; // not yet reporting — keep waiting

    const derived = deriveFromDevice(dev);
    if (derived !== 'online') return; // not fresh enough yet

    this.stopPolling();
    this._vm.update((vm) => ({
      ...vm,
      phase: 'paired',
      versao: dev.versao,
      derivedState: 'online',
      errorMessage: null,
    }));

    if (dev.pacienteId) {
      this._lastOutcome.set({
        deviceId: dev.deviceId,
        pacienteId: dev.pacienteId,
        versao: dev.versao,
        derivedState: 'online',
      });
    }
  }

  /**
   * Watch the enrollment token TTL.
   * When the token expires and we are still in awaiting-claim → phase='expired'.
   */
  private startTtlWatch(expiresAt: string): void {
    this.ttlCheckSub?.unsubscribe();
    const msUntilExpiry = new Date(expiresAt).getTime() - Date.now();
    if (msUntilExpiry <= 0) {
      this.onTokenExpired();
      return;
    }
    // Check every second; expire on TTL boundary (api-contract §2.1)
    this.ttlCheckSub = timer(msUntilExpiry)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.onTokenExpired());
  }

  private onTokenExpired(): void {
    const phase = this._vm().phase;
    if (phase === 'awaiting-claim') {
      this.stopPolling();
      this._vm.update((vm) => ({
        ...vm,
        phase: 'expired',
        errorMessage: 'O código expirou. Solicite um novo código para continuar.',
      }));
    }
  }
}
