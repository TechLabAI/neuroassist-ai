// =============================================================================
// Bandeiras de e2e do EVNT — o nome de cada uma, num lugar só.
//
// POR QUE ISTO É UM MÓDULO E NÃO UMA STRING SOLTA
//
// Quem ESCREVE a bandeira é a suíte de teste; quem a LÊ é o adaptador mock. São
// dois lados que precisam concordar num literal, e é exatamente a forma do
// defeito nº 1 desta casa: o consumidor escreve o literal pelo que acha que ele
// é, o produtor lê outro, e nada acusa — a bandeira simplesmente não faz efeito,
// e o teste passa ou falha por um motivo que ninguém investiga.
//
// Com o literal aqui, escrever errado não compila.
// =============================================================================

export const EVNT_E2E_FLAGS = {
  /** `listActive()` devolve `activeEvent: null` — nenhuma emergência ativa. */
  semEmergencia: '__EVNT_E2E_NO_EMERGENCY__',
  /** `openStream()` estoura de imediato — o aplicativo entra em modo degradado. */
  falhaSse: '__EVNT_E2E_SSE_FAIL__',
  /** `confirm()` estoura com erro de rede. */
  falhaConfirmar: '__EVNT_E2E_CONFIRM_FAIL__',
  /** `confirm()` estoura com `/errors/feedback-terminal` — confirmação concorrente. */
  conflitoConfirmar: '__EVNT_E2E_CONFIRM_409__',
} as const;
