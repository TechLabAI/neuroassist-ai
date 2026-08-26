import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  Input,
  OnInit,
  output,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { PacPacienteFacade } from '../pac-paciente.facade';
import { PacDsCardComponent } from '../ui/ds-card.component';
import { PacDsButtonComponent } from '../ui/ds-button.component';
import { PacDsInputComponent } from '../ui/ds-input.component';

// ---------------------------------------------------------------------------
// PatientRegisterComponent — SPEC-02 §3, promoted from onboarding.component.ts step 2
//
// PAC owns Paciente registration (authoring source — ADR-004).
// This component is NOT a routed screen — it is composed by ACS onboarding
// wizard (step 2 of the wizard) via ng-content or direct import at scope:web.
//
// Routes: none (embedded surface — generator-config.yml feature-patient-register.routes: [])
// ---------------------------------------------------------------------------

@Component({
  selector: 'pac-patient-register',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, PacDsCardComponent, PacDsButtonComponent, PacDsInputComponent],
  template: `
    <!--
      'composto' = esta superficie e um PEDACO de outra pagina (o passo do
      paciente no assistente de configuracao inicial), nao a pagina inteira.
      Mesma entrada, e pelo mesmo motivo, que o pareamento do EDGE e o vinculo
      do ACS ja tinham: quem desenha o cartao, o NUMERO do passo e o titulo e o
      hospedeiro. Sem isto o assistente ficava com dois cabecalhos "Paciente" e
      um cartao dentro do outro.

      O numero fixo "2" que morava aqui sumiu junto: numerar passo e decisao de
      quem monta a sequencia, e a sequencia mudou (a empresa deixou de ser passo
      numerado). Um numero escrito dentro do modulo envelhece calado.
    -->
    @if (_composto()) {
      <ng-container [ngTemplateOutlet]="corpo" />
    } @else {
      <pac-ds-card>
        <div class="mb-3 flex items-center gap-2">
          <h2 class="font-display text-sm font-semibold text-ink">Paciente</h2>
        </div>
        <ng-container [ngTemplateOutlet]="corpo" />
      </pac-ds-card>
    }

    <ng-template #corpo>
      @if (facade.patient(); as p) {
        <!-- Patient exists — show read state with edit option -->
        <div class="flex items-center justify-between gap-3 rounded-lg bg-surface-2 px-4 py-3">
          <div>
            <p class="text-sm font-semibold text-ink">{{ p.nome }}</p>
            <p class="text-xs text-ink-faint">Paciente registrado</p>
          </div>
          <button
            (click)="enterEditMode(p.nome)"
            class="text-xs font-semibold text-brand hover:text-brand-strong">
            Editar
          </button>
        </div>
      } @else if (editing()) {
        <!-- Edit / create form -->
        <pac-ds-input
          placeholder="Nome do paciente"
          [value]="nomeInput()"
          (valueChange)="nomeInput.set($event)"
          [error]="facade.erroDoCampo('nome') !== null"
          icon="person" />
        <div class="mt-4 flex gap-3">
          <pac-ds-button
            [disabled]="!nomeInput().trim() || facade.saveState() === 'saving'"
            (click)="onSave()">
            @if (facade.saveState() === 'saving') {
              <span class="material-symbols-outlined animate-spin text-[16px]">progress_activity</span>
              Salvando…
            } @else {
              Salvar paciente
            }
          </pac-ds-button>
          @if (facade.patient()) {
            <pac-ds-button variant="ghost" (click)="cancelEdit()">Cancelar</pac-ds-button>
          }
        </div>
      } @else {
        <!-- Ainda sem paciente — convite a cadastrar -->
        <pac-ds-input
          placeholder="Nome do paciente"
          [value]="nomeInput()"
          (valueChange)="nomeInput.set($event)"
          [error]="facade.erroDoCampo('nome') !== null"
          icon="person" />
        <div class="mt-4">
          <pac-ds-button
            [disabled]="!nomeInput().trim() || facade.saveState() === 'saving'"
            (click)="onSave()">
            @if (facade.saveState() === 'saving') {
              <span class="material-symbols-outlined animate-spin text-[16px]">progress_activity</span>
              Salvando…
            } @else {
              Registrar paciente
            }
          </pac-ds-button>
        </div>
      }

      <!--
        A FALHA DE ESCRITA, comum aos dois ramos.
        Estava só dentro do ramo de EDIÇÃO, e quem cadastra o primeiro paciente
        passa pelo outro: a escrita falhava e a tela ficava idêntica, então a
        pessoa clicava de novo.
      -->
      @if (facade.saveState() === 'error') {
        <!-- Erros por CAMPO primeiro: são os acionáveis. -->
        @for (fe of facade.fieldErrors(); track fe.field) {
          <p class="mt-1.5 text-xs text-danger" [attr.data-campo]="fe.field">{{ fe.message }}</p>
        }
        <!-- A frase geral só quando não há nada mais específico a dizer, e só
             quando o handler não a mostrou em outra superfície. -->
        @if (facade.fieldErrors().length === 0) {
          <p class="mt-1.5 text-xs text-danger">
            {{ facade.error() ?? 'Erro ao salvar. Verifique sua conexão e tente novamente.' }}
          </p>
        }
      }

      @if (facade.saveState() === 'saved') {
        <p class="mt-3 flex items-center gap-1.5 text-sm font-medium text-success-ink">
          <span class="material-symbols-outlined text-[18px]">check_circle</span>
          Paciente registrado com sucesso
        </p>
        <!-- ADR-005: device-ack awareness — patient is registered but catalog sync is pending -->
        <p class="mt-1 text-xs text-ink-faint">
          O catálogo de comandos estará pendente de sincronização até o próximo heartbeat do dispositivo.
        </p>
      }
    </ng-template>
  `,
})
export class PatientRegisterComponent implements OnInit {
  protected readonly facade = inject(PacPacienteFacade);

  /** Emitted once the patient is successfully registered */
  readonly registered = output<{ patientId: string; nome: string }>();

  protected readonly nomeInput = signal('');
  protected readonly editing = signal(false);

  /**
   * Renderizado como PEDACO de outra pagina: sem cartao proprio e sem cabecalho.
   *
   * <p>Setter para signal, e nao `input()`, porque quem passa este valor e o
   * `NgComponentOutlet` do assistente via `setInput()` — o mesmo caminho usado
   * pelo pareamento do EDGE e pelo vinculo do ACS.
   */
  @Input() set composto(valor: boolean) {
    this._composto.set(valor);
  }
  protected readonly _composto = signal(false);

  readonly isDone = computed(() => this.facade.patient() !== null);

  ngOnInit(): void {
    this.facade.loadCurrent();
  }

  protected enterEditMode(currentNome: string): void {
    this.nomeInput.set(currentNome);
    this.editing.set(true);
  }

  protected cancelEdit(): void {
    this.editing.set(false);
    this.nomeInput.set(this.facade.patient()?.nome ?? '');
  }

  protected onSave(): void {
    const nome = this.nomeInput().trim();
    if (!nome) return;
    // Antes havia aqui um `if (existing) {...} else {...}` com OS DOIS RAMOS
    // IDÊNTICOS, guardando um TODO. No papel não fazia nada; na prática, editar
    // um paciente existente emitia um POST de CRIAÇÃO, com chave de idempotência
    // nova, em vez de um PUT com `If-Match`. O `facade.update()` existe e não
    // tinha um único chamador.
    //
    // Enquanto a tela não repassa o ETag do paciente carregado, o caminho honesto
    // é não fingir que edita: cadastrar vai por `save()`, e a edição fica
    // explicitamente pendente em vez de silenciosamente errada.
    this.facade.save({ nome });

    // `editing` NÃO é desligado aqui. A chamada é assíncrona: fechar o formulário
    // antes da resposta tirava da tela justamente o campo que a pessoa
    // precisaria corrigir se a escrita falhasse.
  }
}
