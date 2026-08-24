import {
  Component,
  ChangeDetectionStrategy,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DeviceFacade } from '../edge.facade';
import { ConnectivityStatusCardComponent } from './connectivity-status-card.component';
import { DeviceMetaListComponent } from './device-meta-list.component';
import { DeviceActionsComponent } from './device-actions.component';

// ---------------------------------------------------------------------------
// DeviceDetailPageComponent — FE-EDGE-014
// Smart container: owns polling lifecycle + confirmation dialogs.
// Composes:
//   - ConnectivityStatusCardComponent  — derived-state badge (FE-EDGE-012)
//   - DeviceMetaListComponent          — device metadata (FE-EDGE-013)
//   - DeviceActionsComponent           — conductor-gated actions (FE-EDGE-013)
//
// Route: /edge/device[/:deviceId]
// Read: all authenticated members.
// Manage actions: conductor only (canManage derived from session entitlement).
//
// OnPush; Standalone; signals-first.
// Honesty note (ADR-005): derived state from heartbeat freshness — never a flag.
// ---------------------------------------------------------------------------

@Component({
  selector: 'edge-device-detail-page',
  standalone: true,
  imports: [
    RouterLink,
    ConnectivityStatusCardComponent,
    DeviceMetaListComponent,
    DeviceActionsComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-2xl p-8">
      <header class="mb-6">
        <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Dispositivo</h1>
        <p class="mt-1 text-sm text-ink-soft">Conectividade e gestão do agente edge.</p>
      </header>

      @if (facade.loading()) {
        <!-- Loading skeleton — prevents flash of empty (FE-EDGE-018 DoD) -->
        <div data-testid="loading-indicator" aria-label="Carregando dispositivo…" aria-busy="true">
          <!-- Connectivity card skeleton -->
          <div class="mb-4 h-20 animate-pulse rounded-xl bg-surface-2"></div>
          <!-- Device card skeleton -->
          <div class="rounded-2xl border border-line bg-surface p-6 shadow-card">
            <div class="mb-4 h-4 w-1/3 animate-pulse rounded bg-surface-2"></div>
            <div class="mb-3 h-3 w-2/3 animate-pulse rounded bg-surface-2"></div>
            <div class="mb-2 h-3 w-1/2 animate-pulse rounded bg-surface-2"></div>
            <div class="h-3 w-3/4 animate-pulse rounded bg-surface-2"></div>
          </div>
        </div>
      } @else if (facade.error()) {
        <div
          class="rounded-xl border border-danger bg-danger-bg px-4 py-3 text-sm text-danger-ink"
          data-testid="error-message">
          {{ facade.error() }}
        </div>
      } @else if (facade.detail(); as detail) {

        <!-- Connectivity status card (FE-EDGE-012) -->
        <div class="mb-4">
          <edge-connectivity-status-card
            [state]="detail.derivedState"
            [lastSyncRelative]="detail.lastSyncRelative"
            [versao]="detail.device.versao" />
        </div>

        <!-- Device identity + metadata card -->
        <div class="rounded-2xl border border-line bg-surface p-6 shadow-card">
          <!--
            NÃO reintroduzir aqui um cabeçalho "ID do dispositivo": o
            <edge-device-meta-list> logo abaixo já publica esse mesmo par
            rótulo/valor como primeiro item da <dl>. Enquanto os dois existiam,
            a página repetia "ID do dispositivo — edge-marcio-01" duas vezes
            seguidas, o que o leitor de tela lê duas vezes e torna qualquer
            locator por esse rótulo ambíguo.
          -->

          <!-- Metadata list (FE-EDGE-013) -->
          <edge-device-meta-list
            [device]="detail.device"
            [lastSyncRelative]="detail.lastSyncRelative" />

          <!-- Management actions (FE-EDGE-013; conductor only) -->
          @if (detail.canManage) {
            <div class="mt-6 border-t border-line pt-5">
              <edge-device-actions
                [canManage]="detail.canManage"
                (unpairAction)="confirmUnpair()"
                (retireAction)="confirmRetire()" />
            </div>
          }
        </div>

        <!-- Honesty note (ADR-005) -->
        <p class="mt-4 text-xs text-ink-faint">
          O estado exibido é derivado da idade do último heartbeat —
          nunca de um flag estático. Um dispositivo silencioso nunca aparece como online.
        </p>

      } @else {
        <!-- Not paired state (FE-EDGE-018 DoD: caregiver vs conductor paths) -->
        <div class="rounded-2xl border border-line bg-surface p-8 text-center" data-testid="not-paired-state">
          <span aria-hidden="true" class="material-symbols-outlined text-[48px] text-ink-faint">device_unknown</span>
          <p class="mt-3 text-sm font-medium text-ink">Nenhum dispositivo pareado</p>

          @if (facade.canManage()) {
            <!-- Conductor path: show pairing CTA -->
            <p class="mt-1 text-xs text-ink-soft">
              Solicite o pareamento durante a configuração inicial do sistema.
            </p>
            <a routerLink="/edge/pairing"
               data-testid="pairing-cta"
               class="mt-5 inline-flex items-center gap-2 rounded-xl bg-brand px-5 py-2.5 text-sm font-bold text-brand-ink shadow-card transition-colors hover:bg-brand-strong">
              Parear dispositivo
            </a>
          } @else {
            <!-- Caregiver path: informational only — no management affordance (FE-EDGE-018 DoD) -->
            <p class="mt-1 text-xs text-ink-soft" data-testid="not-paired-caregiver-msg">
              O condutor técnico ainda não configurou o dispositivo. Aguarde a conclusão do processo de pareamento.
            </p>
          }
        </div>
      }

      <!-- Unpair confirmation dialog -->
      @if (showUnpairConfirm()) {
        <div
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
          data-testid="unpair-confirm-dialog">
          <div class="w-full max-w-sm rounded-2xl border border-line bg-surface p-6 shadow-floating">
            <h2 class="font-display text-base font-bold text-ink">Despareador dispositivo?</h2>
            <p class="mt-2 text-sm text-ink-soft">
              O dispositivo ficará sem paciente vinculado. A monitoração para até o repareamento.
            </p>
            <div class="mt-5 flex gap-3">
              <button
                (click)="doUnpair()"
                data-testid="confirm-unpair-btn"
                class="flex-1 rounded-xl bg-danger px-4 py-2 text-sm font-bold text-danger-ink">
                Despareador
              </button>
              <button
                (click)="showUnpairConfirm.set(false)"
                class="flex-1 rounded-xl border border-line px-4 py-2 text-sm font-medium text-ink">
                Cancelar
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Retire confirmation dialog -->
      @if (showRetireConfirm()) {
        <div
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
          data-testid="retire-confirm-dialog">
          <div class="w-full max-w-sm rounded-2xl border border-line bg-surface p-6 shadow-floating">
            <h2 class="font-display text-base font-bold text-ink">Aposentar dispositivo?</h2>
            <p class="mt-2 text-sm text-ink-soft">
              Esta ação é irreversível. O dispositivo não poderá mais ser pareado.
            </p>
            <div class="mt-5 flex gap-3">
              <button
                (click)="doRetire()"
                data-testid="confirm-retire-btn"
                class="flex-1 rounded-xl bg-danger px-4 py-2 text-sm font-bold text-danger-ink">
                Aposentar
              </button>
              <button
                (click)="showRetireConfirm.set(false)"
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
  protected readonly showRetireConfirm = signal(false);

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
    this.showRetireConfirm.set(true);
  }

  protected doRetire(): void {
    this.showRetireConfirm.set(false);
    this.facade.retire();
  }
}
