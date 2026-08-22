import { Component, ChangeDetectionStrategy, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AcsCaregiverFacade } from '../acs-caregiver.facade';
import type { CuidadorPapel } from '../acs.types';

// =============================================================================
// CaregiverEditorComponent — SPEC-01 routes /acs/cuidadores/novo and /acs/cuidadores/:id/editar
// :cuidadorId present → edit mode (pre-fill from facade.selected via ngOnInit)
// :cuidadorId absent  → invite mode (blank draft)
// ADR-005: deactivate blocked if last covering caregiver for any patient.
// Draft preservation: form restored from facade.selected() on edit mode load.
// =============================================================================

@Component({
  selector: 'acs-caregiver-editor',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="mx-auto max-w-xl p-8">
      <header class="mb-6">
        <h1 class="font-display text-xl font-bold text-ink">
          {{ isEdit() ? 'Editar cuidador' : 'Convidar cuidador' }}
        </h1>
      </header>

      @if (facade.error()) {
        <p class="mb-4 rounded-lg bg-error-bg px-4 py-3 text-sm text-error-ink" role="alert">
          {{ facade.error() }}
        </p>
      }

      <form [formGroup]="draftForm" (ngSubmit)="save()" novalidate class="flex flex-col gap-4">
        <!-- Nome -->
        <div>
          <label for="editor-nome" class="mb-1 block text-xs font-semibold text-ink-soft">
            Nome <span aria-hidden="true" class="text-error-ink">*</span>
          </label>
          <input
            id="editor-nome"
            type="text"
            formControlName="nome"
            class="w-full rounded-lg border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
            [class]="draftForm.get('nome')?.invalid && draftForm.get('nome')?.touched ? 'border-error' : 'border-line'"
            placeholder="Nome completo"
            data-testid="editor-nome"
          />
          @if (draftForm.get('nome')?.errors?.['required'] && draftForm.get('nome')?.touched) {
            <p class="mt-1 text-xs text-error-ink" role="alert">Nome é obrigatório.</p>
          }
          @if (draftForm.get('nome')?.errors?.['minlength'] && draftForm.get('nome')?.touched) {
            <p class="mt-1 text-xs text-error-ink" role="alert">Nome deve ter ao menos 2 caracteres.</p>
          }
        </div>

        <!-- E-mail -->
        <div>
          <label for="editor-email" class="mb-1 block text-xs font-semibold text-ink-soft">
            E-mail <span aria-hidden="true" class="text-error-ink">*</span>
          </label>
          <input
            id="editor-email"
            type="email"
            formControlName="email"
            class="w-full rounded-lg border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
            [class]="draftForm.get('email')?.invalid && draftForm.get('email')?.touched ? 'border-error' : 'border-line'"
            placeholder="email@exemplo.com"
            data-testid="editor-email"
          />
          @if (draftForm.get('email')?.errors?.['required'] && draftForm.get('email')?.touched) {
            <p class="mt-1 text-xs text-error-ink" role="alert">E-mail é obrigatório.</p>
          }
          @if (draftForm.get('email')?.errors?.['email'] && draftForm.get('email')?.touched) {
            <p class="mt-1 text-xs text-error-ink" role="alert">Informe um e-mail válido.</p>
          }
        </div>

        <!-- Papel -->
        <div>
          <label for="editor-papel" class="mb-1 block text-xs font-semibold text-ink-soft">
            Papel <span aria-hidden="true" class="text-error-ink">*</span>
          </label>
          <select
            id="editor-papel"
            formControlName="papel"
            class="w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
            data-testid="editor-papel"
          >
            <option value="cuidadora">Cuidadora</option>
            <option value="coordenador_clinica">Coordenador de Clínica</option>
          </select>
        </div>

        <!-- Actions -->
        <div class="mt-2 flex gap-3">
          <button
            type="submit"
            class="rounded-xl bg-brand px-5 py-2.5 text-sm font-semibold text-brand-ink hover:bg-brand-strong disabled:opacity-50"
            [disabled]="draftForm.invalid || facade.saving()"
            data-testid="editor-submit"
          >
            {{ facade.saving() ? 'Salvando…' : (isEdit() ? 'Salvar' : 'Convidar') }}
          </button>
          <button
            type="button"
            class="rounded-xl border border-line px-5 py-2.5 text-sm font-semibold text-ink hover:bg-surface-2"
            (click)="cancel()"
            data-testid="editor-cancel"
          >Cancelar</button>
        </div>
      </form>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaregiverEditorComponent implements OnInit {
  readonly facade = inject(AcsCaregiverFacade);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly isEdit = signal(false);
  private editingId = signal<string | null>(null);

  /** Reactive form with validators. Draft is preserved in the form state itself. */
  readonly draftForm = this.fb.group({
    nome:  ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    papel: ['cuidadora' as CuidadorPapel, Validators.required],
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('cuidadorId');
    if (id) {
      this.isEdit.set(true);
      this.editingId.set(id);
      // Draft preservation: restore form from existing caregiver data on edit load.
      // On error (unknown cuidadorId): redirect to /acs — no dead-end edit page.
      void this.facade.loadOne(id).then(() => {
        if (this.facade.error()) {
          void this.router.navigate(['/acs']);
          return;
        }
        const sel = this.facade.selected();
        if (sel) {
          this.draftForm.patchValue({
            nome:  sel.nome,
            email: sel.email,
            papel: sel.papel,
          });
        }
      });
    }
  }

  async save(): Promise<void> {
    if (this.draftForm.invalid) {
      this.draftForm.markAllAsTouched();
      return;
    }

    const raw = this.draftForm.getRawValue();
    const dto = {
      nome:  raw.nome ?? '',
      email: raw.email ?? '',
      papel: (raw.papel ?? 'cuidadora') as CuidadorPapel,
    };

    if (!this.isEdit()) {
      const vm = await this.facade.invite(dto);
      if (vm) void this.router.navigate(['/acs']);
    }
    // TODO[FE-ACS-012]: edit mode — update endpoint not yet in MVP api-contract; uses invite for now.
  }

  cancel(): void {
    void this.router.navigate(['/acs']);
  }
}
