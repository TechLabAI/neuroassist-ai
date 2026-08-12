import { Component, ChangeDetectionStrategy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AcsCaregiverFacade } from '../acs-caregiver.facade';

// =============================================================================
// CaregiverRosterComponent — SPEC-01 route /acs
// Lists caregivers for the active tenant with their papel and status.
// Coverage badge per patient derived from AcsCaregiverFacade.coverageFor().
// TODO[FE-ACS-001]: full roster UI (Tailwind, ds-badge, deactivate action)
// =============================================================================

@Component({
  selector: 'acs-caregiver-roster',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="mx-auto max-w-4xl p-8">
      <header class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Cuidadores</h1>
          <p class="mt-1 text-sm text-ink-soft">Gerencie quem tem acesso ao painel e quais pacientes cada um cobre.</p>
        </div>
        <a routerLink="/acs/cuidadores/novo"
           class="inline-flex items-center gap-2 rounded-xl bg-brand px-5 py-2.5 text-sm font-semibold text-brand-ink shadow-card hover:bg-brand-strong">
          <span class="material-symbols-outlined text-[18px]">person_add</span>
          Convidar cuidador
        </a>
      </header>

      @if (facade.loading()) {
        <div class="flex items-center gap-2 text-sm text-ink-soft">
          <span class="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
          Carregando cuidadores…
        </div>
      } @else if (facade.error()) {
        <p class="rounded-lg bg-error-subtle px-4 py-3 text-sm text-error-ink">{{ facade.error() }}</p>
      } @else {
        <div class="flex flex-col gap-3">
          @for (c of facade.activeCaregivers(); track c.id) {
            <div class="flex items-center justify-between rounded-xl border border-border bg-surface p-4">
              <div class="flex items-center gap-3">
                <span class="material-symbols-outlined text-brand">person</span>
                <div>
                  <p class="text-sm font-semibold text-ink">{{ c.nome }}</p>
                  <p class="text-xs text-ink-soft">{{ c.email }} · {{ c.papelLabel }}</p>
                </div>
              </div>
              <a [routerLink]="['/acs/cuidadores', c.id, 'editar']"
                 class="text-xs font-medium text-brand hover:underline">Editar</a>
            </div>
          }
          @if (facade.activeCaregivers().length === 0) {
            <p class="text-sm text-ink-soft">Nenhum cuidador ativo. Convide o primeiro.</p>
          }
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaregiverRosterComponent implements OnInit {
  readonly facade = inject(AcsCaregiverFacade);

  ngOnInit(): void {
    void this.facade.loadRoster();
  }
}
