import { Component, ChangeDetectionStrategy, inject, computed } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { PacientesFacade, DispositivosFacade, SessaoPacienteFacade } from '../core/data-access';

// Emenda 3 (2026-08-30) — shell separado e mais restrito para quando o dispositivo ativo
// é a sessão do paciente (celular), não a máquina da cuidadora. Decisão de wireframe
// (adendo em design/wireframe.md, 2026-08-30): o celular do paciente carrega DUAS
// identidades independentes (edge.device para GEST + SessaoPaciente para CONV, ver
// ADR-012/014) e autoridades muito mais restritas que a cuidadora — só CONV/GEST, sem
// Histórico/Calibração/Onboarding. Um shell com sidebar completa e item condicional
// exporia rotas que o paciente não tem autoridade para ver; o shell separado nunca as
// renderiza, em vez de escondê-las por RBAC no cliente.
//
// Wireframe §3.2.B mantém o indicador "modo paciente" como conceito — ele vive aqui,
// não como variação condicional da Topbar da cuidadora (TopbarComponent).
@Component({
  selector: 'app-patient-shell',
  standalone: true,
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="patient-layout">
      <header class="patient-topbar">
        <span class="flex items-center gap-1.5 rounded-full bg-info-bg px-2.5 py-1 text-xs font-semibold text-info-ink">
          <span class="material-symbols-outlined text-[16px]">smartphone</span>
          Sessão do paciente · ativa desde {{ sessaoDesde() }}
        </span>
        @if (dispositivo(); as dev) {
          <span class="flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold"
                [class]="dev.status === 'online' ? 'bg-success-bg text-success-ink' : 'bg-danger-bg text-danger-ink'">
            <span class="material-symbols-outlined text-[16px]">{{ dev.status === 'online' ? 'wifi' : 'wifi_off' }}</span>
            {{ dev.status === 'online' ? 'Online' : 'Offline' }}
          </span>
        }
      </header>
      <main class="patient-content"><router-outlet /></main>
    </div>
  `,
  styles: [`:host { display: contents; }
    .patient-layout { display: flex; flex-direction: column; height: 100vh; overflow: hidden; }
    .patient-topbar { display: flex; flex-shrink: 0; align-items: center; gap: 0.75rem;
                       height: 3.5rem; padding: 0 1.25rem; border-bottom: 1px solid var(--color-line, #2a2a2a); }
    .patient-content { flex: 1; overflow: auto; background-color: var(--content-bg);
                        background-image: var(--content-mesh); }`],
})
export class PatientShellComponent {
  private readonly pacientesFacade = inject(PacientesFacade);
  private readonly dispositivosFacade = inject(DispositivosFacade);
  private readonly sessoesFacade = inject(SessaoPacienteFacade);

  readonly dispositivo = computed(() => this.dispositivosFacade.items().find(d => d.pacienteId === this.pacientesFacade.items()[0]?.id) ?? null);

  readonly sessaoDesde = computed(() => {
    const p = this.pacientesFacade.items()[0];
    const sess = this.sessoesFacade.items().find(s => s.pacienteId === p?.id && s.status === 'ativa');
    if (!sess) return '—';
    const d = new Date(sess.provisionadaEm);
    return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
  });
}
