import { Component, ChangeDetectionStrategy, OnInit, Input, inject, signal, computed } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AcsCaregiverFacade } from '../acs-caregiver.facade';
import { CoverageBlockModalComponent } from '../ui/coverage-block-modal.component';

// =============================================================================
// PatientLinkageComponent — SPEC-01 route /acs/pacientes/:pacienteId/vinculos
// Shows caregivers linked to a patient + coverage badge.
// ADR-005: revoke blocked for last covering caregiver (BE 409, facade error signal).
// Inline confirmation state avoids browser confirm() — uses pendingRevokeId signal.
// =============================================================================

@Component({
  selector: 'acs-patient-linkage',
  standalone: true,
  imports: [CoverageBlockModalComponent],
  template: `
    <!-- ADR-005: modal BLOQUEANTE — vinculo cross-empresa (422) e re-imposicao do
         ultimo-vinculo (409). A facade ja emitia o sinal coverageModal nos dois
         casos e esta tela era a unica que nao o desenhava: o roster montava o
         modal, aqui o sinal era escrito e ninguem lia. -->
    @if (facade.coverageModal() !== null) {
      <acs-coverage-block-modal
        [type]="facade.coverageModal()!.type"
        [pacienteNome]="facade.coverageModal()!.pacienteNome"
        (confirmed)="facade.dismissCoverageModal()"
      />
    }

    <!-- 'composto' = esta superfície é um PEDAÇO de outra página (o passo de
         vínculo do assistente de configuração), não a página inteira. Sem isto o
         assistente ganhava um segundo <h1> e o seu próprio contêiner de largura,
         que é exatamente a entrada que o passo de pareamento do EDGE tem pelo
         mesmo motivo. -->
    <div [class]="estaComposto() ? '' : 'mx-auto max-w-3xl p-8'">
      @if (!estaComposto()) {
        <header class="mb-6">
          <h1 class="font-display text-xl font-bold text-ink">Vínculos do paciente</h1>
          <p class="mt-1 text-sm text-ink-soft">Cuidadores autorizados a receber alertas para este paciente.</p>
        </header>
      }

      <!-- Coverage badge -->
      <div
        class="mb-4 flex items-center gap-2 rounded-xl border px-4 py-3"
        [class]="coverage().isCovered ? 'border-success bg-success-bg' : 'border-error bg-error-bg'"
        data-testid="coverage-badge"
        role="status"
      >
        <span
          class="material-symbols-outlined text-[18px]"
          [class]="coverage().isCovered ? 'text-success' : 'text-error'"
          aria-hidden="true"
        >
          {{ coverage().isCovered ? 'check_circle' : 'warning' }}
        </span>
        <p
          class="text-sm font-medium"
          [class]="coverage().isCovered ? 'text-success-ink' : 'text-error-ink'"
        >
          {{ coverage().isCovered
              ? 'Paciente coberto — ' + coverage().coveredBy.length + ' cuidador(es) ativo(s)'
              : 'Paciente sem cobertura — adicione ao menos um cuidador ativo' }}
        </p>
      </div>

      <!-- Error banner -->
      @if (facade.error()) {
        <p class="mb-4 rounded-lg bg-error-bg px-4 py-3 text-sm text-error-ink" role="alert" data-testid="linkage-error">
          {{ facade.error() }}
        </p>
      }

      @if (facade.loading()) {
        <div class="flex items-center gap-2 text-sm text-ink-soft" role="status" data-testid="linkage-loading">
          <span class="material-symbols-outlined animate-spin text-[18px]" aria-hidden="true">progress_activity</span>
          Carregando cuidadores…
        </div>
      } @else {

        <!-- Linked caregivers — show first -->
        @if (linkedCaregivers().length > 0) {
          <section class="mb-6">
            <h2 class="mb-3 text-xs font-semibold uppercase tracking-wide text-ink-soft">Vínculos ativos</h2>
            <div class="flex flex-col gap-3">
              @for (c of linkedCaregivers(); track c.id) {
                <div
                  class="flex items-center justify-between rounded-xl border border-success bg-success-bg p-4"
                  data-testid="linked-caregiver-row"
                >
                  <div>
                    <p class="text-sm font-semibold text-ink">{{ c.nome }}</p>
                    <p class="text-xs text-ink-soft">{{ c.papelLabel }}</p>
                  </div>

                  @if (pendingRevokeId() === c.id) {
                    <!-- Inline confirmation -->
                    <div class="flex items-center gap-2">
                      <span class="text-xs text-ink-soft">Confirmar remoção?</span>
                      <button
                        class="rounded-lg bg-error px-3 py-1.5 text-xs font-semibold text-error-ink hover:bg-error disabled:opacity-40"
                        [disabled]="facade.saving()"
                        (click)="confirmRevoke(c.id)"
                        aria-label="Confirmar remoção de vínculo"
                        data-testid="confirm-revoke-btn"
                      >Confirmar</button>
                      <button
                        class="rounded-lg border border-line px-3 py-1.5 text-xs font-semibold text-ink hover:bg-surface-2"
                        (click)="cancelRevoke()"
                        aria-label="Cancelar remoção de vínculo"
                        data-testid="cancel-revoke-btn"
                      >Cancelar</button>
                    </div>
                  } @else {
                    <button
                      class="text-xs font-medium text-error hover:underline disabled:opacity-40"
                      [disabled]="facade.saving()"
                      (click)="requestRevoke(c.id)"
                      aria-label="Remover vínculo de {{ c.nome }}"
                      data-testid="revoke-link-btn"
                    >
                      Remover vínculo
                    </button>
                  }
                </div>
              }
            </div>
          </section>
        }

        <!-- Unlinked active caregivers — available to add -->
        @if (unlinkedCaregivers().length > 0) {
          <section>
            <h2 class="mb-3 text-xs font-semibold uppercase tracking-wide text-ink-soft">Disponíveis para vincular</h2>
            <div class="flex flex-col gap-3">
              @for (c of unlinkedCaregivers(); track c.id) {
                <div
                  class="flex items-center justify-between rounded-xl border border-line bg-surface p-4"
                  data-testid="unlinked-caregiver-row"
                >
                  <div>
                    <p class="text-sm font-semibold text-ink">{{ c.nome }}</p>
                    <p class="text-xs text-ink-soft">{{ c.papelLabel }}</p>
                  </div>
                  <button
                    class="text-xs font-medium text-brand hover:underline disabled:opacity-40"
                    [disabled]="facade.saving()"
                    (click)="link(c.id)"
                    aria-label="Vincular {{ c.nome }} a este paciente"
                    data-testid="link-caregiver-btn"
                  >
                    Vincular
                  </button>
                </div>
              }
            </div>
          </section>
        }

        @if (facade.activeCaregivers().length === 0) {
          <p class="text-sm text-ink-soft" data-testid="no-caregivers-msg">
            Nenhum cuidador ativo disponível. Convide um cuidador primeiro.
          </p>
        }
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatientLinkageComponent implements OnInit {
  readonly facade = inject(AcsCaregiverFacade);
  private readonly route = inject(ActivatedRoute);

  // POR QUE @Input() COM SETTER, E NÃO input() DE SINAL
  //
  // As duas entradas abaixo são a razão de este componente poder ser montado
  // fora da própria rota, e existe teste unitário para as duas. Sob ts-jest o
  // Angular compila em JIT, e em JIT uma entrada declarada por `input()` NÃO
  // chega a ser registrada: `componentRef.setInput()` estoura com NG0303 — a
  // limitação já está anotada em
  // `libs/edge/.../connectivity-chip.component.spec.ts:206`, onde o teste da
  // entrada foi simplesmente abandonado. Entrada não testável é entrada que
  // ninguém percebe quando para de funcionar. O decorador registra em JIT e em
  // AOT, e `ngComponentOutletInputs` — que é como o assistente as passa —
  // funciona igual nos dois.

  private readonly _pacienteIdEntrada = signal<string | null>(null);

  /**
   * Paciente a exibir, quando quem monta esta superfície é OUTRA página.
   *
   * <p>A rota /acs/pacientes/:pacienteId/vinculos continua sendo a fonte
   * normal. O assistente de configuração, porém, monta este componente dentro
   * de /onboarding, onde não há :pacienteId — ele passa o paciente ativo por
   * entrada. Entrada vence rota porque só quem compõe sabe de quem se trata.
   */
  @Input() set pacienteId(valor: string | null) {
    this._pacienteIdEntrada.set(valor);
  }

  private readonly _composto = signal(false);

  /** Monta sem cabeçalho nem contêiner próprios — ver a nota no template. */
  @Input() set composto(valor: boolean) {
    this._composto.set(valor);
  }

  /** Verdadeiro quando este componente é um pedaço de outra página. */
  readonly estaComposto = this._composto.asReadonly();

  private readonly pacienteIdRota = signal('');

  /** Paciente efetivo: o da entrada quando há; senão o do parâmetro de rota. */
  readonly pacienteAlvo = computed(() => this._pacienteIdEntrada() ?? this.pacienteIdRota());

  /** Derived coverage for the current patient — reacts to facade's coverageMap updates. */
  readonly coverage = computed(() => this.facade.coverageFor(this.pacienteAlvo()));

  /** Active caregivers already linked to this patient. */
  readonly linkedCaregivers = computed(() =>
    this.facade.activeCaregivers().filter((c) => this.coverage().coveredBy.includes(c.id)),
  );

  /** Active caregivers not yet linked to this patient. */
  readonly unlinkedCaregivers = computed(() =>
    this.facade.activeCaregivers().filter((c) => !this.coverage().coveredBy.includes(c.id)),
  );

  /** cuidadorId pending revoke confirmation — avoids browser confirm(). */
  readonly pendingRevokeId = signal<string | null>(null);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('pacienteId') ?? '';
    this.pacienteIdRota.set(id);
    void this.facade.loadRoster();
  }

  async link(cuidadorId: string): Promise<void> {
    await this.facade.linkToPatient(cuidadorId, this.pacienteAlvo());
  }

  /** First click — shows inline confirmation. */
  requestRevoke(cuidadorId: string): void {
    this.pendingRevokeId.set(cuidadorId);
  }

  cancelRevoke(): void {
    this.pendingRevokeId.set(null);
  }

  /** Second click — performs actual revoke after obtaining linkId from facade. */
  async confirmRevoke(cuidadorId: string): Promise<void> {
    const pid = this.pacienteAlvo();
    const linkId = this.facade.linkIdFor(cuidadorId, pid);
    if (linkId) {
      await this.facade.revokeLink(cuidadorId, linkId, pid);
    }
    this.pendingRevokeId.set(null);
  }
}
