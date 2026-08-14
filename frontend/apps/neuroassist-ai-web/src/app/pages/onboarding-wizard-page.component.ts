import { Component, ChangeDetectionStrategy, inject, signal, computed, effect } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AcsTenantFacade, AcsCaregiverFacade } from '@neuroassist/acs';
import { DeviceFacade } from '@neuroassist/edge';
import { PacPacienteFacade } from '@neuroassist/pac';

// =============================================================================
// OnboardingWizardPageComponent — scope:web wizard shell (ACS owns this page)
// Promotes design-reference onboarding.component.ts to production.
// ACS owns the wizard orchestration; each step composites a module's step feature.
//
// Step 1: Tenant setup        → AcsTenantFacade.create()
// Step 2: Patient register    → PacComandoFacade.patient (PAC-owned)
// Step 3: Edge device pairing → EdgeDeviceFacade (EDGE-owned)
// Step 4: First command       → link to /pac (PAC catalog)
// Step 5: Invite caregiver    → AcsCaregiverFacade.invite()
//
// Route: /onboarding (scope:web, outside shell — uses AppComponent directly)
// Wired by app.routes.ts as a standalone loadComponent (no shell layout).
// =============================================================================

type Pareamento = 'idle' | 'pareando' | 'pareado';

@Component({
  selector: 'app-onboarding-wizard-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="mx-auto max-w-6xl p-8">
      <header class="mb-7">
        <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Configuração inicial</h1>
        <p class="mt-1 text-sm text-ink-soft">Passo único, conduzido pelo condutor técnico — não faz parte do uso diário da cuidadora.</p>
      </header>

      <div class="grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
        <!-- Coluna principal: passos do wizard -->
        <div class="flex flex-col gap-5">
          <!-- Step 1: Unidade de cuidado (ACS) -->
          <div class="rounded-2xl border border-border bg-surface p-5">
            <div class="mb-3 flex items-center gap-2">
              <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">1</span>
              <h2 class="font-display text-sm font-semibold text-ink">Unidade de cuidado</h2>
              @if (tenantFacade.tenant()) {
                <span class="ml-auto text-xs font-medium text-success-ink">
                  <span class="material-symbols-outlined text-[14px] align-middle">check_circle</span>
                  {{ tenantFacade.tenant()!.nome }}
                </span>
              }
            </div>
            @if (!tenantFacade.tenant()) {
              <div class="flex gap-3">
                <input
                  type="text"
                  class="flex-1 rounded-lg border border-border bg-surface-2 px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
                  [value]="tenantNome()"
                  (input)="tenantNome.set($any($event.target).value)"
                  placeholder="Nome da família ou clínica"
                />
                <button
                  class="rounded-xl bg-brand px-4 py-2 text-sm font-semibold text-brand-ink hover:bg-brand-strong disabled:opacity-50"
                  [disabled]="!tenantNome().trim() || tenantFacade.loading()"
                  (click)="criarTenant()"
                >
                  {{ tenantFacade.loading() ? 'Criando…' : 'Criar' }}
                </button>
              </div>
              @if (tenantFacade.error()) {
                <p class="mt-2 text-xs text-error-ink">{{ tenantFacade.error() }}</p>
              }
            }
          </div>

          <!-- Step 2: Paciente (PAC) -->
          <div class="rounded-2xl border border-border bg-surface p-5">
            <div class="mb-3 flex items-center gap-2">
              <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">2</span>
              <h2 class="font-display text-sm font-semibold text-ink">Paciente</h2>
            </div>
            <p class="text-sm text-ink-soft">
              O registro do paciente é gerido pelo módulo PAC.
              <a routerLink="/pac" class="font-medium text-brand hover:underline">Ir ao catálogo</a>
            </p>
          </div>

          <!-- Step 3: Dispositivo edge (EDGE) -->
          <div class="rounded-2xl border border-border bg-surface p-5">
            <div class="mb-3 flex items-center gap-2">
              <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">3</span>
              <h2 class="font-display text-sm font-semibold text-ink">Dispositivo edge</h2>
            </div>
            <div class="flex items-center justify-between gap-3 rounded-lg bg-surface-2 px-4 py-3">
              @if (pareamentoEfetivo() === 'idle') {
                <span class="text-sm text-ink-soft">Nenhum dispositivo pareado ainda.</span>
                <button
                  class="rounded-xl border border-border px-3 py-1.5 text-xs font-semibold text-ink hover:bg-surface-2"
                  (click)="parear()"
                >Parear dispositivo</button>
              } @else if (pareamentoEfetivo() === 'pareando') {
                <span class="flex items-center gap-2 text-sm font-medium text-brand">
                  <span class="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
                  Pareando…
                </span>
              } @else {
                <span class="flex items-center gap-2 text-sm font-medium text-success-ink">
                  <span class="material-symbols-outlined text-[18px]">check_circle</span>
                  Dispositivo pareado
                </span>
              }
            </div>
          </div>

          <!-- Step 4: Primeiro comando (PAC) -->
          <div class="rounded-2xl border border-border bg-surface p-5">
            <div class="mb-3 flex items-center gap-2">
              <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">4</span>
              <h2 class="font-display text-sm font-semibold text-ink">Primeiro comando</h2>
            </div>
            <div class="flex items-center justify-between gap-3">
              <p class="text-sm text-ink-soft">Configure ao menos 1 comando (gesto → necessidade) no catálogo antes de convidar a cuidadora.</p>
              <a routerLink="/pac" class="flex-shrink-0">
                <button class="rounded-xl border border-border px-3 py-1.5 text-xs font-semibold text-ink hover:bg-surface-2">
                  Ir ao catálogo
                  <span class="material-symbols-outlined text-[14px] align-middle">arrow_forward</span>
                </button>
              </a>
            </div>
          </div>

          <!-- Step 5: Convidar cuidadora (ACS) -->
          <div class="rounded-2xl border border-border bg-surface p-5">
            <div class="mb-3 flex items-center gap-2">
              <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">5</span>
              <h2 class="font-display text-sm font-semibold text-ink">Convidar cuidadora</h2>
            </div>
            <div class="flex flex-col gap-3">
              <div class="flex gap-3">
                <input
                  type="text"
                  class="flex-1 rounded-lg border border-border bg-surface-2 px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
                  [value]="cuidadoraNome()"
                  (input)="cuidadoraNome.set($any($event.target).value)"
                  placeholder="Nome da cuidadora"
                />
                <input
                  type="email"
                  class="flex-1 rounded-lg border border-border bg-surface-2 px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
                  [value]="cuidadoraEmail()"
                  (input)="cuidadoraEmail.set($any($event.target).value)"
                  placeholder="E-mail"
                />
              </div>
              @if (caregiverFacade.error()) {
                <p class="text-xs text-error-ink">{{ caregiverFacade.error() }}</p>
              }
            </div>
          </div>
        </div>

        <!-- Coluna lateral: progresso + conclusão -->
        <div class="flex flex-col gap-6">
          <div class="rounded-2xl border border-border bg-surface p-5">
            <h3 class="mb-3 font-display text-sm font-semibold text-ink">Progresso</h3>
            <div class="flex flex-col gap-2.5">
              @for (item of checklist(); track item.label) {
                <div class="flex items-center gap-2.5 text-sm">
                  <span class="material-symbols-outlined text-[18px]"
                        [class]="item.done ? 'text-success' : 'text-ink-faint'">
                    {{ item.done ? 'check_circle' : 'radio_button_unchecked' }}
                  </span>
                  <span [class]="item.done ? 'text-ink' : 'text-ink-soft'">{{ item.label }}</span>
                </div>
              }
            </div>
          </div>

          <div class="rounded-2xl border border-border bg-surface p-5">
            <p class="text-xs text-ink-soft">Conclua todos os passos ao lado para liberar o painel diário da cuidadora.</p>
            <div class="mt-4">
              <button
                class="w-full rounded-xl bg-brand py-2.5 text-sm font-semibold text-brand-ink hover:bg-brand-strong disabled:opacity-50"
                [disabled]="!podeConcluir()"
                (click)="concluir()"
              >
                Concluir configuração
              </button>
            </div>
            @if (concluido()) {
              <p class="mt-3 flex items-center gap-1.5 text-sm font-medium text-success-ink">
                <span class="material-symbols-outlined text-[18px]">check_circle</span>
                Configuração concluída
              </p>
            }
          </div>
        </div>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingWizardPageComponent {
  readonly tenantFacade = inject(AcsTenantFacade);
  readonly caregiverFacade = inject(AcsCaregiverFacade);
  private readonly edgeFacade = inject(DeviceFacade);
  private readonly pacFacade = inject(PacPacienteFacade);

  readonly tenantNome = signal('');
  readonly cuidadoraNome = signal('');
  readonly cuidadoraEmail = signal('');
  readonly pareamento = signal<Pareamento>('idle');
  readonly concluido = signal(false);

  readonly dispositivoExistente = computed(() => this.edgeFacade.device());

  readonly pareamentoEfetivo = computed<Pareamento>(() =>
    this.dispositivoExistente() ? 'pareado' : this.pareamento(),
  );

  readonly checklist = computed(() => [
    { label: 'Unidade de cuidado', done: !!this.tenantFacade.tenant() },
    { label: 'Paciente registrado', done: this.pacFacade.hasPatient() },
    { label: 'Dispositivo pareado', done: this.pareamentoEfetivo() === 'pareado' },
    { label: 'Cuidadora convidada', done: this.cuidadoraEmail().trim().length > 0 },
  ]);

  readonly podeConcluir = computed(() =>
    !!this.tenantFacade.tenant() &&
    this.pareamentoEfetivo() === 'pareado' &&
    this.cuidadoraEmail().trim().length > 0,
  );

  async criarTenant(): Promise<void> {
    await this.tenantFacade.create({ nome: this.tenantNome().trim(), tipo: 'familia' });
  }

  protected parear(): void {
    this.pareamento.set('pareando');
    setTimeout(() => this.pareamento.set('pareado'), 1600);
  }

  protected async concluir(): Promise<void> {
    if (this.cuidadoraEmail().trim()) {
      await this.caregiverFacade.invite({
        nome: this.cuidadoraNome().trim() || 'Cuidadora',
        email: this.cuidadoraEmail().trim(),
        papel: 'cuidadora',
      });
    }
    this.concluido.set(true);
  }
}
