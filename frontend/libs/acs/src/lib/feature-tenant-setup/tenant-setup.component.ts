import { Component, ChangeDetectionStrategy, inject, signal, output } from '@angular/core';
import { AcsTenantFacade } from '../acs-tenant.facade';
import type { TenantVm, TenantTipo } from '../acs.types';

// =============================================================================
// TenantSetupComponent — onboarding wizard step 1 (composed, scope:web)
// ACS contributes this step; the scope:web wizard shell mounts it via ng-content
// or direct import (SPEC-01 §1, composition table).
//
// 2-step flow:
//   Step 1 — Select tenant type (família | clínica)
//   Step 2 — Enter tenant name + confirm provisioning
//
// On success: emits tenantProvisioned with the new TenantVm.
// TODO[FE-ACS-002]: write tenant.id to @neuroassist/shared/empresa-context on success
// =============================================================================

@Component({
  selector: 'acs-tenant-setup',
  standalone: true,
  imports: [],
  template: `
    <div class="flex flex-col gap-4" data-testid="tenant-setup">

      <!-- Step indicator -->
      <div class="flex items-center gap-2 text-xs text-ink-soft" role="status" aria-label="Passo {{ currentStep() }} de 2">
        <span
          class="flex h-5 w-5 items-center justify-center rounded-full text-[10px] font-bold"
          [class]="currentStep() === 1 ? 'bg-brand text-brand-ink' : 'bg-surface-2 text-ink-soft'"
          aria-hidden="true"
        >1</span>
        <span [class]="currentStep() === 1 ? 'text-ink font-medium' : ''">Tipo de unidade</span>
        <span class="text-border" aria-hidden="true">→</span>
        <span
          class="flex h-5 w-5 items-center justify-center rounded-full text-[10px] font-bold"
          [class]="currentStep() === 2 ? 'bg-brand text-brand-ink' : 'bg-surface-2 text-ink-soft'"
          aria-hidden="true"
        >2</span>
        <span [class]="currentStep() === 2 ? 'text-ink font-medium' : ''">Nome e confirmação</span>
      </div>

      <!-- Step 1: type selection -->
      @if (currentStep() === 1) {
        <div data-testid="step-1">
          <p class="mb-3 text-sm font-semibold text-ink">Qual é o tipo da sua unidade de cuidado?</p>
          <div class="flex gap-3">
            <label
              class="flex flex-1 cursor-pointer items-center gap-2 rounded-xl border p-4 text-sm transition-colors"
              [class]="tipo() === 'familia' ? 'border-brand bg-brand-subtle' : 'border-border'"
              data-testid="tipo-familia"
            >
              <input
                type="radio"
                name="tipo"
                value="familia"
                [checked]="tipo() === 'familia'"
                (change)="tipo.set('familia')"
                class="sr-only"
                aria-label="Família"
              />
              <span class="material-symbols-outlined text-[20px]" aria-hidden="true">home</span>
              <div>
                <p class="font-semibold">Família</p>
                <p class="text-xs text-ink-soft">Cuidados em domicílio</p>
              </div>
            </label>
            <label
              class="flex flex-1 cursor-pointer items-center gap-2 rounded-xl border p-4 text-sm transition-colors"
              [class]="tipo() === 'clinica' ? 'border-brand bg-brand-subtle' : 'border-border'"
              data-testid="tipo-clinica"
            >
              <input
                type="radio"
                name="tipo"
                value="clinica"
                [checked]="tipo() === 'clinica'"
                (change)="tipo.set('clinica')"
                class="sr-only"
                aria-label="Clínica"
              />
              <span class="material-symbols-outlined text-[20px]" aria-hidden="true">local_hospital</span>
              <div>
                <p class="font-semibold">Clínica</p>
                <p class="text-xs text-ink-soft">Instituição de saúde</p>
              </div>
            </label>
          </div>

          <button
            class="mt-4 self-start rounded-xl bg-brand px-5 py-2.5 text-sm font-semibold text-brand-ink hover:bg-brand-strong"
            (click)="nextStep()"
            data-testid="step-1-next"
          >
            Próximo
          </button>
        </div>
      }

      <!-- Step 2: name + confirmation -->
      @if (currentStep() === 2) {
        <div data-testid="step-2">
          <p class="mb-1 text-sm text-ink-soft">
            Tipo selecionado:
            <span class="font-semibold text-ink">{{ tipo() === 'familia' ? 'Família' : 'Clínica' }}</span>
          </p>

          <div class="mt-3">
            <label for="tenant-nome" class="mb-1 block text-xs font-semibold text-ink-soft">
              Nome da unidade de cuidado <span aria-hidden="true" class="text-error-ink">*</span>
            </label>
            <input
              id="tenant-nome"
              type="text"
              class="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
              [value]="nome()"
              (input)="nome.set($any($event.target).value)"
              placeholder="Ex.: Família Amaral ou Clínica São Paulo"
              data-testid="tenant-nome"
            />
          </div>

          @if (facade.error()) {
            <p class="mt-3 rounded-lg bg-error-subtle px-3 py-2 text-xs text-error-ink" role="alert" data-testid="tenant-error">
              {{ facade.error() }}
            </p>
          }

          @if (facade.provisioning()) {
            <div class="mt-3 flex items-center gap-2 text-xs text-brand" role="status" data-testid="tenant-provisioning">
              <span class="material-symbols-outlined animate-spin text-[16px]" aria-hidden="true">progress_activity</span>
              Provisionando schema de dados… (pode levar alguns segundos)
            </div>
          }

          <div class="mt-4 flex gap-3">
            <button
              class="rounded-xl border border-border px-4 py-2.5 text-sm font-semibold text-ink hover:bg-surface-2"
              (click)="prevStep()"
              data-testid="step-2-back"
            >
              Voltar
            </button>
            <button
              class="rounded-xl bg-brand px-5 py-2.5 text-sm font-semibold text-brand-ink hover:bg-brand-strong disabled:opacity-50"
              [disabled]="facade.loading() || !nome().trim()"
              (click)="provision()"
              data-testid="step-2-confirm"
            >
              {{ facade.loading() ? 'Criando…' : 'Criar unidade de cuidado' }}
            </button>
          </div>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TenantSetupComponent {
  readonly facade = inject(AcsTenantFacade);

  /** Current step: 1 = type selection, 2 = name + confirmation. */
  readonly currentStep = signal<1 | 2>(1);
  readonly tipo = signal<TenantTipo>('familia');
  readonly nome = signal('');

  readonly tenantProvisioned = output<TenantVm>();

  nextStep(): void {
    this.currentStep.set(2);
  }

  prevStep(): void {
    this.currentStep.set(1);
  }

  async provision(): Promise<void> {
    if (!this.nome().trim()) return;
    const vm = await this.facade.create({ nome: this.nome().trim(), tipo: this.tipo() });
    if (vm?.isActive) {
      this.tenantProvisioned.emit(vm);
    }
  }
}
