import {
  Injectable,
  inject,
  signal,
  computed,
  DestroyRef,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  timer,
  Subscription,
  switchMap,
  of,
  catchError,
  retry,
  defer,
  throwError,
  Observable,
} from 'rxjs';
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
import { edgeErrorMessage, isEdgeTransient, deveAvisarPorToast } from './edge-error-messages';
import { ObservabilityService } from '@neuroassist/shared/observability';

// ---------------------------------------------------------------------------
// PairingFacade — SPEC-04 §2
// Owns the pairing flow state machine.
// Signals-first; components read .asReadonly() signals, never mutate state.
// ---------------------------------------------------------------------------

/**
 * Delay de `retry` que só deixa repetir falha transiente (ver DeviceFacade).
 * Um 400 de validação repetido três vezes continua sendo um 400 de validação.
 */
function throwIfNotTransient(err: unknown): Observable<unknown> {
  // `of` e não `timer(0)`: o retry tem de continuar SÍNCRONO. Qualquer
  // agendamento aqui adia a transição para a fase de erro em um tique, e a tela
  // fica um instante mostrando 'registrando…' sobre uma operação já perdida.
  if (isEdgeTransient(err)) return of(err);
  return throwError(() => err);
}

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
  private readonly observability = inject(ObservabilityService);

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

  /** Rótulo do último `register()` — necessário para reemitir um código expirado. */
  private lastLabel = 'Dispositivo NeuroAssist';

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
    this.lastLabel = label;
    this._vm.update((vm) => ({ ...vm, phase: 'registering', errorMessage: null }));

    // defer() ensures each retry invokes the port factory again (new HTTP request).
    defer(() => this.pairingPort.register({ label, pacienteHint }))
      .pipe(
        retry({ count: 2, delay: (err) => throwIfNotTransient(err) }), // G6
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
        error: (err: unknown) => {
          // A cópia genérica escondia a causa: um 400 `/errors/validation` e uma
          // queda de rede produziam a MESMA frase, e a única sugestão possível
          // era "tente novamente" — que para um erro de validação nunca resolve.
          // A tabela de cópia do SPEC-07 já tinha a frase certa para cada tipo;
          // faltava alguém consultá-la.
          this._vm.update((vm) => ({
            ...vm,
            phase: 'error',
            errorMessage: edgeErrorMessage(
              err,
              'Não foi possível registrar o dispositivo. Tente novamente.',
            ),
          }));
          // A MENSAGEM tem de ser a do erro, e a AÇÃO só existe se repetir puder
          // adiantar. Antes, todo erro produzia "Falha temporária de conexão.
          // Tente novamente." com botão de repetir — inclusive um 403, que não é
          // temporário e não melhora com insistência. O aviso mentia sobre a
          // causa e oferecia uma saída que não existe; quem perdeu permissão
          // ficava clicando.
          if (isEdgeTransient(err)) {
            this.toast?.show(EDGE_RETRY_TOAST_MESSAGE, {
              label: 'Tentar novamente',
              callback: () => this.register(label, pacienteHint),
            });
          } else if (deveAvisarPorToast(err)) {
            this.toast?.show(edgeErrorMessage(err));
          }
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
        retry({ count: 2, delay: (err) => throwIfNotTransient(err) }), // G6
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dev) => this.handleFirstHeartbeat(dev),
        error: (err: unknown) => {
          this._vm.update((vm) => ({
            ...vm,
            phase: 'error',
            errorMessage: edgeErrorMessage(
              err,
              'Falha ao parear com o paciente. Tente novamente.',
            ),
          }));
          // A MENSAGEM tem de ser a do erro, e a AÇÃO só existe se repetir puder
          // adiantar. Antes, todo erro produzia "Falha temporária de conexão.
          // Tente novamente." com botão de repetir — inclusive um 403, que não é
          // temporário e não melhora com insistência. O aviso mentia sobre a
          // causa e oferecia uma saída que não existe; quem perdeu permissão
          // ficava clicando.
          if (isEdgeTransient(err)) {
            this.toast?.show(EDGE_RETRY_TOAST_MESSAGE, {
              label: 'Tentar novamente',
              callback: () => this.pairToPatient(pacienteId),
            });
          } else if (deveAvisarPorToast(err)) {
            this.toast?.show(edgeErrorMessage(err));
          }
        },
      });
  }

  /** Re-issue the last failed step. */
  retry(): void {
    const { deviceId, pacienteId, phase } = this._vm();

    // Código expirado: o botão diz "Novo código", então tem de SAIR um código
    // novo. Antes isto apenas voltava a fase para 'idle' e a pessoa tinha de
    // descobrir sozinha que precisava clicar "Solicitar código" outra vez — o
    // rótulo prometia uma ação que a tela não executava. Um token expirado não
    // se recupera: só se substitui.
    if (phase === 'expired') {
      this.stopPolling();
      this.register(this.lastLabel, pacienteId ?? undefined);
      return;
    }

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

    // Telemetry: pairing completed with confirmed first heartbeat (FE-EDGE-020)
    this.observability.emit({
      event: 'edge.device.paired',
      moduleKey: 'edge',
      deviceId: dev.deviceId,
      derivedState: 'online',
    });

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
