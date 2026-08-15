import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ConnectivityChipComponent, EdgeShellIntegrationService } from '@neuroassist/edge';

// ---------------------------------------------------------------------------
// TopbarComponent — shell header with always-visible connectivity chip (SPEC-06 §5)
//
// INT-FE-EDGE-002: connectivity is read via EdgeShellIntegrationService.connectivityReadModel()
// — the sole sanctioned composition path (ADR-006, SPEC-06 §5). The VM is passed as
// [vmExternal] to ConnectivityChipComponent so the topbar controls the data binding
// explicitly rather than relying on the chip's internal DeviceFacade injection.
//
// nx-boundary: TopbarComponent imports only from @neuroassist/edge barrel (scope:web→scope:edge
// via barrel — allowed per .eslintrc depConstraints). No scope:edge internal paths.
//
// ADR-005: the chip reflects DerivedState (freshness-based), never the raw status flag.
// SPEC-01 §1: connectivity chip is always visible on every authenticated route.
// ---------------------------------------------------------------------------

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [
    FormsModule,
    ConnectivityChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="topbar">
      <span class="topbar-title">NeuroAssist AI</span>
      <select class="topbar-tenant" [ngModel]="activeEmpresa()" (ngModelChange)="activeEmpresa.set($event)">
        <!-- Populated at runtime from the auth/tenant service -->
      </select>

      <!-- Connectivity chip — always visible (Wireframe §3.2.B, ADR-005) -->
      <!-- VM is provided via EdgeShellIntegrationService.connectivityReadModel() (INT-FE-EDGE-002) -->
      <!-- (open) routes to /edge/device — chip's internal RouterLink also handles direct clicks -->
      <edge-connectivity-chip
        [vmExternal]="edgeConnectivity()"
        (open)="router.navigate(['/edge', 'device'])" />

      <div class="topbar-actions">
        <!-- search, notifications, user menu -->
      </div>
    </header>
  `,
  styles: [`
    .topbar {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 0.5rem 1rem;
      border-bottom: 1px solid #e5e7eb;
    }
    .topbar-title { font-weight: 600; }
    .topbar-actions { margin-left: auto; display: flex; gap: 0.5rem; }
  `],
})
export class TopbarComponent {
  readonly activeEmpresa = signal<string>('');

  protected readonly router = inject(Router);

  /** Read-model signal from EdgeShellIntegrationService — the sole composition path (ADR-006). */
  protected readonly edgeConnectivity = inject(EdgeShellIntegrationService).connectivityReadModel();
}
