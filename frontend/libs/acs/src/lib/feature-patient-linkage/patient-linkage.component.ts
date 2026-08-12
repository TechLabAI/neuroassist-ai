import { Component, ChangeDetectionStrategy, OnInit, inject, signal, computed } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AcsCaregiverFacade } from '../acs-caregiver.facade';

// =============================================================================
// PatientLinkageComponent — SPEC-01 route /acs/pacientes/:pacienteId/vinculos
// Shows caregivers linked to a patient + coverage badge.
// ADR-005: revoke blocked for last covering caregiver (BE 409, facade error signal).
// TODO[FE-ACS-022]: full linkage UI with coverage indicator and revoke confirmation modal
// =============================================================================

@Component({
  selector: 'acs-patient-linkage',
  standalone: true,
  imports: [],
  template: `
    <div class="mx-auto max-w-3xl p-8">
      <header class="mb-6">
        <h1 class="font-display text-xl font-bold text-ink">Vínculos do paciente</h1>
        <p class="mt-1 text-sm text-ink-soft">Cuidadores autorizados a receber alertas para este paciente.</p>
      </header>

      <div class="mb-4 flex items-center gap-2 rounded-xl border px-4 py-3"
           [class]="coverage().isCovered ? 'border-success bg-success-subtle' : 'border-error bg-error-subtle'">
        <span class="material-symbols-outlined text-[18px]"
              [class]="coverage().isCovered ? 'text-success' : 'text-error'">
          {{ coverage().isCovered ? 'check_circle' : 'warning' }}
        </span>
        <p class="text-sm font-medium"
           [class]="coverage().isCovered ? 'text-success-ink' : 'text-error-ink'">
          {{ coverage().isCovered
              ? 'Paciente coberto — ' + coverage().coveredBy.length + ' cuidador(es) ativo(s)'
              : 'Paciente sem cobertura — adicione ao menos um cuidador ativo' }}
        </p>
      </div>

      @if (facade.error()) {
        <p class="mb-4 rounded-lg bg-error-subtle px-4 py-3 text-sm text-error-ink">{{ facade.error() }}</p>
      }

      @if (facade.loading()) {
        <p class="text-sm text-ink-soft">Carregando cuidadores…</p>
      } @else {
        <div class="flex flex-col gap-3">
          @for (c of facade.activeCaregivers(); track c.id) {
            <div class="flex items-center justify-between rounded-xl border border-border bg-surface p-4">
              <div>
                <p class="text-sm font-semibold text-ink">{{ c.nome }}</p>
                <p class="text-xs text-ink-soft">{{ c.papelLabel }}</p>
              </div>
              <button
                class="text-xs font-medium text-brand hover:underline disabled:opacity-40"
                [disabled]="facade.saving()"
                (click)="toggleLink(c.id)"
              >
                {{ isLinked(c.id) ? 'Remover vínculo' : 'Vincular' }}
              </button>
            </div>
          }
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatientLinkageComponent implements OnInit {
  readonly facade = inject(AcsCaregiverFacade);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly pacienteId = signal('');

  readonly coverage = computed(() => this.facade.coverageFor(this.pacienteId()));

  isLinked(cuidadorId: string): boolean {
    return this.coverage().coveredBy.includes(cuidadorId);
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('pacienteId') ?? '';
    this.pacienteId.set(id);
    void this.facade.loadRoster();
  }

  async toggleLink(cuidadorId: string): Promise<void> {
    const pid = this.pacienteId();
    if (this.isLinked(cuidadorId)) {
      // TODO[FE-ACS-022]: show confirmation modal before revoke
      // For now: need linkId — load links for this caregiver first
      // Simplified: facade.revokeLink(cuidadorId, linkId, pid) — linkId lookup deferred
    } else {
      await this.facade.linkToPatient(cuidadorId, pid);
    }
  }
}
