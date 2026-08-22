import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from './sidebar.component';
import { TopbarComponent } from './topbar.component';
import { AlarmOverlayComponent } from '@neuroassist/evnt';
import { DashHomeFacade } from '@neuroassist/dash';
import { EdgeShellIntegrationService } from '@neuroassist/edge';

// =============================================================================
// AppShellComponent — layout persistente que envolve todas as rotas de feature.
//
// AlarmOverlayComponent entra aqui direto (acima do router-outlet) para renderizar
// na camada de interrupção em toda rota do shell. É a implementação interina do
// padrão de outlet global descrito no ADR-005 — o ShellOutletRegistry dinâmico
// (INT-FE-EVNT-001) fica para quando o registry ganhar notificação reativa por signal.
//
// O overlay se auto-oculta: só renderiza conteúdo quando facade.alarmVm().visible
// é true (activeEvent != null). Sem alarme, pegada visual zero.
//
// O shell dispara DashHomeFacade.load() uma vez: paciente e cuidadora aparecem no
// chrome (topbar/sidebar) em qualquer rota, não só na home. Guardado por status
// 'idle' para não duplicar a carga que a própria home faz.
//
// E, assim que o paciente ativo é conhecido, inicia a conectividade do EDGE
// (SPEC-06 §3). Sem essa chamada o chip da topbar ficava em "Sem dispositivo"
// para sempre — contradizendo, na mesma tela, o "Dispositivo online" que a home
// mostra a partir do summary do DASH.
// =============================================================================

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, TopbarComponent, AlarmOverlayComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- Overlay global de alarme (camada de interrupção ADR-005) — acima de sidebar, topbar e outlet -->
    <evnt-alarm-overlay />

    <div class="grid h-screen grid-cols-[auto_1fr] overflow-hidden">
      <app-sidebar />
      <div class="flex min-w-0 flex-col overflow-hidden">
        <app-topbar />
        <main class="content-mesh min-h-0 flex-1 overflow-y-auto">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class AppShellComponent {
  private readonly dash = inject(DashHomeFacade);
  private readonly edge = inject(EdgeShellIntegrationService);

  /** Guarda: resolveForActivePatient/startPolling só devem rodar uma vez. */
  private connectivityStarted = false;

  constructor() {
    if (this.dash.status() === 'idle') {
      void this.dash.load();
    }

    effect(() => {
      const pacienteId = this.dash.patient()?.patientId;
      if (!pacienteId || this.connectivityStarted) return;
      this.connectivityStarted = true;
      this.edge.startConnectivityForActivePatient(pacienteId);
    });
  }
}
