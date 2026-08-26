import { InjectionToken } from '@angular/core';

// =============================================================================
// Tokens de acesso do PAC.
//
// POR QUE EXISTEM: os guards do PAC devolviam `true` incondicionalmente — não
// consultavam nada. O `PAC_PERMISSION_MAP` (pac.descriptor.ts) existe desde o
// início justamente para traduzir chave de tela → authority do backend, e
// nenhuma linha do módulo o lia. Era permissão declarada, nunca aplicada.
//
// A tradução NÃO mora aqui. Mora em `app.config.ts` (scope:web), pelo mesmo
// motivo do ACS e do EDGE: quem sabe quem está logado é o `AuthSessionStore`,
// que é do shell, e uma lib de módulo não pode depender dele sem inverter a
// direção das dependências. O contrato daqui é mínimo — uma pergunta booleana.
//
// O argumento de `can()` é a CHAVE DE TELA (`'commandWrite'`, `'write'`…), não
// a authority. Isto é deliberado: o nome da authority é decisão do acs-service
// e já mudou duas vezes; se o guard citasse o literal, cada mudança lá viraria
// um 403 silencioso aqui. O guard pergunta pela capacidade; o mapa diz qual
// authority a representa hoje.
//
// Ligação em app.config.ts:
//   { provide: PAC_ACCESS_DECISION, useFactory: ... PAC_PERMISSION_MAP ... }
//   { provide: PAC_AUTH_SESSION,    useFactory: ... AuthSessionStore ... }
// =============================================================================

/** Estruturalmente equivalente a `AuthSession` de @neuroassist/shared/auth-session. */
export interface PacAuthSession {
  readonly token: string | null;
}

/**
 * Decisão de acesso por CHAVE DE TELA.
 *
 * <p>Chave desconhecida deve NEGAR — quem implementa este contrato em
 * `app.config.ts` é responsável por isso. Deixar passar o que não se sabe
 * traduzir transforma erro de digitação em permissão concedida.
 */
export interface PacAccessDecision {
  can(chaveDeTela: string): boolean;
}

/**
 * Sessão do PAC. Padrão `null` = não providenciado.
 *
 * <p>Com `null` o `pacAuthGuard` deixa passar, e isso NÃO é segurança: no
 * aplicativo real quem barra a rota é o `sessaoRequeridaGuard` do shell, um
 * nível acima (app.routes.ts), e sobretudo o backend, que valida o token em
 * cada chamada. O bypass existe para que a lib continue montável isolada em
 * teste de unidade — não para servir de porta.
 */
export const PAC_AUTH_SESSION = new InjectionToken<PacAuthSession | null>(
  'PAC_AUTH_SESSION',
  { providedIn: 'root', factory: () => null },
);

/** Decisão de permissão do PAC. Padrão `null` = não providenciado (ver acima). */
export const PAC_ACCESS_DECISION = new InjectionToken<PacAccessDecision | null>(
  'PAC_ACCESS_DECISION',
  { providedIn: 'root', factory: () => null },
);
