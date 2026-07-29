import {
  Component,
  ChangeDetectionStrategy,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DeviceFacade } from '../edge.facade';
import { DerivedState } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// DeviceDetailPageComponent — SPEC-01 §2, route: /edge/device[/:deviceId]
// Read + management surface (read=all; manage=conductor only via canManage).
// OnPush; Standalone; signals-first.
//
// TODO[FE-EDGE-014]: implement full device detail view (Layer 2)
// TODO[FE-EDGE-015]: re-pair action flow
// TODO[FE-EDGE-016]: unpair + retire confirmation dialogs
// ---------------------------------------------------------------------------

function stateLabel(state: DerivedState): string {
  switch (state) {
    case 'online': return 'Online';
    case 'stale': return 'Sinal fraco';
    case 'offline': return 'Offline';
    case 'unknown': return 'Desconhecido';
    case 'not-paired': return 'Não pareado';
  }
}

function stateBadgeClass(state: DerivedState): string {
  switch (state) {
    case 'online': return 'bg-success-bg text-success-ink';
    case 'stale': return 'bg-warn-bg text-warn-ink';
    case 'offline': return 'bg-danger-bg text-danger-ink';
    default: return 'bg-surface-2 text-ink-soft';
  }
}

@Component({
  selector: 'edge-device-detail-page',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-2xl p-8">
      <header class="mb-6">
        <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Dispositivo</h1>
        <p class="mt-1 text-sm text-ink-soft">Conectividade e gestão do agente edge.</p>
      </header>

      @if (facade.loading()) {
        <div class="flex items-center gap-2 text-sm text-ink-soft">
          <span class="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
          Carregando…
        </div>
      } @else if (facade.error()) {
        <div class="rounded-xl border border-danger-border bg-danger-bg px-4 py-3 text-sm text-danger-ink">
          {{ facade.error() }}
        </div>
      } @else if (facade.detail(); as detail) {
        <!-- Device identity card -->
        <div class="rounded-2xl border border-line bg-surface p-6 shadow-card">
          <div class="flex items-start justify-between gap-4">
            <div>
              <p class="text-xs font-medium uppercase tracking-wider text-ink-faint">ID do dispositivo</p>
              <p class="mt-0.5 font-mono text-sm text-ink">{{ detail.device.deviceId }}</p>
            </div>
            <span class="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold"
                  [class]="stateBadgeClass(detail.derivedState)">
              {{ stateLabel(detail.derivedState) }}
            </span>
          </div>

          <div class="mt-5 grid grid-cols-2 gap-4 sm:grid-cols-3">
            <div>
              <p class="text-xs text-ink-faint">Versão</p>
              <p class="mt-0.5 text-sm font-medium text-ink">{{ detail.device.versao ?? '—' }}</p>
            </div>
            <div>
              <p class="text-xs text-ink-faint">Última sincronização</p>
              <p class="mt-0.5 text-sm font-medium text-ink">{{ detail.lastSyncRelative }}</p>
            </div>
            <div>
              <p class="text-xs text-ink-faint">Perfil de detecção</p>
              <p class="mt-0.5 text-sm font-medium text-ink">
                @if (detail.profileDrift) {
                  <span class="text-warn-ink">Desatualizado</span>
                } @else {
                  Atualizado
                }
              </p>
            </div>
          </div>

          <!-- Management actions (conductor only) -->
          @if (detail.canManage) {
            <div class="mt-6 flex flex-wrap gap-3 border-t border-line pt-5">
              <a routerLink="/edge/pairing"
                 class="inline-flex items-center gap-1.5 rounded-xl border border-brand bg-brand-subtle px-4 py-2 text-sm font-medium text-brand transition-colors hover:bg-brand hover:text-brand-ink">
                <span class="material-symbols-outlined text-[18px]">link</span>
                Reparear
              </a>
              <button
                (click)="confirmUnpair()"
                class="inline-flex items-center gap-1.5 rounded-xl border border-line bg-surface px-4 py-2 text-sm font-medium text-ink-soft transition-colors hover:border-danger-border hover:text-danger-ink">
                <span class="material-symbols-outlined text-[18px]">link_off</span>
                Despareador
              </button>
              <button
                (click)="confirmRetire()"
                class="inline-flex items-center gap-1.5 rounded-xl border border-line bg-surface px-4 py-2 text-sm font-medium text-ink-soft transition-colors hover:border-danger-border hover:text-danger-ink">
                <span class="material-symbols-outlined text-[18px]">cancel</span>
                Aposentar
              </button>
            </div>
          }
        </div>

        <!-- Honesty note (ADR-005) -->
        <p class="mt-4 text-xs text-ink-faint">
          O estado exibido é derivado da idade do último heartbeat —
          nunca de um flag estático. Um dispositivo silencioso nunca aparece como online.
        </p>
      } @else {
        <!-- Not paired state -->
        <div class="rounded-2xl border border-line bg-surface p-8 text-center">
          <span class="material-symbols-outlined text-[48px] text-ink-faint">device_unknown</span>
          <p class="mt-3 text-sm font-medium text-ink">Nenhum dispositivo pareado</p>
          <p class="mt-1 text-xs text-ink-soft">
            Solicite o pareamento durante a configuração inicial do sistema.
          </p>
          <a routerLink="/edge/pairing"
             class="mt-5 inline-flex items-center gap-2 rounded-xl bg-brand px-5 py-2.5 text-sm font-bold text-brand-ink shadow-card transition-colors hover:bg-brand-strong">
            Parear dispositivo
          </a>
        </div>
      }

      @if (showUnpairConfirm()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
          <div class="w-full max-w-sm rounded-2xl border border-line bg-surface p-6 shadow-floating">
            <h2 class="font-display text-base font-bold text-ink">Despareador dispositivo?</h2>
            <p class="mt-2 text-sm text-ink-soft">
              O dispositivo ficará sem paciente vinculado. A monitoração para até o repareamento.
            </p>
            <div class="mt-5 flex gap-3">
              <button (click)="doUnpair()"
                      class="flex-1 rounded-xl bg-danger px-4 py-2 text-sm font-bold text-danger-ink">
                Despareador
              </button>
              <button (click)="showUnpairConfirm.set(false)"
                      class="flex-1 rounded-xl border border-line px-4 py-2 text-sm font-medium text-ink">
                Cancelar
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
})
export class DeviceDetailPageComponent implements OnInit {
  protected readonly facade = inject(DeviceFacade);
  private readonly route = inject(ActivatedRoute);

  protected readonly showUnpairConfirm = signal(false);
  protected readonly stateLabel = stateLabel;
  protected readonly stateBadgeClass = stateBadgeClass;

  ngOnInit(): void {
    const deviceId = this.route.snapshot.paramMap.get('deviceId');
    if (deviceId) {
      this.facade.load(deviceId);
    } else {
      // Resolve from active patient context
      // TODO[FE-EDGE-013]: inject EmpresaContextService to get active pacienteId
      this.facade.resolveForActivePatient('pac-marcio'); // placeholder
    }
    this.facade.startPolling();
  }

  protected confirmUnpair(): void {
    this.showUnpairConfirm.set(true);
  }

  protected doUnpair(): void {
    this.showUnpairConfirm.set(false);
    this.facade.unpair('Iniciado pelo condutor técnico via painel');
  }

  protected confirmRetire(): void {
    // TODO[FE-EDGE-016]: implement retire confirmation dialog (Layer 2)
    if (confirm('Aposentar este dispositivo? Ação irreversível.')) {
      this.facade.retire();
    }
  }
}
