import {
  Component,
  ChangeDetectionStrategy,
  inject,
  Input,
  OnDestroy,
  output,
  computed,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { PairingFacade } from '../edge-pairing.facade';
import { PairedDeviceOutcome } from '@neuroassist/shared/contracts';
import { PairingCodeCardComponent } from './pairing-code-card.component';
import { PairingProgressComponent } from './pairing-progress.component';
import { PairingResultComponent } from './pairing-result.component';

// ---------------------------------------------------------------------------
// DevicePairingPageComponent — FE-EDGE-011
// Smart container: owns the pairing wizard state machine via PairingFacade.
// Composes:
//   - PairingProgressComponent  — step indicator
//   - PairingCodeCardComponent  — token display + TTL countdown (awaiting-claim)
//   - PairingResultComponent    — paired / expired / error outcomes
//
// Used in two contexts:
//   1. Own route /edge/pairing (standalone host; conductor guard)
//   2. Composed inside onboarding step-3 slot (via EdgeShellIntegrationService)
//
// OnPush; Standalone; OnDestroy cleans up polling.
// Safety: NEVER transitions to "paired" on register() alone (SPEC-04 §2).
// ---------------------------------------------------------------------------

@Component({
  selector: 'edge-device-pairing',
  standalone: true,
  imports: [RouterLink, PairingCodeCardComponent, PairingProgressComponent, PairingResultComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-lg p-8">
      <header class="mb-6">
        <!--
          O <h1> só existe quando este componente É a página (rota /edge/pareamento).
          Composto dentro do assistente de configuração, ele fica DENTRO de uma seção
          já encabeçada por <h2>Dispositivo edge</h2> — e um <h1> ali produzia duas
          coisas ruins: uma página com dois títulos de nível 1, e uma hierarquia de
          títulos invertida para quem navega por cabeçalho no leitor de tela.
        -->
        @if (!_composto()) {
          <h1 class="font-display text-xl font-bold tracking-tight text-ink">Parear dispositivo</h1>
        }
        <p class="mt-1 text-sm text-ink-soft">
          Conecte o agente edge ao paciente.
        </p>
      </header>

      <!-- Step progress bar (always visible during active pairing) -->
      @if (showProgress()) {
        <div class="mb-5">
          <edge-pairing-progress [phase]="facade.phase()" />
        </div>
      }

      <!-- Main card -->
      <div class="rounded-2xl border border-line bg-surface p-6 shadow-card">

        @switch (facade.phase()) {

          @case ('idle') {
            <div class="flex flex-col items-center gap-4 py-4 text-center">
              <span aria-hidden="true" class="material-symbols-outlined text-[40px] text-ink-faint">link</span>
              <p class="text-sm text-ink-soft">
                Clique em "Solicitar código" para iniciar o pareamento.
                O código de uso único é apresentado ao agente edge.
              </p>
              <!--
                aria-hidden no ícone: a ligatura "add_link" é o texto do nó e
                entrava no nome acessível do botão ("add_link Solicitar código").
                O ícone é decorativo — o rótulo ao lado já diz o que o botão faz.
              -->
              <button
                (click)="onRegister()"
                class="mt-2 inline-flex items-center gap-2 rounded-xl bg-brand px-6 py-3 text-sm font-bold text-brand-ink shadow-card transition-colors hover:bg-brand-strong">
                <span aria-hidden="true" class="material-symbols-outlined text-[18px]">add_link</span>
                Solicitar código
              </button>
            </div>
          }

          @case ('registering') {
            <div class="flex items-center gap-3 text-sm text-brand">
              <span aria-hidden="true" class="material-symbols-outlined animate-spin text-[20px]">progress_activity</span>
              Solicitando código ao edge-service…
            </div>
          }

          @case ('awaiting-claim') {
            <div class="flex flex-col gap-4">
              <!-- Presentational token card (FE-EDGE-009) -->
              <edge-pairing-code-card
                [token]="facade.state().enrollmentToken ?? ''"
                [countdown]="facade.countdown()"
                (expired)="onCodeExpiredInUi()"
                (copied)="onCodeCopied()" />

              <div class="flex items-center gap-2 text-sm text-ink-soft">
                <span aria-hidden="true" class="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
                Aguardando o agente reclamar o código…
              </div>
            </div>
          }

          @case ('awaiting-first-heartbeat') {
            <div class="flex flex-col gap-3">
              <div class="flex items-center gap-2 text-sm text-brand">
                <span aria-hidden="true" class="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
                Aguardando primeiro heartbeat do agente…
              </div>
              <p class="text-xs text-ink-soft">
                O agente iniciou e está enviando o primeiro heartbeat.
                Isso pode levar alguns segundos.
              </p>
            </div>
          }

          @default {
            <!-- Outcome card for paired / expired / error (FE-EDGE-010) -->
            <edge-pairing-result
              [phase]="facade.phase()"
              [versao]="facade.state().versao"
              [errorMessage]="facade.state().errorMessage"
              (retry)="onRetry()" />
          }

        }
      </div>

      <!-- Phase label -->
      <p class="mt-3 text-xs text-ink-faint">
        Estado: <span class="font-medium text-ink-soft">{{ phaseLabel(facade.phase()) }}</span>
      </p>
    </div>
  `,
})
export class DevicePairingPageComponent implements OnDestroy {
  protected readonly facade = inject(PairingFacade);

  /**
   * Optional paciente hint from onboarding step-3 host (SPEC-01 §4).
   * NOTE: @Input() is used instead of the input() signal API (ts-jest limitation).
   */
  @Input() set pacienteHint(value: string | null) {
    this._pacienteHint.set(value ?? null);
  }
  protected readonly _pacienteHint = signal<string | null>(null);

  /**
   * Este componente está COMPOSTO dentro de outra página (o assistente de
   * configuração inicial) em vez de ser a própria página?
   *
   * <p>Quem compõe é quem sabe — por isso é entrada, e não uma adivinhação a
   * partir da rota. Hoje muda só o título; é o ponto onde qualquer outra decisão
   * de "sou página" vs. "sou pedaço" deve entrar.
   *
   * <p>{@code @Input()} e não {@code input()} pelo mesmo motivo do
   * {@code pacienteHint} acima (limitação do ts-jest).
   */
  @Input() set composto(value: boolean) {
    this._composto.set(value);
  }
  protected readonly _composto = signal(false);

  /** Emitted to host page when pairing completes (SPEC-03 §6). */
  readonly paired = output<PairedDeviceOutcome>();

  protected readonly countdown = computed(() => this.facade.countdown());

  protected readonly showProgress = computed(() => {
    const p = this.facade.phase();
    return p !== 'idle' && p !== 'paired' && p !== 'expired' && p !== 'error';
  });

  protected onRegister(): void {
    const hint = this._pacienteHint();
    this.facade.register(
      'Dispositivo NeuroAssist',
      hint ?? undefined,
    );
  }

  protected onRetry(): void {
    this.facade.retry();
  }

  protected onCodeExpiredInUi(): void {
    // UI notification — the facade already handles the state transition via
    // startTtlWatch (SPEC-04 §2). No additional action needed from the component.
  }

  protected onCodeCopied(): void {
    // No-op for now — could emit a toast in P8 observability wave.
  }

  protected phaseLabel(phase: string): string {
    const labels: Record<string, string> = {
      'idle': 'Aguardando início',
      'registering': 'Solicitando código…',
      'awaiting-claim': 'Aguardando o agente reclamar o código…',
      'awaiting-first-heartbeat': 'Aguardando primeiro heartbeat…',
      'paired': 'Dispositivo pareado!',
      'expired': 'Código expirado',
      'error': 'Erro no pareamento',
    };
    return labels[phase] ?? phase;
  }

  ngOnDestroy(): void {
    this.facade.stopPolling();
    const outcome = this.facade.lastOutcome();
    if (outcome) {
      this.paired.emit(outcome);
    }
  }
}
