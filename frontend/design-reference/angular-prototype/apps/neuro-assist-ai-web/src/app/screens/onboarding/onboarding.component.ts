import { Component, ChangeDetectionStrategy, inject, signal, computed, effect } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DispositivosFacade, TenantsFacade, PacientesFacade, CuidadoresFacade } from '../../core/data-access';
import { DsCardComponent } from '../../ui/ds-card.component';
import { DsButtonComponent } from '../../ui/ds-button.component';
import { DsInputComponent } from '../../ui/ds-input.component';
import { DsBadgeComponent } from '../../ui/ds-badge.component';

// Contrato: .build-plans/onboarding.plan.md  (o implementer lê SÓ o build-plan)
// Wireframe ref: §5.1–5.5 — Onboarding
// Objetivo: cadastro inicial de tenant, paciente e pareamento do dispositivo edge —
// conduzido uma única vez pelo condutor técnico, não pela cuidadora no dia a dia (fluxo 4.5).
// (Emenda 1) passo 4 aponta para o catálogo de comandos (Tela 04), não mais 1 calibração única.
// O estado de pareamento lê o DispositivosFacade real — se já existe um DispositivoEdge
// (mesma fonte que Home/Topbar), mostra "pareado" direto em vez de simular do zero.

type Pareamento = 'idle' | 'pareando' | 'pareado';

@Component({
  selector: 'app-onboarding',
  standalone: true,
  imports: [RouterLink, DsCardComponent, DsButtonComponent, DsInputComponent, DsBadgeComponent],
  template: `
    <div class="mx-auto max-w-6xl p-8">
      <header class="mb-7">
        <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Configuração inicial</h1>
        <p class="mt-1 text-sm text-ink-soft">Passo único, conduzido pelo condutor técnico — não faz parte do uso diário da cuidadora.</p>
      </header>

      <div class="grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
        <!-- Coluna principal: passos do wizard -->
        <div class="flex flex-col gap-5">
          <app-ds-card>
            <div class="mb-3 flex items-center gap-2">
              <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">1</span>
              <h2 class="font-display text-sm font-semibold text-ink">Unidade de cuidado</h2>
            </div>
            <app-ds-input placeholder="Nome da família ou clínica" [(value)]="tenantNome" icon="home" />
          </app-ds-card>

          <app-ds-card>
            <div class="mb-3 flex items-center gap-2">
              <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">2</span>
              <h2 class="font-display text-sm font-semibold text-ink">Paciente</h2>
            </div>
            <app-ds-input placeholder="Nome do paciente" [(value)]="pacienteNome" icon="person" />
          </app-ds-card>

          <app-ds-card>
            <div class="mb-3 flex items-center gap-2">
              <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">3</span>
              <h2 class="font-display text-sm font-semibold text-ink">Dispositivo edge</h2>
            </div>
            <div class="flex items-center justify-between gap-3 rounded-lg bg-surface-2 px-4 py-3">
              @if (pareamentoEfetivo() === 'idle') {
                <span class="text-sm text-ink-soft">Nenhum dispositivo pareado ainda.</span>
                <app-ds-button size="sm" variant="secondary" (click)="parear()">Parear dispositivo</app-ds-button>
              } @else if (pareamentoEfetivo() === 'pareando') {
                <span class="flex items-center gap-2 text-sm font-medium text-brand">
                  <span class="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
                  Pareando…
                </span>
                <app-ds-badge tone="nf">Aguardando</app-ds-badge>
              } @else if (dispositivoExistente(); as dev) {
                <span class="flex items-center gap-2 text-sm font-medium text-success-ink">
                  <span class="material-symbols-outlined text-[18px]">check_circle</span>
                  Dispositivo já pareado · versão {{ dev.versao }}
                </span>
                <app-ds-badge [tone]="dev.status === 'online' ? 'ok' : 'er'">{{ dev.status === 'online' ? 'Online' : 'Offline' }}</app-ds-badge>
              } @else {
                <span class="flex items-center gap-2 text-sm font-medium text-success-ink">
                  <span class="material-symbols-outlined text-[18px]">check_circle</span>
                  Dispositivo pareado
                </span>
                <app-ds-badge tone="ok">Online</app-ds-badge>
              }
            </div>
          </app-ds-card>

          <app-ds-card>
            <div class="mb-3 flex items-center gap-2">
              <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">4</span>
              <h2 class="font-display text-sm font-semibold text-ink">Primeiro comando</h2>
            </div>
            <div class="flex items-center justify-between gap-3">
              <p class="text-sm text-ink-soft">Configure ao menos 1 comando (gesto → necessidade) no catálogo antes de convidar a cuidadora.</p>
              <a routerLink="/calibracao" class="flex-shrink-0">
                <app-ds-button size="sm" variant="secondary">
                  Ir ao catálogo <span class="material-symbols-outlined text-[16px]">arrow_forward</span>
                </app-ds-button>
              </a>
            </div>
          </app-ds-card>

          <app-ds-card>
            <div class="mb-3 flex items-center gap-2">
              <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">5</span>
              <h2 class="font-display text-sm font-semibold text-ink">Convidar cuidadora</h2>
            </div>
            <div class="flex gap-3">
              <app-ds-input class="flex-1" placeholder="Nome da cuidadora" [(value)]="cuidadoraNome" icon="person" />
              <app-ds-input class="flex-1" placeholder="E-mail" type="email" [(value)]="cuidadoraEmail" icon="mail" />
            </div>
          </app-ds-card>

          <!-- Emenda 3 (2026-08-30) — Tela 07, opcional, fora dos 5 passos numerados
               (o dispositivo edge fixo já cobre o caso majoritário). -->
          <app-ds-card>
            <div class="mb-3 flex items-center gap-2">
              <span class="material-symbols-outlined text-[18px] text-ink-faint">smartphone</span>
              <h2 class="font-display text-sm font-semibold text-ink">Opcional — celular do paciente</h2>
            </div>
            <div class="flex items-center justify-between gap-3">
              <p class="text-sm text-ink-soft">Se o paciente consegue operar o próprio celular (ex.: Tobii), ative uma sessão persistente nele.</p>
              <a routerLink="/celular-paciente" class="flex-shrink-0">
                <app-ds-button size="sm" variant="secondary">
                  Ativar celular <span class="material-symbols-outlined text-[16px]">arrow_forward</span>
                </app-ds-button>
              </a>
            </div>
          </app-ds-card>
        </div>

        <!-- Coluna lateral: progresso + conclusão -->
        <div class="flex flex-col gap-6">
          <app-ds-card>
            <h3 class="mb-3 font-display text-sm font-semibold text-ink">Progresso</h3>
            <div class="flex flex-col gap-2.5">
              @for (item of checklist(); track item.label) {
                <div class="flex items-center gap-2.5 text-sm">
                  <span class="material-symbols-outlined text-[18px]" [class]="item.done ? 'text-success' : 'text-ink-faint'">
                    {{ item.done ? 'check_circle' : 'radio_button_unchecked' }}
                  </span>
                  <span [class]="item.done ? 'text-ink' : 'text-ink-soft'">{{ item.label }}</span>
                </div>
              }
            </div>
          </app-ds-card>

          <app-ds-card>
            <p class="text-xs text-ink-soft">Conclua todos os passos ao lado para liberar o painel diário da cuidadora.</p>
            <div class="mt-4">
              <app-ds-button [disabled]="!podeConcluir()" (click)="concluir()">Concluir configuração</app-ds-button>
            </div>
            @if (concluido()) {
              <p class="mt-3 flex items-center gap-1.5 text-sm font-medium text-success-ink">
                <span class="material-symbols-outlined text-[18px]">check_circle</span> Configuração concluída
              </p>
            }
          </app-ds-card>
        </div>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingComponent {
  private readonly dispositivosFacade = inject(DispositivosFacade);
  private readonly tenantsFacade = inject(TenantsFacade);
  private readonly pacientesFacade = inject(PacientesFacade);
  private readonly cuidadoresFacade = inject(CuidadoresFacade);

  readonly tenantNome = signal('');
  readonly pacienteNome = signal('');
  readonly cuidadoraNome = signal('');
  readonly cuidadoraEmail = signal('');
  readonly pareamento = signal<Pareamento>('idle');
  readonly concluido = signal(false);

  constructor() {
    // Pré-preenche com o que já existe nas facades reais (mesma fonte que
    // Home/Histórico/Calibração) em vez de valores literais fixos — o mock
    // representa uma família já configurada, não um formulário em branco.
    effect(() => {
      const t = this.tenantsFacade.items()[0];
      if (t) this.tenantNome.set(t.nome);
    });
    effect(() => {
      const p = this.pacientesFacade.items()[0];
      if (p) this.pacienteNome.set(p.nome);
    });
    effect(() => {
      const c = this.cuidadoresFacade.items()[0];
      if (c) {
        this.cuidadoraNome.set(c.nome);
        this.cuidadoraEmail.set(c.email);
      }
    });
  }

  /** DispositivoEdge já existente na mesma fonte que Home/Topbar (1:1 por paciente). */
  readonly dispositivoExistente = computed(() => this.dispositivosFacade.items()[0] ?? null);

  /** Estado exibido: se já existe um dispositivo real, é "pareado" direto — o fluxo de
   * pareamento simulado (parear()) só aparece quando de fato não há nenhum ainda. */
  readonly pareamentoEfetivo = computed<Pareamento>(() =>
    this.dispositivoExistente() ? 'pareado' : this.pareamento(),
  );

  readonly checklist = computed(() => [
    { label: 'Unidade de cuidado', done: this.tenantNome().trim().length > 0 },
    { label: 'Paciente', done: this.pacienteNome().trim().length > 0 },
    { label: 'Dispositivo pareado', done: this.pareamentoEfetivo() === 'pareado' },
    { label: 'Cuidadora convidada', done: this.cuidadoraEmail().trim().length > 0 },
  ]);

  readonly podeConcluir = computed(() =>
    this.tenantNome().trim().length > 0 &&
    this.pacienteNome().trim().length > 0 &&
    this.pareamentoEfetivo() === 'pareado' &&
    this.cuidadoraEmail().trim().length > 0,
  );

  protected parear(): void {
    this.pareamento.set('pareando');
    setTimeout(() => this.pareamento.set('pareado'), 1600);
  }

  protected concluir(): void {
    this.concluido.set(true);
    // TODO[fe-autopilot]: persistir Tenant, Paciente, DispositivoEdge, Cuidador e o primeiro Comando via HttpAdapters
  }
}
