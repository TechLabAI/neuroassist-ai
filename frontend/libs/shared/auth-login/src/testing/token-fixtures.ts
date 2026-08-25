import type { TokenResponse } from '@neuroassist/shared/auth-session';

// =============================================================================
// Fixtures de token.
//
// PROVENIÊNCIA: copiadas de uma resposta real de
//   curl -X POST http://localhost:8088/oauth2/token \
//        -d '{"grantType":"password","login":"coord@neuroassist.dev","senha":"..."}'
// em 2026-08-24, com os JWTs encurtados (só o valor importa ser opaco).
//
// Escrever fixture "pelo que o nome dos campos sugere" é o defeito nº 1 desta
// casa: o mock passa a concordar com o consumidor e a discordar do produtor, e
// o teste fica verde precisamente quando o consumidor está errado. Se o
// `AutenticacaoResponse` do acs-service mudar, recopie daqui — não edite no
// olho.
// =============================================================================

export const RESPOSTA_TOKEN_COORD: TokenResponse = {
  accessToken: 'eyJhbGciOiJSUzI1NiJ9.fixture-access.assinatura',
  refreshToken: 'eyJhbGciOiJSUzI1NiJ9.fixture-refresh.assinatura',
  tokenType: 'Bearer',
  expiresIn: 900,
  tenant: 'e2e00000-0000-0000-0000-000000000001',
  sub: 'e2e00000-0000-0000-0000-000000000010',
  papel: 'COORDENADOR_CLINICA',
  // Acrescentado ao `AutenticacaoResponse` em 2026-08-24 — entre `papel` e
  // `authorities`. É `null` em token de serviço e de dispositivo: não há pessoa
  // a nomear.
  nome: 'Coord Dev',
  // Lista COMPLETA, recopiada do serviço em 2026-08-24. A versão anterior tinha
  // dez das trinta e duas — e a diferença não era cosmética: faltavam `edge.manage`
  // e `edge.read`, que são justamente o que separa coordenadora de cuidadora na
  // tela de dispositivo. Uma fixture encurtada mente sobre quem o usuário é.
  authorities: [
    'ROLE_CAREGIVER',
    'acs.cuidador.criar',
    'acs.cuidador.read',
    'acs.cuidador.desativar',
    'acs.directory.read',
    'pac.paciente.vincular',
    'pac.paciente.criar',
    'pac.paciente.read',
    'pac.paciente.editar',
    'pac.paciente.deletar',
    'pac.catalogo-ativo.read',
    'pac.comando-authoring.criar',
    'pac.comando-authoring.editar',
    'pac.comando-lifecycle.read',
    'pac.comando-lifecycle.publicar',
    'pac.comando-lifecycle.aposentar',
    'pac.comando-lifecycle.recalibrar',
    'gest.tipo-gesto.read',
    'gest.tipo-gesto.criar',
    'gest.tipo-gesto.editar',
    'gest.detection-profile.read',
    'gest.detection-profile.publicar',
    'gest.detection-contract.read',
    'gest.detection-contract.validar',
    'gest.eligibility.read',
    'edge.read',
    'edge.manage',
    'evnt.event-query.read',
    'evnt.event-confirmation.confirmar',
    'dash.panel-composition.read',
    'hist:read',
    'hist:read:all',
  ],

};

/**
 * Sessão de CUIDADORA — o mesmo `POST /oauth2/token`, com outro login.
 *
 * <p>Existe porque cuidadora e coordenadora diferem em authorities, e há telas
 * cuja regra é exatamente essa diferença: a cuidadora tem `edge.read` e NÃO tem
 * `edge.manage`, então não deve ver afordância de reparear, desparear ou
 * aposentar dispositivo. Sem uma sessão de cuidadora de verdade, o cenário que
 * prova isso só podia fingir — e fingia com um flag em localStorage que nenhuma
 * linha do produto lia.
 */
export const RESPOSTA_TOKEN_CUIDADORA: TokenResponse = {
  accessToken: 'eyJhbGciOiJSUzI1NiJ9.fixture-access-cuidadora.assinatura',
  refreshToken: 'eyJhbGciOiJSUzI1NiJ9.fixture-refresh-cuidadora.assinatura',
  tokenType: 'Bearer',
  expiresIn: 900,
  tenant: 'e2e00000-0000-0000-0000-000000000001',
  sub: 'e2e00000-0000-0000-0000-000000000011',
  papel: 'CUIDADORA',
  nome: 'Cuidadora Dev',
  authorities: [
    'ROLE_CAREGIVER',
    'acs.cuidador.read',
    'acs.directory.read',
    'pac.paciente.read',
    'pac.paciente.editar',
    'pac.catalogo-ativo.read',
    'pac.comando-authoring.criar',
    'pac.comando-authoring.editar',
    'pac.comando-lifecycle.read',
    'pac.comando-lifecycle.publicar',
    'pac.comando-lifecycle.aposentar',
    'pac.comando-lifecycle.recalibrar',
    'gest.tipo-gesto.read',
    'gest.detection-profile.read',
    'gest.detection-contract.read',
    'gest.detection-contract.validar',
    'gest.eligibility.read',
    'edge.read',
    'evnt.event-query.read',
    'evnt.event-confirmation.confirmar',
    'dash.panel-composition.read',
    'hist:read',
  ],
};

/**
 * Sessão de APOIO AO PACIENTE — o terceiro papel do `CuidadorPapel`.
 *
 * <p>Existe porque a CUIDADORA não serve para provar ausência de escrita no PAC:
 * ela tem `pac.comando-authoring.criar` e `pac.comando-lifecycle.publicar` — a
 * única coisa do PAC que ela não tem é `pac.paciente.criar` (cadastrar paciente
 * é da coordenadora). Usar a cuidadora nos cenários de "sem permissão de
 * escrever comando" produziria um verde que não prova nada, porque ela PODE.
 *
 * <p>O apoio é o último degrau do escalonamento (ADR-011): recebe o alarme quando
 * ninguém mais atendeu e pode confirmá-lo. Nenhuma authority de escrita sobre
 * paciente, comando, cuidadora ou dispositivo — e nem `pac.catalogo-ativo.read`,
 * que é o que decide se o item "Comandos do paciente" aparece no menu.
 *
 * <p>Lista recopiada VERBATIM do `RoleAuthorityPolicy.AUTHORITY_MAP`
 * (CuidadorPapel.PACIENTE_APOIO) do acs-service em 2026-08-25. Repare que ele
 * não tem sequer `ROLE_CAREGIVER`.
 */
export const RESPOSTA_TOKEN_APOIO: TokenResponse = {
  accessToken: 'eyJhbGciOiJSUzI1NiJ9.fixture-access-apoio.assinatura',
  refreshToken: 'eyJhbGciOiJSUzI1NiJ9.fixture-refresh-apoio.assinatura',
  tokenType: 'Bearer',
  expiresIn: 900,
  tenant: 'e2e00000-0000-0000-0000-000000000001',
  sub: 'e2e00000-0000-0000-0000-000000000012',
  papel: 'PACIENTE_APOIO',
  nome: 'Apoio Dev',
  authorities: [
    'acs.directory.read',
    'dash.panel-composition.read',
    'hist:read',
    'evnt.event-confirmation.confirmar',
    'evnt.event-query.read',
  ],
};
