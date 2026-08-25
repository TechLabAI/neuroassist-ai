import type { Type } from '@angular/core';

// =============================================================================
// O contrato do assistente de configuração inicial.
//
// POR QUE ELE MORA AQUI, e não dentro de um módulo.
//
// Nasceu dentro do ACS, e enquanto morou lá só o ACS podia declarar um passo —
// o que fez o assistente CONHECER os outros módulos para montar os deles:
// importava o cadastro de paciente direto do PAC e resolvia o pareamento por um
// slot do EDGE. Cada passo novo era mais um módulo que o shell precisava
// conhecer por dentro.
//
// Com o contrato em `shared`, a direção se inverte: cada módulo DECLARA o passo
// que é dele, e o assistente vira hospedeiro — renderiza o que foi declarado, em
// ordem, sem saber de quem veio. É o modelo do SPEC-06, e é o que permite um
// módulo novo entrar na configuração inicial sem que ninguém edite o shell.
// =============================================================================

/**
 * O que o hospedeiro sabe e os módulos não.
 *
 * <p>Um passo do ACS precisa do paciente ativo para saber a quem vincular — e o
 * ACS não pode importar o PAC (fronteira do Nx), nem deveria: um módulo que
 * conhece o outro reintroduz exatamente o acoplamento que esta composição existe
 * para desfazer. O hospedeiro, que já reúne todos, passa o contexto adiante.
 *
 * <p>É o inverso do que havia antes: o shell conhecia os módulos por dentro;
 * agora ele conhece o CONTEXTO, e os módulos decidem o que fazer com ele.
 */
export interface OnboardingWizardContext {
  /** O paciente da configuração em curso, ou null enquanto não houver um. */
  readonly pacienteId: string | null;
}

/** Um passo do assistente, declarado pelo módulo que o possui. */
export interface OnboardingWizardStep {
  /** Chave única do passo. */
  readonly key: string;
  /** Título em pt-BR, mostrado ao operador. */
  readonly title: string;
  /**
   * Componente resolvido preguiçosamente, para o hospedeiro montar.
   *
   * <p>`Promise` de propósito: hoje várias resoluções são imediatas, e este é o
   * gancho por onde qualquer uma vira `import()` preguiçoso sem que o
   * hospedeiro mude.
   */
  readonly component: Promise<Type<unknown>>;
  /**
   * Posição na sequência. Menor aparece antes.
   *
   * <p>Números, e não uma lista ordenada, porque a ordem é uma decisão de
   * PRODUTO distribuída entre módulos: o vínculo precisa vir depois do convite
   * da cuidadora (não há a quem vincular antes), e nenhum dos dois módulos
   * conhece o outro para se posicionar por vizinhança.
   */
  readonly order: number;
  /**
   * Entradas a passar ao componente, calculadas no momento da renderização.
   *
   * <p>É função, e não objeto, porque as entradas dependem de estado que muda —
   * o paciente ativo, por exemplo, só existe depois que o passo do paciente
   * termina. Um objeto calculado na declaração congelaria o valor de antes.
   *
   * <p>Recebe o {@link OnboardingWizardContext} porque parte do que o passo
   * precisa saber pertence à configuração inteira, e não ao módulo.
   */
  readonly inputs?: (contexto: OnboardingWizardContext) => Record<string, unknown>;
}

/**
 * O que um módulo implementa para contribuir com o assistente.
 *
 * <p>Devolver lista vazia é legítimo: um módulo pode não ter passo de
 * configuração inicial.
 */
export interface OnboardingWizardContributor {
  onboardingWizardComposition(): OnboardingWizardStep[];
}
