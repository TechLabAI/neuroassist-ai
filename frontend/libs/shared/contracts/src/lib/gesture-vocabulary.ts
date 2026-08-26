// =============================================================================
// Vocabulário de gestos — a ÚNICA lista de nomes que o motor e o catálogo
// compartilham.
//
// POR QUE ESTE ARQUIVO EXISTE
//
// Antes dele o projeto tinha três vocabulários que não se encontravam:
//
//   detector (browser)      tg-piscar-duplo-001, tg-sobrancelha-dupla, …
//   catálogo oferecido      tg-piscar-duplo,     tg-sobrancelha,       …
//   banco (tipo_gesto)      uuid + engine_feature_ref (blink_v1)
//
// A interseção era VAZIA — repare em `tg-piscar-duplo` contra
// `tg-piscar-duplo-001`. Um comando configurado pela tela nunca casava com uma
// detecção, e nada acusava: o adaptador comparava o id do catálogo com o id
// emitido pelo motor, e os testes de cada lado usavam os ids do seu próprio
// lado. É o defeito nº 1 catalogado deste repositório — cada ponta escrita a
// partir da própria suposição.
//
// O nome canônico é o do BANCO, não um quarto inventado aqui: a coluna
// `tipo_gesto.engine_feature_ref` já era o ponteiro para "o que o motor sabe
// detectar", e a constraint `ck_tipo_gesto_supported_needs_ref` já dizia que
// `supported` sem referência de motor é estado inválido. O modelo estava certo;
// o frontend é que o ignorava.
//
// Em inglês porque é identificador de máquina, atravessa Java, SQL e TypeScript,
// e vive em coluna de banco. O texto que a cuidadora lê é outro campo, em
// português — os dois estão neste arquivo, e a separação entre eles é o ponto.
// =============================================================================

/**
 * O que o motor de detecção sabe reconhecer hoje.
 *
 * <p>Estes seis são exatamente os que o `GestureDetector` (MediaPipe no browser,
 * ADR-007) emite. Acrescentar um valor aqui **não** cria capacidade de detecção:
 * quem detecta é o detector. A ordem é a inversa — o motor ganha o gesto, e só
 * então o nome entra nesta lista.
 *
 * <p>O sufixo `_v1` acompanha a convenção que já estava no banco (`blink_v1`) e
 * na ADR-003 (`gesture_v1.onnx`): se um gesto mudar de algoritmo a ponto de o
 * limiar calibrado deixar de valer, ele vira `_v2` e as calibrações antigas não
 * são silenciosamente reinterpretadas por um motor diferente.
 */
export const ENGINE_FEATURE = {
  blinkDouble: 'blink_double_v1',
  blinkLeft: 'blink_left_v1',
  blinkRight: 'blink_right_v1',
  browRaiseBoth: 'brow_raise_both_v1',
  browRaiseLeft: 'brow_raise_left_v1',
  browRaiseRight: 'brow_raise_right_v1',
} as const;

export type EngineFeatureRef = (typeof ENGINE_FEATURE)[keyof typeof ENGINE_FEATURE];

export const ENGINE_FEATURE_REFS: readonly EngineFeatureRef[] = Object.freeze(
  Object.values(ENGINE_FEATURE),
);

export function isEngineFeatureRef(valor: string | null | undefined): valor is EngineFeatureRef {
  return valor !== null && valor !== undefined && (ENGINE_FEATURE_REFS as readonly string[]).includes(valor);
}

/**
 * Estado de detectabilidade — espelha `tipo_gesto.detectability_state`
 * (`ck_tipo_gesto_state`).
 *
 * <p>Existe porque o catálogo é extensível: uma clínica pode **pedir** um gesto
 * novo, e o pedido vira uma linha antes de existir detecção para ele. Sem este
 * campo o catálogo oferecia "Leitura labial" e "Mover o nariz" como se
 * funcionassem — nomes sem motor nenhum atrás.
 */
export type DetectabilityState = 'registered' | 'pending_training' | 'supported' | 'deprecated';

/** Só `supported` é escolhível para um comando — e a constraint do banco garante
 * que `supported` sempre traz `engine_feature_ref`. */
export function ehDetectavel(estado: DetectabilityState): boolean {
  return estado === 'supported';
}

/** Por que este gesto não pode ser escolhido — frase para a cuidadora, não código. */
export function motivoIndisponivel(estado: DetectabilityState): string | null {
  switch (estado) {
    case 'supported':
      return null;
    case 'registered':
      return 'Pedido registrado — ainda não há detecção para este movimento.';
    case 'pending_training':
      return 'Em treinamento — ainda não pode ser usado num comando.';
    case 'deprecated':
      return 'Descontinuado — escolha outro movimento.';
  }
}

// ---------------------------------------------------------------------------
// A instrução — o que estava faltando na tela
// ---------------------------------------------------------------------------

/**
 * Como executar o gesto, em português, mais a janela de tempo que a frase cita.
 *
 * <p>Portado do protótipo (`calibracao.component.ts`), que dizia à pessoa o que
 * fazer diante da câmera. O porte da ADR-007 trouxe a matemática do detector e
 * deixou a instrução para trás: a tela mostrava a câmera e nenhuma orientação, e
 * "não detectou" ficava indistinguível de "não sei o que devo fazer".
 *
 * <p>`janelaMs` não é decoração: é o mesmo número que o detector usa, e existe
 * para ser conferido por teste. Uma instrução que promete 1,8 s enquanto o
 * detector espera 1,2 s manda a pessoa fazer a coisa errada — e é o tipo de
 * divergência que ninguém percebe, porque os dois lados continuam "corretos"
 * cada um por si.
 */
export interface GestureGuidance {
  readonly engineFeatureRef: EngineFeatureRef;
  /** Nome padrão em pt-BR. O catálogo pode renomear por empresa; este é o de fábrica. */
  readonly nomePadrao: string;
  /** Instrução de execução, dirigida a quem está diante da câmera. */
  readonly comoFazer: string;
  /** Janela de tempo citada em `comoFazer`, em milissegundos. */
  readonly janelaMs: number;
}

export const GESTURE_GUIDANCE: Readonly<Record<EngineFeatureRef, GestureGuidance>> = Object.freeze({
  [ENGINE_FEATURE.blinkDouble]: {
    engineFeatureRef: ENGINE_FEATURE.blinkDouble,
    nomePadrao: 'Piscar duas vezes',
    comoFazer:
      'Pisque duas vezes de forma deliberada — olho completamente fechado e reaberto em cada piscada.',
    janelaMs: 1800,
  },
  [ENGINE_FEATURE.blinkLeft]: {
    engineFeatureRef: ENGINE_FEATURE.blinkLeft,
    nomePadrao: 'Piscar o olho esquerdo',
    comoFazer:
      'Feche e reabra apenas o olho esquerdo, mantendo o direito aberto.',
    janelaMs: 100,
  },
  [ENGINE_FEATURE.blinkRight]: {
    engineFeatureRef: ENGINE_FEATURE.blinkRight,
    nomePadrao: 'Piscar o olho direito',
    comoFazer:
      'Feche e reabra apenas o olho direito, mantendo o esquerdo aberto.',
    janelaMs: 100,
  },
  [ENGINE_FEATURE.browRaiseBoth]: {
    engineFeatureRef: ENGINE_FEATURE.browRaiseBoth,
    nomePadrao: 'Levantar as duas sobrancelhas',
    comoFazer: 'Levante as duas sobrancelhas ao mesmo tempo e mantenha por um instante.',
    janelaMs: 167,
  },
  [ENGINE_FEATURE.browRaiseLeft]: {
    engineFeatureRef: ENGINE_FEATURE.browRaiseLeft,
    nomePadrao: 'Levantar a sobrancelha esquerda',
    comoFazer: 'Levante apenas a sobrancelha esquerda, com a direita relaxada, e mantenha por um instante.',
    janelaMs: 167,
  },
  [ENGINE_FEATURE.browRaiseRight]: {
    engineFeatureRef: ENGINE_FEATURE.browRaiseRight,
    nomePadrao: 'Levantar a sobrancelha direita',
    comoFazer: 'Levante apenas a sobrancelha direita, com a esquerda relaxada, e mantenha por um instante.',
    janelaMs: 167,
  },
});

/** Instrução do gesto, ou `null` quando o comando aponta para algo que o motor
 * não detecta — caso em que a tela precisa dizer isso, não ficar muda. */
export function guidanceDe(ref: string | null | undefined): GestureGuidance | null {
  return isEngineFeatureRef(ref) ? GESTURE_GUIDANCE[ref] : null;
}
