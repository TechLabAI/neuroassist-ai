import {
  Component,
  ChangeDetectionStrategy,
  Input,
  OnInit,
  computed,
  effect,
  inject,
  signal,
  output,
} from '@angular/core';
import { PacComandoFacade } from '../pac-comando.facade';
import { TiposGestoReadFacade } from '../tipos-gesto-read.facade';
import { PacDsButtonComponent } from '../ui/ds-button.component';
import { PacDsInputComponent } from '../ui/ds-input.component';
import { PAC_ACCESS_DECISION } from '../access/pac-access-tokens';

// =============================================================================
// FirstCommandStepComponent — o passo "Primeiro comando" do assistente.
//
// POR QUE ELE PRECISOU NASCER, em vez de compor uma superfície existente.
//
// O passo era um `<a routerLink="/pac">` escrito no assistente: ele não FAZIA a
// coisa, MANDAVA A PESSOA EMBORA. E mandava para um lugar de onde não há volta
// guiada — quem sai do assistente no meio perde o progresso visível e o passo
// seguinte. Um assistente que interrompe o próprio fluxo perde a pessoa.
//
// As duas superfícies que o PAC já tinha não serviam:
//   - `CommandCatalogComponent` é uma LISTA — não cria nada;
//   - `CommandEditorComponent` é uma PÁGINA inteira: lê `:comandoId` da rota,
//     navega ao salvar, e traz o gate de emergência num modal em tela cheia.
//     Composta dentro do assistente, ela reintroduziria a mesma ejeção.
//
// Então este componente é o caminho (b): pequeno, dentro do `libs/pac`, com o
// mínimo para criar UM comando — rótulo e gesto. O resto da calibração
// (sensibilidade, urgência, tempos, ciclo de vida) fica no editor completo, e o
// passo diz isso em vez de fingir que cobre tudo.
//
// LIMITAÇÃO ESCRITA, para não passar por resolvida: o comando nasce RASCUNHO,
// com urgência 'rotina' e sensibilidade 'média' (os padrões do editor). Validar
// e publicar continuam no catálogo — este passo entrega o primeiro comando
// existindo, não o ciclo de vida inteiro.
// =============================================================================

@Component({
  selector: 'pac-first-command-step',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PacDsButtonComponent, PacDsInputComponent],
  template: `
    @if (!pacienteId()) {
      <!-- Sem paciente não há a quem pertencer o comando. Dizer isso é melhor do
           que desenhar um formulário que vai falhar na escrita. -->
      <p class="text-sm text-ink-soft" data-testid="primeiro-comando-sem-paciente">
        Registre o paciente antes de configurar o primeiro comando.
      </p>
    } @else if (jaTemComando()) {
      <div class="flex items-center gap-2 text-sm text-success-ink" data-testid="primeiro-comando-pronto">
        <span class="material-symbols-outlined text-[18px]">check_circle</span>
        Catálogo com {{ comandoFacade.catalog().length }} comando(s) — o primeiro já existe.
      </div>
    } @else {
      <p class="mb-3 text-sm text-ink-soft">
        Um comando liga um gesto a uma necessidade. Crie o primeiro aqui; a calibração fina fica no catálogo.
      </p>

      <div class="flex flex-col gap-3 sm:flex-row">
        <div class="flex-1">
          <pac-ds-input
            placeholder="O que a pessoa está pedindo (ex.: Água)"
            [value]="rotulo()"
            (valueChange)="rotulo.set($event)"
            [disabled]="!podeEscrever()"
            icon="label" />
        </div>
        <div class="flex-1">
          <select
            class="h-9 w-full rounded-md border-[1.5px] border-field-line bg-field px-3 font-body text-sm text-ink outline-none focus:border-brand disabled:opacity-45"
            [value]="tipoSelecionado()"
            (change)="escolherTipo($any($event.target).value)"
            [disabled]="!podeEscrever() || tiposFacade.tipos().length === 0"
            data-testid="primeiro-comando-gesto"
            aria-label="Gesto que aciona o comando"
          >
            @for (tipo of tiposFacade.tipos(); track tipo.id) {
              <option [value]="tipo.id">{{ tipo.nome }}</option>
            }
          </select>
        </div>
      </div>

      <div class="mt-4">
        <pac-ds-button
          [disabled]="!podeSalvar()"
          (click)="criar()">
          @if (salvando()) {
            <span class="material-symbols-outlined animate-spin text-[16px]">progress_activity</span>
            Salvando…
          } @else {
            <!-- "Salvar comando", e nao "Criar comando": o assistente ja tem um
                 botao "Criar" (o da empresa, no contexto do topo), e dois botoes
                 cujo nome acessivel casa com /criar/i na mesma pagina quebram
                 qualquer localizador por papel+nome no e2e. O verbo do PAC para
                 escrita de comando e "Salvar" (ver o editor completo). -->
            Salvar comando
          }
        </pac-ds-button>
      </div>

      @if (!podeEscrever()) {
        <p class="mt-2 text-xs text-ink-soft" data-testid="primeiro-comando-negado">
          Criar comando é da coordenadora — sua conta não tem essa permissão.
        </p>
      }

      @if (erro()) {
        <p class="mt-2 text-xs text-danger" role="alert" data-testid="primeiro-comando-erro">{{ erro() }}</p>
      }

      @if (salvo()) {
        <p class="mt-3 flex items-center gap-1.5 text-sm font-medium text-success-ink" data-testid="primeiro-comando-salvo">
          <span class="material-symbols-outlined text-[18px]">check_circle</span>
          Comando criado — rascunho, pendente de sincronização com o dispositivo.
        </p>
      }
    }
  `,
})
export class FirstCommandStepComponent implements OnInit {
  protected readonly comandoFacade = inject(PacComandoFacade);
  protected readonly tiposFacade = inject(TiposGestoReadFacade);

  /**
   * Decisão de acesso do PAC. Opcional e, na ausência, NEGA — a mesma regra da
   * criação de empresa no assistente: um ambiente que esqueceu de ligar a
   * decisão não deve ganhar a capacidade de presente.
   */
  private readonly access = inject(PAC_ACCESS_DECISION, { optional: true });

  /**
   * O paciente vem do CONTEXTO do assistente. `Input` com setter para signal
   * porque quem passa é o `NgComponentOutlet`, via `setInput()`.
   */
  @Input() set pacienteHint(valor: string | null) {
    this._pacienteId.set(valor);
  }
  private readonly _pacienteId = signal<string | null>(null);
  protected readonly pacienteId = this._pacienteId.asReadonly();

  /** Avisa o hospedeiro que o primeiro comando existe. */
  readonly criado = output<{ pacienteId: string; rotulo: string }>();

  /**
   * PUBLICO de proposito, junto com `tipoSelecionado`, `podeSalvar`,
   * `podeEscrever` e `criar()`.
   *
   * <p>Neste ambiente (ts-jest JIT) as entradas por `input()`/`model()` de um
   * componente de biblioteca NAO sao reconhecidas pelo template de quem o usa:
   * `[disabled]` e `[value]` sobre `pac-ds-button`/`pac-ds-input` caem em
   * NG0303 e simplesmente nao sao ligados. Ver a mesma nota em
   * `command-editor.component.spec.ts`. Asserção sobre esses atributos, aqui,
   * mede o ambiente e nao o componente — entao a superficie de teste e o estado,
   * e o DOM fica para o que ele de fato prova (qual ramo foi renderizado, que
   * texto a tela diz).
   */
  readonly rotulo = signal('');

  /**
   * O gesto escolhido, ou o primeiro da lista enquanto ninguém escolheu.
   *
   * <p>Separado em dois porque a lista chega DEPOIS da montagem: um signal
   * simples semeado no `ngOnInit` ficaria vazio para sempre se `load()` ainda
   * não tivesse respondido, e o botão nunca habilitaria.
   */
  private readonly tipoEscolhido = signal('');
  readonly tipoSelecionado = computed(
    () => this.tipoEscolhido() || this.tiposFacade.firstTipoId(),
  );

  protected readonly salvando = computed(() => this.comandoFacade.editor().saveState === 'saving');
  protected readonly salvo = computed(
    () => this.comandoFacade.editor().saveState === 'saved-pending-sync',
  );
  protected readonly erro = computed(() =>
    this.comandoFacade.editor().saveState === 'error'
      ? (this.comandoFacade.editor().emergencyGateError ??
        this.comandoFacade.error() ??
        'Erro ao criar o comando. Tente novamente.')
      : null,
  );

  /** O catálogo já tem comando — o passo está cumprido, com ou sem esta tela. */
  protected readonly jaTemComando = computed(() => this.comandoFacade.catalog().length > 0);

  /**
   * A CHAVE DE TELA, e nao a authority.
   *
   * <p>Convencao do PAC: a lib pergunta por 'commandWrite' e quem traduz para
   * 'pac.comando-authoring.criar' e o provedor em `app.config.ts`, pelo
   * PAC_PERMISSION_MAP. Importar o mapa aqui, alem de furar essa fronteira,
   * criava um CICLO de import (descriptor -> routes -> componentes -> ui) que
   * deixava `PacDsButtonComponent` indefinido no `imports` deste componente: o
   * template compilava com NG0303 e o `[disabled]` simplesmente nao era ligado —
   * um botao que parecia respeitar a permissao e nao respeitava.
   *
   * <p>Ausencia do provedor NEGA — ao contrario do resto do PAC, que ainda deixa
   * passar (`acesso === null || ...`, o bypass Wave-0). Criar comando escreve no
   * catalogo do paciente; um ambiente que esqueceu de ligar a decisao nao deve
   * ganhar a capacidade de presente.
   */
  readonly podeEscrever = computed(
    () => this.access?.can('commandWrite') ?? false,
  );

  readonly podeSalvar = computed(
    () =>
      this.podeEscrever() &&
      this.rotulo().trim().length > 0 &&
      this.tipoSelecionado().length > 0 &&
      !this.salvando(),
  );

  constructor() {
    // O aviso ao hospedeiro sai quando a ESCRITA CONFIRMA, não no clique: um
    // `emit` no clique anunciaria como feito algo que ainda pode falhar.
    effect(() => {
      if (!this.salvo()) return;
      const pid = this._pacienteId();
      if (pid) this.criado.emit({ pacienteId: pid, rotulo: this.rotulo().trim() });
    });
  }

  escolherTipo(id: string): void {
    this.tipoEscolhido.set(id);
  }

  ngOnInit(): void {
    this.tiposFacade.load();
    const pid = this._pacienteId();
    if (pid) this.comandoFacade.load(pid);
  }

  criar(): void {
    const pid = this._pacienteId();
    if (!pid || !this.podeSalvar()) return;

    // `openNew` semeia o editor com os padrões (urgência 'rotina', sensibilidade
    // 'média', tempos do backend) e o gesto escolhido; só o rótulo é nosso.
    // Urgência 'rotina' é deliberada: 'emergencia' aciona o gate de
    // detectabilidade (422), que é uma conversa do editor completo e não cabe
    // num passo de configuração inicial.
    this.comandoFacade.openNew(pid, this.tipoSelecionado());
    this.comandoFacade.patchEditor({ rotulo: this.rotulo().trim() });
    this.comandoFacade.save();
  }
}
