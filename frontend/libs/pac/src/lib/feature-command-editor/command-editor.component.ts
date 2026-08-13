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
                    @for (t of tipos(); track t.id) {
                      <option [value]="t.id">{{ t.nome }}</option>
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
                    @for (t of tipos(); track t.id) {
                      <option [value]="t.id">{{ t.nome }}</option>
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

  protected readonly sensibilidades = SENSIBILIDADES;
  protected readonly novoTipoAberto = signal<NovoTipoAlvo>(null);
  protected novoTipoNome = '';

  readonly editor = this.comandoFacade.editor;
  readonly tipos = this.tiposFacade.tipos;
  readonly emergencyGateError = computed(() => this.editor().emergencyGateError);
  readonly isNew = computed(() => this.editor().cid === null);

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
      const stopEditInit = effect(() => {
        const row = this.comandoFacade.catalog().find((r) => r.id === cid);
        if (row) {
          untracked(() => {
            this.comandoFacade.openEdit(pid, row);
            stopEditInit.destroy();
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

  protected onCancel(): void {
    this.comandoFacade.cancelEdit();
    this.router.navigate(['..'], { relativeTo: this.route });
  }
}
