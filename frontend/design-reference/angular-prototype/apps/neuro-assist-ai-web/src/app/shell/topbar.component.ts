import { Component, ChangeDetectionStrategy, inject, computed } from '@angular/core';
import { PacientesFacade, DispositivosFacade } from '../core/data-access';
import { UiStateService } from './ui-state.service';

/**
 * Topbar — paciente ativo + status do dispositivo sempre visível (wireframe §3.2.B,
 * Princípio 2.6: status de conectividade nunca fica escondido).
 */
@Component({
  selector: 'app-topbar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="flex h-16 flex-shrink-0 items-center gap-4 border-b border-line bg-surface px-6">
      <div class="flex items-center gap-2">
        <span class="material-symbols-outlined text-[20px] text-ink-soft">person</span>
        <span class="text-sm font-semibold text-ink">{{ paciente()?.nome ?? '—' }}</span>
      </div>
      @if (dispositivo(); as dev) {
        <span class="flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold"
              [class]="dev.status === 'online' ? 'bg-success-bg text-success-ink' : 'bg-danger-bg text-danger-ink'">
          <span class="material-symbols-outlined text-[16px]">{{ dev.status === 'online' ? 'wifi' : 'wifi_off' }}</span>
          Dispositivo {{ dev.status === 'online' ? 'online' : 'offline' }}
        </span>
      }
      <div class="flex-1"></div>
      <button (click)="ui.toggleTheme()"
        class="grid size-9 place-items-center rounded-xl text-ink-soft transition-colors hover:bg-surface-2 hover:text-ink"
        title="Alternar tema claro/escuro">
        <span class="material-symbols-outlined text-[20px]">{{ ui.theme() === 'light' ? 'dark_mode' : 'light_mode' }}</span>
      </button>
      <div class="grid size-8 place-items-center rounded-full bg-brand/15 text-xs font-bold text-brand">JA</div>
    </header>
  `,
})
export class TopbarComponent {
  readonly ui = inject(UiStateService);
  private readonly pacientes = inject(PacientesFacade).items;
  private readonly dispositivos = inject(DispositivosFacade).items;
  readonly paciente = computed(() => this.pacientes()[0] ?? null);
  readonly dispositivo = computed(() => this.dispositivos()[0] ?? null);
}
