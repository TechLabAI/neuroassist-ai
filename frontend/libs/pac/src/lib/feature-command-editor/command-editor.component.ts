import {
  Component,
  ChangeDetectionStrategy,
  inject,
  computed,
  OnInit,
  signal,
  Injector,
  effect,
  untracked,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { PacComandoFacade, EditorVM } from '../pac-comando.facade';
import { TiposGestoReadFacade } from '../tipos-gesto-read.facade';
import { PacPacienteFacade } from '../pac-paciente.facade';
import { PacDsBadgeComponent } from '../ui/ds-badge.component';
import { PacDsCardComponent } from '../ui/ds-card.component';
import { PacDsButtonComponent } from '../ui/ds-button.component';
import { PacDsInputComponent } from '../ui/ds-input.component';
import { ComandoSensibilidade, ComandoUrgencia } from '../api/comando.port';
import { PAC_ACCESS_DECISION } from '../access/pac-access-tokens';
import { ModalService } from '@neuroassist/shared/ui-feedback';

// ---------------------------------------------------------------------------
// CommandEditorComponent — SPEC-02 §2, promoted from calibracao.component.ts
//
// ADR-005 (command-edit-not-live-until-synced):
//   - After save, editor shows "salvo — pendente de sincronização" (never "ativo").
//   - The live-test slot (ng-content) hosts GestureLiveTestComponent at the
//     SHELL page level — PAC NEVER imports scope:gest (nx-boundary invariant).
//
// ADR-006 (no DELETE / retire-not-delete):
//   - "Remover" → "Aposentar" — terminal transition via PacComandoFacade.retire()
//
// Emergency gate (SPEC-07, PAC-FE-021):
//   - 422 emergency-not-detectable → emergencyGateError signal → blocking modal.
//   - The modal is non-dismissible until the user changes urgency to 'rotina'.
// ---------------------------------------------------------------------------

interface SensibilidadeOption {
  value: ComandoSensibilidade;
  label: string;
  hint: string;
}

const SENSIBILIDADES: SensibilidadeOption[] = [
  { value: 'baixa', label: 'Baixa', hint: 'precisa de um gesto bem claro' },
  { value: 'media', label: 'Média', hint: 'equilíbrio recomendado' },
  { value: 'alta',  label: 'Alta',  hint: 'detecta o menor sinal' },
];

type NovoTipoAlvo = 'principal' | 'secundario' | null;

@Component({
  selector: 'pac-command-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    PacDsBadgeComponent,
    PacDsCardComponent,
    PacDsButtonComponent,
    PacDsInputComponent,
  ],
  template: `
    <div class="mx-auto max-w-6xl p-8">
      <header class="mb-6">
        <h1 class="font-display text-2xl font-bold tracking-tight text-ink">
          {{ isNew() ? 'Novo comando' : 'Editar comando' }}
        </h1>
        <p class="mt-1 text-sm text-ink-soft">
          O rótulo e o gesto definem o comando; sensibilidade e urgência calibram como ele é reconhecido e notificado.
        </p>
      </header>

      <!-- Emergency gate blocking modal (ADR-005 / PAC-FE-021 — non-dismissible) -->
      @if (emergencyGateError()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
          <div class="mx-4 max-w-md rounded-xl bg-surface p-6 shadow-xl">
            <div class="flex items-center gap-3 text-alarm">
              <span class="material-symbols-outlined text-[28px]">emergency</span>
              <h2 class="font-display text-lg font-bold">Gesto não detectável</h2>
            </div>
            <p class="mt-3 text-sm text-ink-soft">{{ emergencyGateError() }}</p>
            <p class="mt-2 text-xs text-ink-faint">
              Para continuar, altere a urgência para <strong>Rotina</strong> ou escolha um gesto reconhecível pelo motor de detecção.
            </p>
            <div class="mt-5 flex gap-3">
              <pac-ds-button variant="secondary" (click)="changeUrgencyToRotina()">
                Alterar para Rotina
              </pac-ds-button>
            </div>
          </div>
        </div>
      }

      <div class="grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
        <!-- Coluna principal: formulário -->
        <pac-ds-card>
          <label class="mb-1 block text-xs font-medium text-ink-soft">Rótulo (a palavra que a família usa)</label>
          <pac-ds-input
            placeholder='Ex.: "água", "Sapo"...'
            [value]="editor().rotulo"
            (valueChange)="patchRotulo($event)"
            icon="edit" />
          <p class="mt-1.5 text-xs text-ink-faint">
            Texto livre — o sistema não interpreta o significado, só armazena e exibe.
          </p>

          <div class="mt-5">
            <label class="mb-2 block text-xs font-medium text-ink-soft">Urgência</label>
            <div class="flex items-center gap-2">
              <button
                (click)="setUrgencia('emergencia')"
                class="flex-1 rounded-lg border-[1.5px] py-2.5 text-center text-sm font-semibold transition-colors"
                [class]="editor().urgencia === 'emergencia' ? 'border-alarm bg-alarm-bg text-alarm' : 'border-line text-ink-soft hover:bg-surface-2'">
                Emergência
              </button>
              <button
                (click)="setUrgencia('rotina')"
                class="flex-1 rounded-lg border-[1.5px] py-2.5 text-center text-sm font-semibold transition-colors"
                [class]="editor().urgencia === 'rotina' ? 'border-brand bg-brand-subtle text-brand' : 'border-line text-ink-soft hover:bg-surface-2'">
                Rotina
              </button>
            </div>
            <p class="mt-1.5 text-xs text-ink-faint">
              @if (editor().urgencia === 'emergencia') {
                Dispara o alarme em tela cheia — exige confirmação imediata da cuidadora.
              } @else {
                Aparece como um aviso discreto, sem interromper a tela — fica registrado no histórico.
              }
            </p>
          </div>

          <div class="mt-5 grid grid-cols-1 gap-4 md:grid-cols-2">
            <!-- Gesto principal -->
            <div>
              <label class="mb-1 block text-xs font-medium text-ink-soft">Gesto principal</label>
              @if (novoTipoAberto() === 'principal') {
                <div class="flex items-center gap-2">
                  <input
                    [(ngModel)]="novoTipoNome"
                    placeholder='Ex.: "Leitura labial"'
                    class="h-9 w-full rounded-md border-[1.5px] border-field-line bg-field px-3 font-body text-sm text-ink outline-none focus:border-brand" />
                  <button
                    (click)="confirmarNovoTipo('principal')"
                    [disabled]="!novoTipoNome.trim()"
                    class="flex-shrink-0 text-xs font-semibold text-brand hover:text-brand-strong disabled:opacity-40">
                    salvar
                  </button>
                  <button (click)="novoTipoAberto.set(null)" class="flex-shrink-0 text-xs font-semibold text-ink-soft hover:text-ink">×</button>
                </div>
              } @else {
                <div class="flex items-center gap-2">
                  <select
                    [ngModel]="editor().primitivoPrincipal"
                    (ngModelChange)="patchPrimitivoPrincipal($event)"
                    class="h-9 w-full rounded-md border-[1.5px] border-field-line bg-field px-3 font-body text-sm text-ink outline-none focus:border-brand">
                    <!--
                      Movimento sem motor entra DESABILITADO, dizendo por que.
                      Antes vinha na lista indistinguível dos detectáveis, e
                      escolhê-lo produzia um comando que nunca dispara — coisa
                      que a pessoa só descobriria no teste, ou pior, no uso.
                    -->
                    @for (t of tipos(); track t.id) {
                      <option [value]="t.id" [disabled]="!t.detectavel">
                        {{ t.nome }}@if (!t.detectavel) { — {{ t.motivoIndisponivel }} }
                      </option>
                    }
                  </select>
                  <button
                    (click)="novoTipoAberto.set('principal')"
                    title="Cadastrar novo tipo de gesto"
                    class="grid size-9 flex-shrink-0 place-items-center rounded-md border-[1.5px] border-dashed border-line text-ink-soft hover:bg-brand-subtle hover:text-brand">
                    <span class="material-symbols-outlined text-[18px]">add</span>
                  </button>
                </div>
              }
            </div>

            <!-- Gesto secundário (opcional) -->
            <div>
              @if (novoTipoAberto() === 'secundario') {
                <label class="mb-1 block text-xs font-medium text-ink-soft">Novo tipo (segundo gesto)</label>
                <div class="flex items-center gap-2">
                  <input
                    [(ngModel)]="novoTipoNome"
                    placeholder='Ex.: "Leitura labial"'
                    class="h-9 w-full rounded-md border-[1.5px] border-field-line bg-field px-3 font-body text-sm text-ink outline-none focus:border-brand" />
                  <button
                    (click)="confirmarNovoTipo('secundario')"
                    [disabled]="!novoTipoNome.trim()"
                    class="flex-shrink-0 text-xs font-semibold text-brand hover:text-brand-strong disabled:opacity-40">
                    salvar
                  </button>
                  <button (click)="novoTipoAberto.set(null)" class="flex-shrink-0 text-xs font-semibold text-ink-soft hover:text-ink">×</button>
                </div>
              } @else if (editor().primitivoSecundario === null) {
                <label class="mb-1 block text-xs font-medium text-ink-soft">&nbsp;</label>
                <button
                  (click)="addSecundario()"
                  class="flex h-9 w-full items-center justify-center rounded-md border-[1.5px] border-dashed border-line text-xs font-semibold text-brand hover:bg-brand-subtle">
                  + Adicionar segundo gesto (combinação)
                </button>
              } @else {
                <label class="mb-1 block text-xs font-medium text-ink-soft">Segundo gesto (combinação)</label>
                <div class="flex items-center gap-2">
                  <select
                    [ngModel]="editor().primitivoSecundario"
                    (ngModelChange)="patchPrimitivoSecundario($event)"
                    class="h-9 w-full rounded-md border-[1.5px] border-field-line bg-field px-3 font-body text-sm text-ink outline-none focus:border-brand">
                    <!--
                      Movimento sem motor entra DESABILITADO, dizendo por que.
                      Antes vinha na lista indistinguível dos detectáveis, e
                      escolhê-lo produzia um comando que nunca dispara — coisa
                      que a pessoa só descobriria no teste, ou pior, no uso.
                    -->
                    @for (t of tipos(); track t.id) {
                      <option [value]="t.id" [disabled]="!t.detectavel">
                        {{ t.nome }}@if (!t.detectavel) { — {{ t.motivoIndisponivel }} }
                      </option>
                    }
                  </select>
                  <button
                    (click)="novoTipoAberto.set('secundario')"
                    title="Cadastrar novo tipo de gesto"
                    class="grid size-9 flex-shrink-0 place-items-center rounded-md border-[1.5px] border-dashed border-line text-ink-soft hover:bg-brand-subtle hover:text-brand">
                    <span class="material-symbols-outlined text-[18px]">add</span>
                  </button>
                  <button
                    (click)="patchPrimitivoSecundario(null)"
                    class="flex-shrink-0 text-xs font-semibold text-danger hover:text-danger">
                    remover
                  </button>
                </div>
              }
            </div>
          </div>
          <p class="mt-2 text-xs text-ink-faint">
            Não achou o gesto na lista? Use o <span class="material-symbols-outlined align-middle text-[13px]">add</span> para cadastrar um tipo novo — o catálogo não é fechado.
          </p>

          <!-- Sensibilidade -->
          <div class="mt-5">
            <label class="mb-2 block text-xs font-medium text-ink-soft">Sensibilidade</label>
            <div class="flex items-center gap-2">
              @for (s of sensibilidades; track s.value) {
                <button
                  (click)="setSensibilidade(s.value)"
                  class="flex-1 rounded-lg border-[1.5px] py-2.5 text-center transition-colors"
                  [class]="editor().sensibilidade === s.value ? 'border-brand bg-brand-subtle' : 'border-line hover:bg-surface-2'">
                  <div class="text-sm font-semibold" [class]="editor().sensibilidade === s.value ? 'text-brand' : 'text-ink'">{{ s.label }}</div>
                </button>
              }
            </div>
            <p class="mt-2 text-center text-xs text-ink-faint">{{ sensibilidadeHint() }}</p>
          </div>

          <!-- Slot para GestureLiveTestComponent (scope:web compõe aqui — PAC nunca importa scope:gest) -->
          <div class="mt-5">
            <ng-content select="[slot=live-test]" />
          </div>
        </pac-ds-card>

        <!-- Coluna lateral: pré-visualização + ações -->
        <div class="flex flex-col gap-6">
          <pac-ds-card>
            <h3 class="mb-3 font-display text-sm font-semibold text-ink">Pré-visualização</h3>
            <div class="flex items-center gap-2">
              <span class="text-sm font-semibold text-ink">"{{ editor().rotulo || '—' }}"</span>
              <pac-ds-badge [tone]="editor().urgencia === 'emergencia' ? 'er' : 'nf'">
                {{ editor().urgencia }}
              </pac-ds-badge>
            </div>
            <p class="mt-2 text-xs text-ink-faint">{{ gestoDescricao() }}</p>
            <div class="mt-2.5 flex items-center justify-between text-xs">
              <span class="text-ink-soft">Sensibilidade</span>
              <span class="font-medium text-ink">{{ editor().sensibilidade }}</span>
            </div>
            <!-- ADR-005: honest save state -->
            @if (editor().saveState === 'saved-pending-sync') {
              <div class="mt-2 flex items-center gap-1.5 text-xs font-medium text-warning-ink">
                <span class="material-symbols-outlined text-[14px]">sync_problem</span>
                Salvo — pendente de sincronização com o dispositivo
              </div>
            }
          </pac-ds-card>

          <pac-ds-card>
            <p class="text-xs text-ink-soft">
              Salve para adicionar ao catálogo — as mudanças só têm efeito no dispositivo após sincronização.
            </p>
            <div class="mt-4 flex flex-col gap-2">
              <pac-ds-button
                [disabled]="!editor().rotulo.trim() || editor().saveState === 'saving'"
                (click)="onSave()">
                @if (editor().saveState === 'saving') {
                  <span class="material-symbols-outlined animate-spin text-[16px]">progress_activity</span>
                  Salvando…
                } @else {
                  Salvar comando
                }
              </pac-ds-button>
              <pac-ds-button variant="ghost" (click)="onCancel()">Cancelar</pac-ds-button>
            </div>
            @if (editor().saveState === 'error' && !emergencyGateError()) {
              <p class="mt-2 text-xs text-danger">Erro ao salvar. Tente novamente.</p>
            }
          </pac-ds-card>

          <!-- Ciclo de vida (ADR-006 / SPEC-03) ─────────────────────────────
               O clique vai no HOST pac-ds-button, não num output: o componente
               não expõe evento de clique, e o botão interno com [disabled] não
               deixa o evento subir. É essa a propriedade que torna a
               desabilitação real e não decorativa. -->
          <pac-ds-card>
            <h3 class="mb-1 font-display text-sm font-semibold text-ink">Ciclo de vida</h3>
            <p class="text-xs text-ink-soft">{{ cicloDeVidaLegenda() }}</p>
            <div class="mt-4 flex flex-col gap-2">
              <pac-ds-button
                variant="secondary"
                [disabled]="!podeValidar()"
                (click)="onValidar()">
                Validar
              </pac-ds-button>
              <pac-ds-button
                [disabled]="!podePublicar()"
                (click)="onPublicar()">
                Publicar
              </pac-ds-button>
              <pac-ds-button
                variant="secondary"
                [disabled]="!podeRecalibrar()"
                (click)="onRecalibrar()">
                Recalibrar
              </pac-ds-button>
              <pac-ds-button
                variant="danger"
                [disabled]="!podeAposentar()"
                (click)="onAposentar()">
                Aposentar
              </pac-ds-button>
            </div>
            @if (!podeGerirCiclo()) {
              <p class="mt-2 text-xs text-ink-faint">
                Seu perfil não permite mudar o estado de um comando.
              </p>
            }
          </pac-ds-card>
        </div>
      </div>
    </div>
  `,
})
export class CommandEditorComponent implements OnInit {
  private readonly comandoFacade = inject(PacComandoFacade);
  private readonly tiposFacade = inject(TiposGestoReadFacade);
  private readonly pacienteFacade = inject(PacPacienteFacade);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  // Mesmo padrão de command-catalog.component.ts:180 — token ausente (lib
  // montada isolada em teste) deixa passar. No aplicativo real quem providencia
  // é app.config.ts, traduzindo pelo PAC_PERMISSION_MAP; ver pac-access-tokens.ts.
  private readonly acesso = inject(PAC_ACCESS_DECISION, { optional: true });
  private readonly modais = inject(ModalService);

  protected readonly sensibilidades = SENSIBILIDADES;
  protected readonly novoTipoAberto = signal<NovoTipoAlvo>(null);
  protected novoTipoNome = '';

  readonly editor = this.comandoFacade.editor;
  readonly tipos = this.tiposFacade.tipos;
  readonly emergencyGateError = computed(() => this.editor().emergencyGateError);
  readonly isNew = computed(() => this.editor().cid === null);

  // ─── Ciclo de vida: quem pode, e quando ───────────────────────────────────
  //
  // Duas condições independentes, e a distinção importa:
  //
  //   PERMISSÃO — de quem está logado. SPEC-05: "WHILE the caller lacks the
  //   comando publish permission, THE SYSTEM SHALL disable the validar publicar
  //   recalibrar and retirar lifecycle actions". Uma única permissão governa as
  //   quatro; recalibrar e aposentar exigem ainda a sua própria.
  //
  //   ESTADO — do comando (SPEC-03). Uma coordenadora com todas as permissões
  //   continua sem poder publicar um rascunho: ele precisa ser validado antes.
  //
  // `estado === null` é comando novo ainda não gravado: não há o que transicionar.
  // `saveState === 'saving'` desabilita tudo enquanto uma chamada está no ar —
  // duas transições concorrentes partem do mesmo If-Match e a segunda volta 409.

  private readonly pode = (chave: string): boolean =>
    this.acesso === null || this.acesso.can(chave);

  /** Permissão-guarda-chuva das quatro ações (SPEC-05). */
  readonly podeGerirCiclo = computed(() => this.pode('commandPublish'));

  private readonly ocupado = computed(
    () => this.editor().saveState === 'saving',
  );

  readonly podeValidar = computed(
    () => this.podeGerirCiclo() && !this.ocupado() && this.editor().estado === 'drafted',
  );

  readonly podePublicar = computed(
    () => this.podeGerirCiclo() && !this.ocupado() && this.editor().estado === 'validated',
  );

  readonly podeRecalibrar = computed(
    () =>
      this.podeGerirCiclo() &&
      this.pode('commandRecalibrate') &&
      !this.ocupado() &&
      this.editor().estado === 'published',
  );

  /** Aposentar cabe em qualquer estado vivo — 'retired' é terminal (ADR-006). */
  readonly podeAposentar = computed(() => {
    const estado = this.editor().estado;
    return (
      this.podeGerirCiclo() &&
      this.pode('commandRetire') &&
      !this.ocupado() &&
      estado !== null &&
      estado !== 'retired'
    );
  });

  protected cicloDeVidaLegenda(): string {
    switch (this.editor().estado) {
      case 'drafted':
        return 'Rascunho — valide para conferir que o gesto é reconhecível.';
      case 'validated':
        return 'Validado — publique para que o dispositivo passe a reconhecê-lo.';
      case 'published':
        return 'Publicado — ajustes de sensibilidade e tempo entram por recalibrar.';
      case 'retired':
        return 'Aposentado — estado terminal, o comando fica no histórico.';
      default:
        return 'Salve o comando para poder validá-lo e publicá-lo.';
    }
  }

  ngOnInit(): void {
    this.tiposFacade.load();
    const cid = this.route.snapshot.paramMap.get('comandoId');
    const pid = this.pacienteFacade.patient()?.id ?? '';

    // Reset any stale save state from a previous session so the save-navigation
    // effect (below) does not fire immediately on mount.
    this.comandoFacade.cancelEdit();

    if (cid) {
      // Edit mode: the catalog may be empty when navigating directly to
      // /pac/comandos/:id/editar (e.g. fresh page load bypassing the catalog).
      // Always load the catalog; the effect below opens the editor reactively
      // once the target comando is available.
      if (this.comandoFacade.loadState() === 'idle') {
        this.comandoFacade.load(pid);
      }

      // One-shot reactive open: calls openEdit() once when the catalog has the
      // target comando. Destroyed immediately after the first successful match.
      //
      // O ramo `else` é o que faltava: com um comandoId que não existe, o
      // efeito simplesmente nunca casava e a tela ficava para sempre em "Editar
      // comando" com o formulário em branco — indistinguível de um comando de
      // verdade cujo carregamento demorou. Uma URL inválida tem de ter destino;
      // aqui o destino é o catálogo, que é o pai da rota.
      //
      // Só decide DEPOIS de `loadState() === 'loaded'`: enquanto está 'loading'
      // ou 'idle' a ausência do comando não significa nada.
      const stopEditInit = effect(() => {
        const carregado = this.comandoFacade.loadState() === 'loaded';
        const row = this.comandoFacade.catalog().find((r) => r.id === cid);
        if (row) {
          untracked(() => {
            this.comandoFacade.openEdit(pid, row);
            stopEditInit.destroy();
          });
        } else if (carregado) {
          untracked(() => {
            stopEditInit.destroy();
            this.router.navigate(['/pac']);
          });
        }
      }, { injector: this.injector });
    } else {
      // New command
      this.comandoFacade.openNew(pid, this.tiposFacade.firstTipoId());
    }

    // Navigate to catalog on successful save (ADR-005 save-then-navigate).
    // Must NOT navigate immediately on mount (saveState is 'idle' after cancelEdit above).
    // On error (422 emergency gate), saveState becomes 'error' — we stay on the editor
    // so the blocking modal is visible.
    effect(() => {
      if (this.editor().saveState === 'saved-pending-sync') {
        untracked(() => this.router.navigate(['..'], { relativeTo: this.route }));
      }
    }, { injector: this.injector });
  }

  protected gestoDescricao(): string {
    const ed = this.editor();
    const getById = this.tiposFacade.getById();
    const principal = getById(ed.primitivoPrincipal);
    return ed.primitivoSecundario ? `${principal} + ${getById(ed.primitivoSecundario)}` : principal;
  }

  protected sensibilidadeHint(): string {
    return (
      SENSIBILIDADES.find((s) => s.value === this.editor().sensibilidade)?.hint ?? ''
    );
  }

  protected patchRotulo(val: string): void {
    this.comandoFacade.patchEditor({ rotulo: val });
  }

  protected setUrgencia(u: ComandoUrgencia): void {
    this.comandoFacade.patchEditor({ urgencia: u, emergencyGateError: null });
  }

  protected setSensibilidade(s: ComandoSensibilidade): void {
    this.comandoFacade.patchEditor({ sensibilidade: s });
  }

  protected patchPrimitivoPrincipal(id: string): void {
    this.comandoFacade.patchEditor({ primitivoPrincipal: id });
  }

  protected patchPrimitivoSecundario(id: string | null): void {
    this.comandoFacade.patchEditor({ primitivoSecundario: id });
  }

  protected addSecundario(): void {
    const tipos = this.tiposFacade.tipos();
    const principal = this.editor().primitivoPrincipal;
    const next = tipos.find((t) => t.id !== principal)?.id ?? tipos[0]?.id ?? '';
    this.patchPrimitivoSecundario(next);
  }

  protected confirmarNovoTipo(alvo: NovoTipoAlvo): void {
    const nome = this.novoTipoNome.trim();
    if (!nome) return;
    this.tiposFacade.addNew(nome);
    // After addNew, the new tipo will appear in tipos() reactively;
    // select it once available by matching name
    const newId = this.tiposFacade.findByNome(nome);
    if (alvo === 'principal' && newId) this.patchPrimitivoPrincipal(newId);
    if (alvo === 'secundario' && newId) this.patchPrimitivoSecundario(newId);
    this.novoTipoNome = '';
    this.novoTipoAberto.set(null);
  }

  protected changeUrgencyToRotina(): void {
    this.setUrgencia('rotina');
  }

  protected onSave(): void {
    this.comandoFacade.save();
    // Navigation is handled reactively by the effect in ngOnInit() watching
    // editor().saveState. When save succeeds → 'saved-pending-sync' → navigate to '..'.
    // When save fails with 422 emergency-not-detectable → 'error' → stay on editor,
    // blocking modal appears via emergencyGateError() signal.
  }

  // ─── Ações de ciclo de vida ───────────────────────────────────────────────
  //
  // SOBRE A CONFIRMAÇÃO, E POR QUE ELA NÃO É `bloqueante`
  //
  // O `ModalOutletComponent` desenha UM botão — o da `acao`. Com
  // `bloqueante: true` não há Esc nem clique fora, e então o único caminho para
  // fora da caixa é o botão que CONFIRMA: seria uma "confirmação" que não sabe
  // dizer não. Por isso estas duas são não-bloqueantes — Esc e o clique fora são
  // o "cancelar", e o botão é o "sim".
  //
  // O modal bloqueante do módulo continua sendo um só: o do portão de
  // emergência, que aparece DEPOIS de o backend já ter recusado a publicação.
  // Ali não há decisão a tomar, só um caminho de saída.

  protected onValidar(): void {
    this.comandoFacade.validate();
  }

  /**
   * Publicar. Rotina vai direto; emergência confirma antes.
   *
   * <p>Publicar um comando de emergência é o que faz um gesto passar a disparar
   * alarme em tela cheia na casa de alguém. Rotina não tem esse peso — pedir
   * confirmação nas duas ensinaria a clicar "sim" sem ler, que é como se perde a
   * confirmação que importa.
   */
  protected onPublicar(): void {
    if (this.editor().urgencia !== 'emergencia') {
      this.comandoFacade.publish();
      return;
    }
    // `confirmar()` é bloqueante E oferece a recusa. A primeira versão usava
    // `abrir({bloqueante:false})` porque o modal só sabia desenhar UM botão — e
    // um bloqueante de botão único é uma confirmação incapaz de dizer não, com o
    // clique fora servindo de cancelar. O serviço passou a suportar as duas
    // saídas, então a decisão volta a ser deliberada: sai por um dos dois botões,
    // e não por um clique distraído fora do quadro.
    this.modais.confirmar({
      titulo: 'Publicar comando de emergência',
      mensagem:
        `Ao publicar, o gesto de "${this.editor().rotulo}" passa a disparar o alarme em tela cheia ` +
        'e exigir confirmação imediata da cuidadora. Confirma a publicação?',
      rotuloConfirmar: 'Publicar emergência',
      aoConfirmar: () => this.comandoFacade.publish(),
    });
  }

  protected onRecalibrar(): void {
    this.comandoFacade.recalibrateCurrent();
  }

  /** Aposentar sempre confirma: é terminal e não há DELETE nem volta (ADR-006). */
  protected onAposentar(): void {
    this.modais.confirmar({
      titulo: 'Aposentar comando',
      mensagem:
        `"${this.editor().rotulo}" deixará de ser reconhecido pelo dispositivo. ` +
        'Aposentar é definitivo — o comando fica no histórico, mas não volta ao catálogo ativo.',
      rotuloConfirmar: 'Aposentar',
    aoConfirmar: () => this.comandoFacade.retireCurrent(),
    });
  }

  protected onCancel(): void {
    this.comandoFacade.cancelEdit();
    this.router.navigate(['..'], { relativeTo: this.route });
  }
}
