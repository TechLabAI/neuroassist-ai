import { Component, ChangeDetectionStrategy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AcsCaregiverFacade } from '../acs-caregiver.facade';
import type { CaregiverDraft } from '../acs.types';

// =============================================================================
// CaregiverEditorComponent — SPEC-01 routes /acs/cuidadores/novo and /acs/cuidadores/:id/editar
// :cuidadorId present → edit mode (pre-fill from facade.selected)
// :cuidadorId absent  → invite mode (blank draft)
// ADR-005: deactivate blocked if last covering caregiver for any patient.
// TODO[FE-ACS-011]: full editor UI (Tailwind, ds-input, ds-button, ds-badge)
// =============================================================================

@Component({
  selector: 'acs-caregiver-editor',
  standalone: true,
  imports: [],
  template: `
    <div class="mx-auto max-w-xl p-8">
      <header class="mb-6">
        <h1 class="font-display text-xl font-bold text-ink">
          {{ isEdit() ? 'Editar cuidador' : 'Convidar cuidador' }}
        </h1>
      </header>

      @if (facade.error()) {
        <p class="mb-4 rounded-lg bg-error-subtle px-4 py-3 text-sm text-error-ink">{{ facade.error() }}</p>
      }

      <div class="flex flex-col gap-4">
        <div>
          <label class="mb-1 block text-xs font-semibold text-ink-soft">Nome</label>
          <input
            type="text"
            class="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
            [value]="draft().nome"
            (input)="draft.update(d => ({ ...d, nome: $any($event.target).value }))"
            placeholder="Nome completo"
          />
        </div>
        <div>
          <label class="mb-1 block text-xs font-semibold text-ink-soft">E-mail</label>
          <input
            type="email"
            class="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
            [value]="draft().email"
            (input)="draft.update(d => ({ ...d, email: $any($event.target).value }))"
            placeholder="email@exemplo.com"
          />
        </div>
        <div>
          <label class="mb-1 block text-xs font-semibold text-ink-soft">Papel</label>
          <select
            class="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
            [value]="draft().papel"
            (change)="draft.update(d => ({ ...d, papel: $any($event.target).value }))"
          >
            <option value="cuidadora">Cuidadora</option>
            <option value="coordenador_clinica">Coordenador de Clínica</option>
          </select>
        </div>
      </div>

      <div class="mt-6 flex gap-3">
        <button
          class="rounded-xl bg-brand px-5 py-2.5 text-sm font-semibold text-brand-ink hover:bg-brand-strong disabled:opacity-50"
          [disabled]="facade.saving() || !canSave()"
          (click)="save()"
        >
          {{ facade.saving() ? 'Salvando…' : (isEdit() ? 'Salvar' : 'Convidar') }}
        </button>
        <button
          class="rounded-xl border border-border px-5 py-2.5 text-sm font-semibold text-ink hover:bg-surface-2"
          (click)="cancel()"
        >Cancelar</button>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaregiverEditorComponent implements OnInit {
  readonly facade = inject(AcsCaregiverFacade);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly isEdit = signal(false);
  readonly draft = signal<CaregiverDraft>({ nome: '', email: '', papel: 'cuidadora' });

  readonly canSave = () => {
    const d = this.draft();
    return d.nome.trim().length > 0 && d.email.trim().includes('@');
  };

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('cuidadorId');
    if (id) {
      this.isEdit.set(true);
      void this.facade.loadOne(id).then(() => {
        const sel = this.facade.selected();
        if (sel) this.draft.set({ nome: sel.nome, email: sel.email, papel: sel.papel });
      });
    }
  }

  async save(): Promise<void> {
    const d = this.draft();
    if (!this.isEdit()) {
      const vm = await this.facade.invite({ nome: d.nome, email: d.email, papel: d.papel });
      if (vm) void this.router.navigate(['/acs']);
    }
    // TODO[FE-ACS-012]: edit mode (update endpoint not in MVP api-contract — uses invite for now)
  }

  cancel(): void {
    void this.router.navigate(['/acs']);
  }
}
