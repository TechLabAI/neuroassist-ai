import { Component, ChangeDetectionStrategy, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AcsCaregiverFacade } from '../acs-caregiver.facade';
import { CoverageBlockModalComponent } from '../ui/coverage-block-modal.component';

// =============================================================================
// CaregiverRosterComponent — SPEC-01 route /acs
// Lists active caregivers for the active tenant with papel and status.
// Coverage badge per patient derived from AcsCaregiverFacade.coverageFor().
// Deactivate uses inline confirmation signal — never browser confirm().
// =============================================================================

@Component({
  selector: 'acs-caregiver-roster',
  standalone: true,
  imports: [RouterLink, CoverageBlockModalComponent],
  template: `
    <!-- ADR-005 blocking modal: shown when deactivate/unlink would leave a patient uncovered -->
    @if (facade.coverageModal() !== null) {
      <acs-coverage-block-modal
        [type]="facade.coverageModal()!.type"
        [pacienteNome]="facade.coverageModal()!.pacienteNome"
        (confirmed)="facade.dismissCoverageModal()"
      />
    }

    <div class="mx-auto max-w-4xl p-8">
      <header class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Cuidadores</h1>
          <p class="mt-1 text-sm text-ink-soft">
            Gerencie quem tem acesso ao painel e quais pacientes cada um cobre.
          </p>
        </div>
        <a
          routerLink="/acs/cuidadores/novo"
          class="inline-flex items-center gap-2 rounded-xl bg-brand px-5 py-2.5 text-sm font-semibold text-brand-ink shadow-card hover:bg-brand-strong"
          aria-label="Convidar novo cuidador"
        >
          <span class="material-symbols-outlined text-[18px]" aria-hidden="true">person_add</span>
          Convidar cuidador
        </a>
      </header>

      @if (facade.loading()) {
        <div class="flex items-center gap-2 text-sm text-ink-soft" role="status" data-testid="roster-loading">
          <span class="material-symbols-outlined animate-spin text-[18px]" aria-hidden="true">progress_activity</span>
          Carregando cuidadores…
        </div>
      } @else if (facade.error()) {
        <!-- Error banner with retry -->
        <div class="rounded-lg bg-error-subtle px-4 py-3" role="alert" data-testid="roster-error">
          <p class="text-sm text-error-ink">{{ facade.error() }}</p>
          <button
            class="mt-2 text-xs font-medium text-error-ink underline hover:no-underline"
            (click)="retryLoad()"
            aria-label="Tentar carregar cuidadores novamente"
          >
            Tentar novamente
          </button>
        </div>
      } @else {
        <div class="flex flex-col gap-3">
          @for (c of facade.activeCaregivers(); track c.id) {
            <div
              class="flex items-center justify-between rounded-xl border border-border bg-surface p-4"
              data-testid="roster-row"
            >
              <div class="flex items-center gap-3">
                <span class="material-symbols-outlined text-brand" aria-hidden="true">person</span>
                <div>
                  <p class="text-sm font-semibold text-ink">{{ c.nome }}</p>
                  <p class="text-xs text-ink-soft">{{ c.email }} · {{ c.papelLabel }}</p>
                </div>
              </div>

              <div class="flex items-center gap-3">
                <a
                  [routerLink]="['/acs/cuidadores', c.id, 'editar']"
                  class="text-xs font-medium text-brand hover:underline"
                  [attr.aria-label]="'Editar cuidador ' + c.nome"
                >Editar</a>

                @if (pendingDeactivateId() === c.id) {
                  <!-- Inline confirmation row -->
                  <div class="flex items-center gap-2" role="group" [attr.aria-label]="'Confirmar desativação de ' + c.nome">
                    <span class="text-xs text-ink-soft">Desativar?</span>
                    <button
                      class="rounded-lg bg-error px-3 py-1 text-xs font-semibold text-error-ink hover:bg-error-strong disabled:opacity-40"
                      [disabled]="facade.saving()"
                      (click)="confirmDeactivate(c.id)"
                      [attr.aria-label]="'Confirmar desativação de ' + c.nome"
                      data-testid="confirm-deactivate-btn"
                    >
                      {{ facade.saving() ? 'Desativando…' : 'Confirmar' }}
                    </button>
                    <button
                      class="rounded-lg border border-border px-3 py-1 text-xs font-semibold text-ink hover:bg-surface-2"
                      (click)="cancelDeactivate()"
                      [attr.aria-label]="'Cancelar desativação de ' + c.nome"
                      data-testid="cancel-deactivate-btn"
                    >Cancelar</button>
                  </div>
                } @else {
                  <button
                    class="text-xs font-medium text-error hover:underline disabled:opacity-40"
                    [disabled]="facade.saving()"
                    (click)="requestDeactivate(c.id)"
                    [attr.aria-label]="'Desativar cuidador ' + c.nome"
                    data-testid="deactivate-btn"
                  >Desativar</button>
                }
              </div>
            </div>
          }
          @if (facade.activeCaregivers().length === 0) {
            <p class="text-sm text-ink-soft" data-testid="roster-empty">
              Nenhum cuidador ativo. Convide o primeiro.
            </p>
          }
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaregiverRosterComponent implements OnInit {
  readonly facade = inject(AcsCaregiverFacade);

  /** cuidadorId pending deactivation confirmation — avoids browser confirm(). */
  readonly pendingDeactivateId = signal<string | null>(null);

  ngOnInit(): void {
    void this.facade.loadRoster();
  }

  retryLoad(): void {
    void this.facade.loadRoster();
  }

  /** First click on Desativar — shows inline confirmation. */
  requestDeactivate(id: string): void {
    this.pendingDeactivateId.set(id);
  }

  cancelDeactivate(): void {
    this.pendingDeactivateId.set(null);
  }

  /** Second click — performs actual deactivation. */
  async confirmDeactivate(id: string): Promise<void> {
    await this.facade.deactivate(id);
    this.pendingDeactivateId.set(null);
  }
}
