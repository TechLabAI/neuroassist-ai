import {
  Component,
  ChangeDetectionStrategy,
  inject,
  OnDestroy,
  input,
  output,
  computed,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { PairingFacade } from '../edge-pairing.facade';
import { PairingPhase, PairedDeviceOutcome } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// DevicePairingPageComponent — SPEC-01 §2 / SPEC-06 §5
// Owns the pairing wizard: idle → registering → awaiting-claim → paired.
// Used in two contexts:
//   1. Own route /edge/pairing (standalone host; conductor guard)
//   2. Composed inside onboarding step-3 slot (via EdgeShellIntegrationService)
//
// OnPush; Standalone; OnDestroy cleans up polling.
// TODO[FE-EDGE-006..012]: implement full pairing flow UI (Layer 2)
// ---------------------------------------------------------------------------

function phaseLabel(phase: PairingPhase): string {
  switch (phase) {
    case 'idle': return 'Aguardando início';
    case 'registering': return 'Solicitando código…';
    case 'awaiting-claim': return 'Aguardando o agente reclamar o código…';
    case 'awaiting-first-heartbeat': return 'Aguardando primeiro heartbeat…';
    case 'paired': return 'Dispositivo pareado!';
    case 'expired': return 'Código expirado';
    case 'error': return 'Erro no pareamento';
  }
}

@Component({
  selector: 'edge-device-pairing',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-lg p-8">
      <header class="mb-6">
        <h1 class="font-display text-xl font-bold tracking-tight text-ink">Parear dispositivo</h1>
        <p class="mt-1 text-sm text-ink-soft">
          Conecte o agente edge ao paciente. O código expira em {{ countdown() }}s.
        </p>
      </header>

      <!-- State machine display -->
      <div class="rounded-2xl border border-line bg-surface p-6 shadow-card">
        @switch (facade.phase()) {

          @case ('idle') {
            <div class="flex flex-col items-center gap-4 py-4 text-center">
              <span class="material-symbols-outlined text-[40px] text-ink-faint">link</span>
              <p class="text-sm text-ink-soft">
                Clique em "Solicitar código" para iniciar o pareamento.
                O código de uso único é apresentado ao agente edge.
              </p>
              <button
                (click)="onRegister()"
                class="mt-2 inline-flex items-center gap-2 rounded-xl bg-brand px-6 py-3 text-sm font-bold text-brand-ink shadow-card transition-colors hover:bg-brand-strong">
                <span class="material-symbols-outlined text-[18px]">add_link</span>
                Solicitar código
              </button>
            </div>
          }

          @case ('registering') {
            <div class="flex items-center gap-3 text-sm text-brand">
              <span class="material-symbols-outlined animate-spin text-[20px]">progress_activity</span>
              Solicitando código ao edge-service…
            </div>
          }

          @case ('awaiting-claim') {
            <div class="flex flex-col gap-4">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium text-ink">Código de pareamento</span>
                <span class="text-xs text-ink-faint">Expira em {{ countdown() }}s</span>
              </div>
              <!-- Enrollment token — shown once (api-contract §2.1) -->
              <div class="rounded-xl border border-brand bg-brand-subtle px-5 py-4 text-center">
                <p class="font-mono text-2xl font-bold tracking-widest text-brand">
                  {{ facade.state().enrollmentToken ?? '—' }}
                </p>
                <p class="mt-2 text-xs text-ink-soft">
                  Informe este código ao agente edge para concluir o registro.
                </p>
              </div>
              <div class="flex items-center gap-2 text-sm text-ink-soft">
                <span class="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
                Aguardando o agente reclamar o código…
              </div>
            </div>
          }

          @case ('awaiting-first-heartbeat') {
            <div class="flex flex-col gap-3">
              <div class="flex items-center gap-2 text-sm text-brand">
                <span class="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
                Aguardando primeiro heartbeat do agente…
              </div>
              <p class="text-xs text-ink-soft">
                O agente iniciou e está enviando o primeiro heartbeat.
                Isso pode levar alguns segundos.
              </p>
            </div>
          }

          @case ('paired') {
            <div class="flex flex-col items-center gap-4 py-4 text-center">
              <span class="material-symbols-outlined text-[48px] text-success">check_circle</span>
              <div>
                <p class="font-display text-base font-bold text-ink">Dispositivo pareado!</p>
                @if (facade.state().versao; as versao) {
                  <p class="mt-1 text-xs text-success-ink">Online · versão {{ versao }}</p>
                }
              </div>
              <a routerLink="/edge/device"
                 class="mt-2 inline-flex items-center gap-2 rounded-xl bg-success px-5 py-2 text-sm font-bold text-success-ink">
                Ver dispositivo
              </a>
            </div>
          }

          @case ('expired') {
            <div class="flex flex-col items-center gap-4 py-4 text-center">
              <span class="material-symbols-outlined text-[40px] text-warn">timer_off</span>
              <div>
                <p class="text-sm font-medium text-ink">Código expirado</p>
                <p class="mt-1 text-xs text-ink-soft">
                  {{ facade.state().errorMessage }}
                </p>
              </div>
              <button (click)="onRetry()"
                      class="rounded-xl bg-brand px-5 py-2.5 text-sm font-bold text-brand-ink hover:bg-brand-strong">
                Novo código
              </button>
            </div>
          }

          @case ('error') {
            <div class="flex flex-col items-center gap-4 py-4 text-center">
              <span class="material-symbols-outlined text-[40px] text-danger">error</span>
              <div>
                <p class="text-sm font-medium text-ink">Erro no pareamento</p>
                <p class="mt-1 text-xs text-ink-soft">{{ facade.state().errorMessage }}</p>
              </div>
              <button (click)="onRetry()"
                      class="rounded-xl bg-brand px-5 py-2.5 text-sm font-bold text-brand-ink hover:bg-brand-strong">
                Tentar novamente
              </button>
            </div>
          }

        }
      </div>

      <!-- Phase breadcrumb -->
      <p class="mt-3 text-xs text-ink-faint">
        Estado: <span class="font-medium text-ink-soft">{{ phaseLabel(facade.phase()) }}</span>
      </p>
    </div>
  `,
})
export class DevicePairingPageComponent implements OnDestroy {
  protected readonly facade = inject(PairingFacade);

  /** Optional paciente hint from onboarding step-3 host (SPEC-01 §4). */
  readonly pacienteHint = input<string | null>(null);

  /** Emitted to host page when pairing completes (SPEC-03 §6). */
  readonly paired = output<PairedDeviceOutcome>();

  protected readonly countdown = computed(() => this.facade.countdown());
  protected readonly phaseLabel = phaseLabel;

  protected onRegister(): void {
    const hint = this.pacienteHint();
    this.facade.register(
      'Dispositivo NeuroAssist',
      hint ?? undefined,
    );
  }

  protected onRetry(): void {
    this.facade.retry();
  }

  ngOnDestroy(): void {
    this.facade.stopPolling();
    // Emit outcome if pairing completed before the host unmounts
    const outcome = this.facade.lastOutcome();
    if (outcome) {
      this.paired.emit(outcome);
    }
  }
}
