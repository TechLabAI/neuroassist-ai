import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ConnectivityChipComponent } from '@neuroassist/edge';

// ---------------------------------------------------------------------------
// TopbarComponent — shell header with always-visible connectivity chip (SPEC-06 §5)
// Connectivity is read via EdgeShellIntegrationService (via ConnectivityChipComponent
// which injects DeviceFacade) — never by importing DeviceFacade directly (nx-boundary).
//
// ADR-005: the chip reflects DerivedState (freshness-based), never the raw status flag.
// SPEC-01 §1: connectivity chip is always visible on every authenticated route.
// ---------------------------------------------------------------------------

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
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
      <!-- ConnectivityChipComponent reads DerivedState from DeviceFacade via EdgeShellIntegrationService -->
      <edge-connectivity-chip />

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
}
