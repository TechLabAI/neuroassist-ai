import { Injectable, Signal, Type, computed, inject } from '@angular/core';
import type {
  OnboardingWizardStep,
  OnboardingWizardContributor,
} from '@neuroassist/shared/contracts';
import { PatientRegisterComponent } from '../feature-patient-register/patient-register.component';
import { FirstCommandStepComponent } from '../feature-first-command/first-command-step.component';
import { PacPacienteFacade } from '../pac-paciente.facade';

// =============================================================================
// PacShellIntegrationService — a porta do PAC para o shell (SPEC-06 §3)
//
// POR QUE ELE PASSOU A EXISTIR.
//
// O assistente de configuração inicial (`scope:web`) importava o
// `PatientRegisterComponent` DIRETO do barril do PAC e o montava com
// `<pac-patient-register />` num bloco de template que trazia o número do passo
// e o título escritos à mão. Ou seja: o shell conhecia o passo do PAC por
// dentro — sabia o componente, sabia a posição, sabia o rótulo. Cada passo novo
// era mais um módulo que ele precisava conhecer.
//
// Com esta classe a direção se inverte, como já acontecia com EDGE e ACS: o PAC
// DECLARA o passo que é dele, e o hospedeiro só reúne, ordena e renderiza. O
// componente é entregue por `resolvePatientRegisterSlot()`, e o `scope:web`
// nunca mais importa o tipo — ele chega por `NgComponentOutlet`, em tempo de
// execução.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class PacShellIntegrationService implements OnboardingWizardContributor {
  private readonly pacienteFacade = inject(PacPacienteFacade);

  /**
   * O paciente ativo, para quem monta o CONTEXTO do assistente.
   *
   * <p>O hospedeiro precisa deste id para repassá-lo aos passos dos outros
   * módulos (o EDGE pareia com ele; o ACS vincula a ele). Enquanto ele o lia
   * injetando `PacPacienteFacade`, o shell continuava conhecendo o PAC por
   * dentro para uma coisa que é justamente a fronteira desta classe.
   */
  readonly pacienteAtivoId: Signal<string | null> = computed(
    () => this.pacienteFacade.patient()?.id ?? null,
  );

  /** O paciente da empresa já está registrado — item de progresso do assistente. */
  readonly temPaciente: Signal<boolean> = computed(() => this.pacienteFacade.hasPatient());

  /**
   * Entrega a superfície do PRIMEIRO COMANDO para quem compõe o assistente.
   *
   * <p>Ver a nota do próprio componente: nenhuma superfície existente do PAC
   * servia sem virar uma página inteira dentro do assistente, então o passo
   * ganhou um componente próprio — dentro do `libs/pac`, nunca no shell.
   */
  resolveFirstCommandSlot(): Promise<Type<FirstCommandStepComponent>> {
    return Promise.resolve(FirstCommandStepComponent);
  }

  /**
   * Entrega a superfície de CADASTRO DE PACIENTE para quem compõe o assistente.
   *
   * <p>Espelha `EdgeShellIntegrationService.resolvePairingSlot()` e
   * `AcsShellIntegrationService.resolveCaregiverLinkSlot()`, e existe pelo mesmo
   * motivo: o hospedeiro monta o componente por `NgComponentOutlet` sem jamais
   * importar o tipo de dentro do `scope:pac`. O `Promise` é o formato combinado
   * — hoje resolve na hora, e é o gancho por onde o componente vira `import()`
   * preguiçoso sem mexer em quem chama.
   */
  resolvePatientRegisterSlot(): Promise<Type<PatientRegisterComponent>> {
    return Promise.resolve(PatientRegisterComponent);
  }

  /**
   * O passo do assistente que pertence ao PAC.
   *
   * <p>`order` é chave de ORDENAÇÃO, não o número mostrado: o hospedeiro numera
   * por posição depois de ordenar. O paciente vem antes do dispositivo (order 3)
   * porque não há a quem parear um dispositivo antes de existir paciente.
   *
   * <p>`composto: true` suprime o cartão e o cabeçalho próprios do componente —
   * quem desenha o número e o título do passo é o hospedeiro, e sem isto a tela
   * ficava com dois cabeçalhos "Paciente", um dentro do outro.
   */
  onboardingWizardComposition(): OnboardingWizardStep[] {
    return [
      {
        key: 'pac-patient-register',
        title: 'Paciente',
        component: this.resolvePatientRegisterSlot() as Promise<Type<unknown>>,
        order: 2,
        inputs: () => ({ composto: true }),
      },
      {
        // O PRIMEIRO COMANDO deixou de ser um ponteiro.
        //
        // Era um `<a routerLink="/pac">` escrito no assistente: o passo não
        // fazia a coisa, mandava a pessoa embora — e quem sai do assistente no
        // meio perde o progresso visível e o passo seguinte. Agora o PAC é dono
        // do passo de verdade, e ele CRIA o comando ali mesmo.
        key: 'pac-first-command',
        title: 'Primeiro comando',
        component: this.resolveFirstCommandSlot() as Promise<Type<unknown>>,
        // Depois do dispositivo (order 3) e antes do convite (order 5): a
        // cuidadora é convidada para um paciente que já tem o que responder.
        order: 4,
        // Este passo PRECISA do contexto: o comando pertence a um paciente, e o
        // PAC não tem como saber qual é o da configuração em curso a partir da
        // rota /onboarding.
        inputs: (contexto) => ({ pacienteHint: contexto.pacienteId }),
      },
    ];
  }
}
