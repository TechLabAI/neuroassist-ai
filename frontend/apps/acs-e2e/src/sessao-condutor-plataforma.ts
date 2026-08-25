import {
  scriptSemearSessao,
  conteudoSessaoSemeada,
  RESPOSTA_TOKEN_COORD,
} from '@neuroassist/shared/auth-login/testing';

// =============================================================================
// Sessão do CONDUTOR DE PLATAFORMA — só para esta suíte.
//
// POR QUE ELA PRECISA EXISTIR
//
// Criar empresa exige `acs.tenant.provisionar` (`TenantController:45`), e o
// `RoleAuthorityPolicy` do acs-service NÃO concede essa authority a papel humano
// nenhum: E2E-029 tirou-a do COORDENADOR_CLINICA e a moveu para o cliente de
// serviço `platform-ops`, que obtém token sem tenant — não há tenant antes de
// criá-lo. É o "Passo 0" do tutorial: a clínica é registrada pela equipe técnica
// antes de a coordenadora chegar.
//
// Os cenários que EXERCITAM o provisionamento (criar, provisionando, retentar)
// são, portanto, cenários de condutor técnico, não de coordenadora. Semeá-los
// com a sessão de coordenadora só passava porque o botão não conferia permissão
// alguma — o verde media a ausência da trava.
//
// POR QUE NÃO ESTÁ EM `shared/auth-login/testing`
//
// As fixtures de lá são cópias VERBATIM de respostas reais do `POST
// /oauth2/token`, e não existe login humano que devolva esta lista — o
// `platform-ops` é um cliente de serviço. Uma fixture inventada convivendo com
// as copiadas apagaria a diferença entre as duas, que é justamente o que dá
// valor àquele arquivo. Aqui ela fica declarada como o que é: uma composição
// deste teste.
//
// A CHAVE do sessionStorage não é redigitada: ela é extraída do próprio script
// do auth-login. Um segundo literal aqui seria o defeito nº 1 desta casa — o
// dia em que a chave mudar, este arquivo acompanha ou falha ruidosamente.
// =============================================================================

const CHAVE_SESSAO = (() => {
  const achado = /sessionStorage\.setItem\((".*?")/.exec(scriptSemearSessao());
  if (!achado) {
    throw new Error(
      'Não foi possível extrair a chave de sessão de scriptSemearSessao(). ' +
        'O formato do script do auth-login mudou — ajuste a extração aqui.',
    );
  }
  return achado[1];
})();

/**
 * Coordenadora + as três authorities de tenant do cliente `platform-ops`.
 *
 * <p>Mantém `acs.cuidador.criar` porque a rota `/onboarding` exige essa
 * capacidade no `canActivate` (`acsCaregiverWriteGuard`): sem ela o condutor
 * nem chegaria à tela cujo passo 1 estamos exercitando.
 */
const RESPOSTA_TOKEN_CONDUTOR = {
  ...RESPOSTA_TOKEN_COORD,
  sub: 'e2e00000-0000-0000-0000-000000000013',
  nome: 'Condutor Plataforma',
  authorities: [
    ...RESPOSTA_TOKEN_COORD.authorities,
    // Verbatim de `TenantController` / `RoleAuthorityPolicyTest:32`.
    'acs.tenant.provisionar',
    'acs.tenant.read',
    'acs.tenant.reprocessar',
  ],
};

/** Script para `page.addInitScript()` — mesma mecânica de `scriptSemearSessao()`. */
export function scriptSemearSessaoCondutorPlataforma(validoPorSegundos = 3600): string {
  return `(() => {
  try {
    sessionStorage.setItem(${CHAVE_SESSAO}, ${JSON.stringify(
      conteudoSessaoSemeada(validoPorSegundos, RESPOSTA_TOKEN_CONDUTOR),
    )});
  } catch { /* sessionStorage bloqueado: o cenário falha no guard, e isso é informação */ }
})()`;
}
