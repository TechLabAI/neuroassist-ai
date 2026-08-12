import { Component, ChangeDetectionStrategy, inject, signal, output } from '@angular/core';
import { AcsTenantFacade } from '../acs-tenant.facade';
import type { TenantVm } from '../acs.types';

// =============================================================================
// TenantSetupComponent — onboarding wizard step 1 (composed, scope:web)
// ACS contributes this step; the scope:web wizard shell mounts it via ng-content
// or direct import (SPEC-01 §1, composition table).
// On success: emits tenantProvisioned with the new TenantVm.
// TODO[FE-ACS-002]: write tenant.id to @neuroassist/shared/empresa-context on success
// =============================================================================

@Component({
  selector: 'acs-tenant-setup',
  standalone: true,
  imports: [],
  template: `
    <div class="flex flex-col gap-3">
      <div>
        <label class="mb-1 block text-xs font-semibold text-ink-soft">Nome da unidade de cuidado</label>
        <input
          type="text"
          class="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
          [value]="nome()"
          (input)="nome.set($any($event.target).value)"
          placeholder="Ex.: Família Amaral ou Clínica São Paulo"
        />
      </div>
      <div class="flex gap-3">
        <label class="flex cursor-pointer items-center gap-2 rounded-lg border border-border p-3 text-sm"
               [class]="tipo() === 'familia' ? 'border-brand bg-brand-subtle' : ''">
          <input type="radio" name="tipo" value="familia"
                 [checked]="tipo() === 'familia'"
                 (change)="tipo.set('familia')" class="sr-only"/>
          <span class="material-symbols-outlined text-[16px]">home</span>
          Família
        </label>
        <label class="flex cursor-pointer items-center gap-2 rounded-lg border border-border p-3 text-sm"
               [class]="tipo() === 'clinica' ? 'border-brand bg-brand-subtle' : ''">
          <input type="radio" name="tipo" value="clinica"
                 [checked]="tipo() === 'clinica'"
                 (change)="tipo.set('clinica')" class="sr-only"/>
          <span class="material-symbols-outlined text-[16px]">local_hospital</span>
          Clínica
        </label>
      </div>

      @if (facade.error()) {
        <p class="rounded-lg bg-error-subtle px-3 py-2 text-xs text-error-ink">{{ facade.error() }}</p>
      }

      @if (facade.provisioning()) {
        <div class="flex items-center gap-2 text-xs text-brand">
          <span class="material-symbols-outlined animate-spin text-[16px]">progress_activity</span>
          Provisionando schema de dados… (pode levar alguns segundos)
        </div>
      }

      <button
        class="self-start rounded-xl bg-brand px-5 py-2.5 text-sm font-semibold text-brand-ink hover:bg-brand-strong disabled:opacity-50"
        [disabled]="facade.loading() || !nome().trim()"
        (click)="provision()"
      >
        {{ facade.loading() ? 'Criando…' : 'Criar unidade de cuidado' }}
      </button>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TenantSetupComponent {
  readonly facade = inject(AcsTenantFacade);

  readonly nome = signal('');
  readonly tipo = signal<'familia' | 'clinica'>('familia');

  readonly tenantProvisioned = output<TenantVm>();

  async provision(): Promise<void> {
    const vm = await this.facade.create({ nome: this.nome().trim(), tipo: this.tipo() });
    if (vm?.isActive) {
      this.tenantProvisioned.emit(vm);
    }
  }
}
