// =============================================================================
// clock.ts — horário de referência do protótipo.
//
// Nunca usar `new Date()`/`Date.now()` (wall-clock real) para filtrar ou exibir
// dados do mock — os timestamps do mock são fixos em 2026-07-20..2026-07-27; se
// o componente comparar contra a data real do dia em que alguém abre o protótipo,
// "eventos de hoje" e "sincronizado há Xh" ficam incoerentes assim que a data
// real passar de 2026-07-27 (ver knowledges/data-model-driven-ui.md — mock nunca
// usa "hoje"). `agora()` fixa o "presente" da simulação no último dia do mock.
// =============================================================================

const AGORA_ISO = '2026-07-27T18:00:00';

export function agora(): Date {
  return new Date(AGORA_ISO);
}

export function agoraIso(): string {
  return AGORA_ISO;
}
