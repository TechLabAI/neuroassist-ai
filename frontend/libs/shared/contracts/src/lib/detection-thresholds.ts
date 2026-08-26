// =============================================================================
// DetectionThresholds — limiares de detecção de gesto (GEST-FE-029)
//
// Espelha campo a campo o record DetectionThresholds do gest-service (Java).
// Os valores canônicos de cada faixa vivem no DetectionThresholdTable.defaults()
// do backend; LIMIARES_PADRAO abaixo espelha a faixa 'media'.
//
// Regras de coerência (derivadas do record Java):
//   EAR: earClose < earOpen ≤ earOtherMax < 1.0  (EAR cai ao fechar o olho;
//        valor maior = detectar mais fácil)
//   BRI: briRaiseOff ≤ briOtherMax < briRaiseOn   (BRI sobe ao levantar a
//        sobrancelha; valor menor = detectar mais fácil)
//
// Guarda de drift: os valores de LIMIARES_PADRAO são vigiados pelo script
//   backend/scripts/check_edge_agent_drift.py
// que compara este arquivo com o DetectionThresholdTable.java.
// Mudar um valor aqui sem atualizar o backend reprova no CI.
// =============================================================================

/**
 * Conjunto completo de limiares de uma faixa de sensibilidade.
 * Espelha `com.neuroassist.gest.domain.model.DetectionThresholds`.
 */
export interface DetectionThresholds {
  /** Abaixo disto o olho conta como fechado (EAR — cai quando fecha). */
  readonly earClose: number;
  /** Acima disto o olho conta como claramente aberto (histerese contra tremor). */
  readonly earOpen: number;
  /** Num piscar individual, o OUTRO olho precisa estar acima disto. */
  readonly earOtherMax: number;
  /** Frames consecutivos fechados para valer piscada (e não ruído de frame). */
  readonly blinkMinFrames: number;
  /** Janela em ms para duas piscadas contarem como piscar duplo. */
  readonly doubleBlinkWindowMs: number;
  /** Acima disto a sobrancelha conta como levantada (BRI — sobe ao levantar). */
  readonly briRaiseOn: number;
  /** Abaixo disto conta como relaxada; a folga até briRaiseOn é a histerese. */
  readonly briRaiseOff: number;
  /** Num levantamento individual, o lado oposto deve estar abaixo disto. */
  readonly briOtherMax: number;
  /** Frames de sustentação — a sobrancelha sobe e desce ao falar. */
  readonly browMinFrames: number;
}

/**
 * Limiares padrão alinhados com a faixa 'media' de DetectionThresholdTable.defaults()
 * no gest-service.
 *
 * Invariante de alinhamento: este objeto deve reproduzir exatamente
 * `new DetectionThresholds(0.22, 0.27, 0.30, 2, 2000, 0.30, 0.22, 0.25, 5)`
 * da faixa 'media' do backend. O script check_edge_agent_drift.py verifica isso.
 *
 * Usa-se como fallback SOMENTE quando o perfil de detecção não pôde ser lido
 * (ou seja, quando o dispositivo está offline ou o backend indisponível).
 * Nesse caso a tela da estação declara o estado degradado explicitamente.
 */
export const LIMIARES_PADRAO: DetectionThresholds = {
  // ── Alinhados com a faixa `media` do backend, e comparáveis de verdade ──────
  // EAR é a MESMA grandeza nos dois motores: (altura do olho / largura),
  // calculada dos landmarks. Um limiar de EAR significa a mesma coisa aqui e lá.
  earClose: 0.22,
  earOpen: 0.27,
  // Contagens de frame e janela de tempo são adimensionais — comparáveis por
  // construção.
  blinkMinFrames: 2,
  doubleBlinkWindowMs: 2000,
  browMinFrames: 5,

  // ── NÃO alinhados, e deliberadamente ───────────────────────────────────────
  //
  // Os três campos abaixo, mais `earOtherMax`, são consumidos por este motor
  // contra BLENDSHAPES do MediaPipe (`browInnerUp`, `browOuterUpLeft/Right`,
  // `eyeBlinkLeft/Right`) — ativações 0..1 de uma rede neural.
  //
  // No backend eles são calibrados para BRI, que é OUTRA grandeza:
  //
  //     BRI = (eye_top_y − brow_peak_y) / inter_eye_dist        (edge_agent_core.py)
  //
  // Uma razão geométrica normalizada pela distância interocular. Um limiar de
  // 0.30 em BRI não quer dizer nada num blendshape — são escalas diferentes,
  // produzidas por processos diferentes.
  //
  // A primeira versão deste arquivo copiou os números do backend por igualdade
  // de NOME, e com isso mudou em silêncio a sensibilidade das sobrancelhas:
  // 0.40 → 0.30 para disparar, 0.18 → 0.25 para soltar. Num canal em que o falso
  // negativo significa Márcio não conseguir pedir ajuda, e o falso positivo
  // significa alarme sem motivo, isso não é um ajuste — é uma mudança clínica
  // feita por coincidência de nomenclatura. A suíte não acusou porque nenhum
  // teste exercita o limiar real de sobrancelha.
  //
  // Os valores abaixo são os do protótipo, "proven accurate on real hardware".
  // Ficam até o backend publicar uma faixa calibrada para blendshapes — e a
  // divergência é AFIRMADA pelo guarda de drift, não tolerada por ele.
  //
  // `earOtherMax` cai no mesmo caso (é comparado com `eyeBlinkRight/Left`, um
  // blendshape). Hoje o número coincide com o do backend, então não há mudança
  // de comportamento — mas o acoplamento é errado, e mexer no lado EAR do
  // backend mudaria a piscada individual por um motivo sem relação.
  earOtherMax: 0.3,
  briRaiseOn: 0.4,
  briRaiseOff: 0.18,
  briOtherMax: 0.18,
} as const;

/**
 * Os campos de `DetectionThresholds` que este motor consome contra BLENDSHAPES,
 * e que portanto **não** podem ser comparados com a tabela do backend.
 *
 * <p>Existe para o guarda de drift afirmar a divergência em vez de ignorá-la:
 * um guarda que simplesmente pula campos vira o "guarda que não guarda nada".
 */
export const CAMPOS_ESCALA_BLENDSHAPE = [
  'earOtherMax',
  'briRaiseOn',
  'briRaiseOff',
  'briOtherMax',
] as const satisfies readonly (keyof DetectionThresholds)[];
