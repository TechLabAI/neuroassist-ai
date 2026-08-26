import { HttpContextToken } from '@angular/common/http';

// =============================================================================
// Tokens de contexto HTTP compartilhados.
//
// POR QUE ISTO VIVE EM `contracts` E NÃO EM `auth-login`
//
// `SKIP_AUTH_HEADER` nasceu dentro de `auth-login`, junto do interceptor que o
// lê. Faz sentido enquanto só o próprio login precisa dele — mas ele é, por
// natureza, um **acordo entre bibliotecas**: quem faz a requisição marca, e um
// interceptor que vive noutro lugar obedece.
//
// Quando a estação do paciente (GEST) precisou marcar a autenticação de
// dispositivo, dois muros apareceram:
//
//   1. `libs/gest/tsconfig.lib.json` sobrescreve `paths` e deixa passar apenas
//      `@neuroassist/shared/contracts`. Importar `auth-login` compila no Jest e
//      **reprova no build da aplicação** — a armadilha desta casa: o
//      transpilador do Jest não resolve o mesmo grafo que o ngtsc.
//   2. `auth-login` é `type:feature`, como o GEST. Feature importando feature é
//      violação de fronteira do Nx — o tsconfig só estava sendo o primeiro a
//      reclamar.
//
// `contracts` é `type:contracts`: existe precisamente para o que atravessa
// bibliotecas.
//
// **A identidade do token é o que importa.** `HttpContextToken` é comparado por
// referência: duas declarações iguais em arquivos diferentes são dois tokens
// distintos, e o interceptor não veria a marca. Por isso `auth-login`
// **reexporta** este símbolo em vez de declarar o seu — há uma instância só, e
// todo import antigo continua funcionando.
// =============================================================================

/**
 * Marca uma requisição que **não** deve levar `Authorization`.
 *
 * <p>Usado onde a requisição é justamente para OBTER uma credencial: o login de
 * pessoa (`POST /oauth2/token`) e a autenticação de dispositivo da estação
 * (`POST /oauth2/token/device`).
 *
 * <p>Não é zelo excessivo. Estes endpoints são `permitAll`, mas ficam atrás do
 * filtro do resource server, que rejeita um token inválido **antes** de o
 * controller ser alcançado. Medido contra o acs-service em execução: sem header,
 * `401 acs.auth.invalid-credentials` (chegou ao endpoint); com um Bearer velho,
 * `401 acs.auth-unauthenticated` (não chegou). Os dois são 401 — e é isso que
 * torna o defeito difícil de diagnosticar.
 */
export const SKIP_AUTH_HEADER = new HttpContextToken<boolean>(() => false);
