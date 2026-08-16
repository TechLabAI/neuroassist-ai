import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from './sidebar.component';
import { TopbarComponent } from './topbar.component';
import { AlarmOverlayComponent } from '@neuroassist/evnt';

// =============================================================================
// AppShellComponent — persistent shell layout wrapping all feature routes.
//
// AlarmOverlayComponent is included directly here (above the router-outlet)
// so it renders at interrupt z-layer on every shell route. This is the interim
// implementation of the global-outlet pattern described in ADR-005 — the full
// ShellOutletRegistry dynamic rendering (INT-FE-EVNT-001) is planned for a
// future iteration once the registry gains reactive signal-based notification.
//
// The overlay is self-hiding: AlarmOverlayComponent only renders content when
// facade.alarmVm().visible is true (i.e., activeEvent != null). It has zero
// visual footprint when no alarm is active.
// =============================================================================

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    SidebarComponent,
    TopbarComponent,
    AlarmOverlayComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- Global alarm overlay (ADR-005 interrupt layer) — above sidebar, topbar, and router-outlet -->
    <evnt-alarm-overlay />

    <div class="shell-layout">
      <app-sidebar class="shell-sidebar" />
      <div class="shell-main">
        <app-topbar class="shell-topbar" />
        <main class="shell-content">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
  styles: [`
    .shell-layout {
      display: grid;
      grid-template-columns: auto 1fr;
      grid-template-rows: 1fr;
      height: 100vh;
      overflow: hidden;
    }
    .shell-main {
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    .shell-content {
      flex: 1;
      overflow: auto;
    }
  `],
})
export class AppShellComponent {}
