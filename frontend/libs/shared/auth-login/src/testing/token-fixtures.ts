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
  authorities: [
    'ROLE_CAREGIVER',
    'acs.cuidador.criar',
    'acs.cuidador.read',
    'acs.cuidador.desativar',
    'acs.directory.read',
    'pac.paciente.vincular',
    'pac.paciente.criar',
    'dash.panel-composition.read',
    'hist:read',
    'hist:read:all',
  ],
};
