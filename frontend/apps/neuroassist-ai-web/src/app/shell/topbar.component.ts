import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ConnectivityChipComponent, EdgeShellIntegrationService } from '@neuroassist/edge';
import { DashHomeFacade } from '@neuroassist/dash';
import { UiStateService } from './ui-state.service';

// ---------------------------------------------------------------------------
// TopbarComponent — paciente ativo + status do dispositivo sempre visível
// (wireframe §3.2.B, SPEC-01 §1, Princípio 2.6: conectividade nunca escondida).
//
// INT-FE-EDGE-002: a conectividade vem de EdgeShellIntegrationService
// .connectivityReadModel() — único caminho de composição sancionado (ADR-006,
// SPEC-06 §5). O VM entra como [vmExternal] para o topbar controlar o binding
// explicitamente, em vez de depender da injeção interna de DeviceFacade no chip.
//
// nx-boundary: TopbarComponent importa apenas dos barris @neuroassist/edge e
// @neuroassist/dash (scope:web→scope:* via barril — permitido em depConstraints).
//
// ADR-005: o chip reflete DerivedState (baseado em frescor), nunca a flag crua.
//
// O <select> de tenant do esqueleto Wave-0 foi removido: nunca foi populado
// ("Populated at runtime from the auth/tenant service") e ocupava o lugar do
// nome do paciente, que é a informação que a cuidadora precisa ver.
// ---------------------------------------------------------------------------

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [ConnectivityChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header
      class="flex h-16 flex-shrink-0 items-center gap-3 border-b border-line bg-surface px-4 md:gap-4 md:px-6">
      <div class="flex items-center gap-2">
        <span class="material-symbols-outlined text-[20px] text-ink-soft">person</span>
        <span class="text-sm font-semibold text-ink">{{ paciente() }}</span>
      </div>

      <!-- Chip de conectividade — sempre visível (Wireframe §3.2.B, ADR-005) -->
      <edge-connectivity-chip
        [vmExternal]="edgeConnectivity()"
        (open)="router.navigate(['/edge', 'device'])" />

      <div class="flex-1"></div>

      <button
        type="button"
        (click)="ui.toggleTheme()"
        class="grid size-9 place-items-center rounded-xl text-ink-soft transition-colors hover:bg-surface-2 hover:text-ink"
        [title]="ui.theme() === 'light' ? 'Mudar para tema escuro' : 'Mudar para tema claro'"
        [attr.aria-label]="ui.theme() === 'light' ? 'Mudar para tema escuro' : 'Mudar para tema claro'">
        <span class="material-symbols-outlined text-[20px]">
          {{ ui.theme() === 'light' ? 'dark_mode' : 'light_mode' }}
        </span>
      </button>

      <!-- text-ink, não text-brand: no tema escuro --color-brand continua o navy #1B2F5C
           e o monograma sumiria contra a topbar escura. -->
      <div class="grid size-8 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-ink">
        {{ iniciais() }}
      </div>
    </header>
  `,
})
export class TopbarComponent {
  protected readonly router = inject(Router);
  protected readonly ui = inject(UiStateService);

  /** Read-model signal de EdgeShellIntegrationService — único caminho (ADR-006). */
  protected readonly edgeConnectivity = inject(EdgeShellIntegrationService).connectivityReadModel();

  private readonly dash = inject(DashHomeFacade);
  protected readonly paciente = computed(() => this.dash.patient()?.name ?? '—');
  protected readonly iniciais = computed(() => {
    const nome = this.dash.caregiverName().trim();
    if (!nome) return '—';
    const partes = nome.split(/\s+/);
    return ((partes[0][0] ?? '') + (partes.length > 1 ? (partes[partes.length - 1][0] ?? '') : ''))
      .toUpperCase();
  });
}
