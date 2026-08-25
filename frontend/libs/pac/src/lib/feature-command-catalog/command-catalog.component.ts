import {
  Component,
  ChangeDetectionStrategy,
  inject,
  computed,
  OnInit,
  output,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { PacComandoFacade, ComandoRowVM } from '../pac-comando.facade';
import { TiposGestoReadFacade } from '../tipos-gesto-read.facade';
import { PacPacienteFacade } from '../pac-paciente.facade';
import { PacDsBadgeComponent } from '../ui/ds-badge.component';
import { PacDsCardComponent } from '../ui/ds-card.component';
import { PacDsButtonComponent } from '../ui/ds-button.component';
import { PacDsKpiComponent } from '../ui/ds-kpi.component';
import { PAC_ACCESS_DECISION } from '../access/pac-access-tokens';

// ---------------------------------------------------------------------------
// CommandCatalogComponent — SPEC-02 §1, promoted from calibracao.component.ts
// Lists all active commands for the active patient; links to editor route.
//
// ADR-005 (command-edit-not-live-until-synced): every command shows syncState
// badge. In MVP, all commands are 'pendente de sincronização' (stub adapter).
// PAC never imports scope:gest — GestureLiveTestComponent is composed by
// the host shell page via ng-content slot in CommandEditorComponent.
//
// Nx boundary: scope:pac imports only scope:pac + scope:shared.
// ---------------------------------------------------------------------------

@Component({
  selector: 'pac-command-catalog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    PacDsBadgeComponent,
    PacDsCardComponent,
    PacDsButtonComponent,
    PacDsKpiComponent,
  ],
  template: `
    <div class="mx-auto max-w-6xl p-8">
      <header class="mb-7 flex items-center justify-between">
        <div>
          <h1 class="font-display text-2xl font-bold tracking-tight text-ink">
            Calibração — {{ patientName() }}
          </h1>
          <p class="mt-1 text-sm text-ink-soft">
            Catálogo de comandos: cada gesto mapeado a uma necessidade, calibrado individualmente.
          </p>
          <!-- ADR-005 honest-state banner (always visible in MVP — EDGE ack not yet contracted) -->
          <p class="mt-1 flex items-center gap-1.5 text-xs text-warning-ink">
            <span class="material-symbols-outlined text-[14px]">sync_problem</span>
            Pendente de sincronização com o dispositivo — os comandos serão ativados no próximo heartbeat.
          </p>
        </div>
        <!--
          Sem permissão de escrever comando o link NÃO é renderizado — não é
          escondido por CSS. Um item invisível mas presente no DOM continua
          navegável por teclado e por leitor de tela; "escondido" não é resposta,
          é aparência de resposta. Quem barra de verdade é o pacCommandWriteGuard
          na rota e o backend na chamada; isto aqui é ergonomia — não oferecer
          uma porta que vai bater na cara da pessoa.
        -->
        @if (podeEscreverComando()) {
          <a routerLink="comandos/novo">
            <pac-ds-button>
              <span class="material-symbols-outlined text-[18px]">add</span>
              Novo comando
            </pac-ds-button>
          </a>
        }
      </header>

      @if (loading()) {
        <p class="text-sm text-ink-faint">Carregando catálogo…</p>
      } @else if (error()) {
        <pac-ds-card>
          <p class="text-sm text-danger">{{ error() }}</p>
        </pac-ds-card>
      } @else {
        <div class="grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
          <!-- CommandList -->
          <div>
            @if (isEmpty()) {
              <pac-ds-card>
                <div class="flex flex-col items-center py-10 text-center">
                  <span class="material-symbols-outlined text-[40px] text-ink-faint">sentiment_neutral</span>
                  <p class="mt-3 text-sm text-ink-soft">Nenhum comando configurado ainda.</p>
                  @if (podeEscreverComando()) {
                    <a routerLink="comandos/novo" class="mt-4">
                      <pac-ds-button>Adicionar primeiro comando</pac-ds-button>
                    </a>
                  }
                </div>
              </pac-ds-card>
            } @else {
              <div class="flex flex-col gap-3">
                @for (c of catalog(); track c.id) {
                  <pac-ds-card>
                    <div class="flex items-center justify-between gap-4">
                      <div class="flex items-center gap-3">
                        <span
                          class="material-symbols-outlined text-[22px]"
                          [class]="c.urgencia === 'emergencia' ? 'text-alarm' : 'text-info'">
                          {{ c.urgencia === 'emergencia' ? 'emergency_home' : 'notifications' }}
                        </span>
                        <div>
                          <div class="flex items-center gap-2">
                            <span class="text-sm font-semibold text-ink">"{{ c.rotulo }}"</span>
                            <pac-ds-badge [tone]="c.urgencia === 'emergencia' ? 'er' : 'nf'">
                              {{ c.urgencia }}
                            </pac-ds-badge>
                            <!-- ADR-005: honest sync state per command -->
                            <pac-ds-badge tone="wn">
                              <span class="material-symbols-outlined text-[11px]">sync_problem</span>
                              pendente
                            </pac-ds-badge>
                          </div>
                          <p class="mt-0.5 text-xs text-ink-faint">
                            {{ resolveGesto(c) }} · sensibilidade {{ c.sensibilidade }}
                            @if (c.testedState === 'not-tested') {
                              · <span class="text-ink-faint">não testado</span>
                            } @else {
                              · <span class="text-success-ink">reconhecido ✓</span>
                            }
                          </p>
                        </div>
                      </div>
                      <div class="flex items-center gap-1">
                        <a
                          [routerLink]="['comandos', c.id, 'editar']"
                          class="grid size-8 place-items-center rounded-lg text-ink-soft transition-colors hover:bg-surface-2 hover:text-ink"
                          title="Editar">
                          <span class="material-symbols-outlined text-[18px]">edit</span>
                        </a>
                        <button
                          (click)="onRetire(c)"
                          class="grid size-8 place-items-center rounded-lg text-ink-soft transition-colors hover:bg-danger-bg hover:text-danger"
                          title="Aposentar (desativar sem deletar)">
                          <span class="material-symbols-outlined text-[18px]">archive</span>
                        </button>
                      </div>
                    </div>
                  </pac-ds-card>
                }
              </div>
            }
          </div>

          <!-- Coluna lateral: resumo -->
          <div class="flex flex-col gap-6">
            <div class="grid grid-cols-2 gap-4 lg:grid-cols-1">
              <pac-ds-kpi label="Comandos" [value]="catalog().length" kind="tint" />
              <pac-ds-kpi label="Emergência" [value]="totalEmergencia()" kind="tint" />
            </div>
            <pac-ds-card>
              <h3 class="mb-3 font-display text-sm font-semibold text-ink">Tipos de gesto</h3>
              <div class="flex flex-wrap gap-1.5">
                @for (t of tipos(); track t.id) {
                  <pac-ds-badge tone="mu">{{ t.nome }}</pac-ds-badge>
                }
              </div>
              <p class="mt-3 text-xs text-ink-soft">
                Cada comando é um gesto (simples ou combinado) mapeado a uma necessidade específica.
                O rótulo é a palavra que a família já usa. O catálogo de tipos de gesto é extensível.
              </p>
            </pac-ds-card>
          </div>
        </div>
      }
    </div>
  `,
})
export class CommandCatalogComponent implements OnInit {
  private readonly comandoFacade = inject(PacComandoFacade);
  private readonly tiposFacade = inject(TiposGestoReadFacade);
  private readonly pacienteFacade = inject(PacPacienteFacade);
  private readonly acesso = inject(PAC_ACCESS_DECISION, { optional: true });

  /**
   * Pode escrever comando? Token ausente (lib montada isolada em teste) deixa
   * passar — ver a nota em pac-access-tokens.ts. No aplicativo real o provider
   * de app.config.ts traduz 'commandWrite' pelo PAC_PERMISSION_MAP.
   */
  protected readonly podeEscreverComando = computed(
    () => this.acesso === null || this.acesso.can('commandWrite'),
  );

  /** Emitted when the caregiver initiates retire — parent handles confirmation modal */
  readonly retireRequested = output<ComandoRowVM>();

  readonly loading = computed(
    () => this.comandoFacade.loading() || this.tiposFacade.loading() || this.pacienteFacade.loading(),
  );
  readonly error = this.comandoFacade.error;
  readonly catalog = this.comandoFacade.catalog;
  readonly isEmpty = this.comandoFacade.isEmpty;
  readonly tipos = this.tiposFacade.tipos;
  readonly totalEmergencia = computed(
    () => this.catalog().filter((c) => c.urgencia === 'emergencia').length,
  );
  readonly patientName = computed(() => this.pacienteFacade.patient()?.nome ?? '—');

  ngOnInit(): void {
    const pid = this.pacienteFacade.patient()?.id;
    if (pid) {
      this.comandoFacade.load(pid);
      this.tiposFacade.load();
    }
  }

  protected resolveGesto(c: ComandoRowVM): string {
    const getById = this.tiposFacade.getById();
    const principal = getById(c.primitivoPrincipalNome);
    return c.primitivoSecundarioNome
      ? `${principal} + ${getById(c.primitivoSecundarioNome)}`
      : principal;
  }

  protected onRetire(row: ComandoRowVM): void {
    const pid = this.pacienteFacade.patient()?.id;
    if (!pid) return;
    // ADR-006: retire (no DELETE — history-preserving)
    this.comandoFacade.retire(pid, row);
  }
}
