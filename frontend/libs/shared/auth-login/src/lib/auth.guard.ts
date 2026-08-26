import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthSessionStore } from '@neuroassist/shared/auth-session';
import { ROTA_HOME, ROTA_LOGIN } from './auth.config';

// =============================================================================
// Guards de sessão.
//
// Estes NÃO têm bypass. O `acsCoordinatorGuard` tem — ele deixa passar quando o
// token `ACS_AUTH_SESSION` resolve para `null`, que é o padrão, e portanto hoje
// todo `/acs` está aberto. Um guard que devolve `true` quando não está
// configurado não é um guard: é documentação de intenção.
//
// Nome do parâmetro de retorno: `destino`. Guardar para onde a pessoa ia evita
// o padrão irritante de mandar todo mundo para a home depois do login.
// =============================================================================

/**
 * Só passa quem tem sessão válida. Quem não tem vai para `/login`, com o
 * destino pretendido preservado.
 *
 * <p>`autenticado()` já considera a expiração: um token vencido é tratado como
 * ausência de sessão. Deixar passar por "tem token" mandaria a pessoa para uma
 * tela que tomaria 401 na primeira chamada.
 */
export const sessaoRequeridaGuard: CanActivateFn = (_rota, estado) => {
  const sessao = inject(AuthSessionStore);
  if (sessao.autenticado()) return true;

  return inject(Router).createUrlTree([ROTA_LOGIN], {
    queryParams: { destino: estado.url },
  });
};

/**
 * O inverso, para a própria tela de login: quem já entrou não deve ver o
 * formulário de novo — vai para a home.
 *
 * <p>Sem isto, o botão "voltar" do navegador depois do login mostra o
 * formulário de novo, e a pessoa razoavelmente conclui que não entrou.
 */
export const sessaoAusenteGuard: CanActivateFn = () => {
  const sessao = inject(AuthSessionStore);
  if (!sessao.autenticado()) return true;

  return inject(Router).createUrlTree([ROTA_HOME]);
};
