import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import type { TokenResponse } from '@neuroassist/shared/auth-session';
import { AUTH_API_BASE, SKIP_AUTH_HEADER } from './auth.config';

// =============================================================================
// AuthApiService — a única coisa no frontend que fala com `/oauth2/token`.
//
// O corpo espelha `AutenticacaoRequest` do acs-service campo por campo
// (`grantType`, `login`, `senha`) e a resposta espelha `AutenticacaoResponse`
// (`accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `tenant`, `sub`,
// `papel`, `nome`, `authorities`) — conferido no record Java, não deduzido pelo
// nome. Deduzir o contrato do produtor é o erro mais repetido desta casa, e um
// mock escrito a partir da mesma dedução faz o teste passar exatamente quando a
// dedução está errada.
// =============================================================================

/**
 * Por que o erro do login é um tipo fechado de três valores e não a
 * `HttpErrorResponse` crua: a tela precisa distinguir **exatamente** três
 * situações, e nenhuma delas pode vazar detalhe do servidor para o usuário.
 */
export type LoginErro =
  /** O ACS respondeu 401. Não se sabe — nem se pode saber — qual metade errou. */
  | 'credencial-invalida'
  /** Não houve resposta: backend fora, DNS, proxy, rede do cliente. */
  | 'rede'
  /** Houve resposta, e ela não é 401 nem 200. Problema do servidor. */
  | 'servidor';

/** Erro de login já classificado. Não carrega corpo de resposta nem credencial. */
export class LoginFalhou extends Error {
  constructor(readonly motivo: LoginErro) {
    super(`login falhou: ${motivo}`);
    this.name = 'LoginFalhou';
  }
}

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(AUTH_API_BASE);

  /**
   * Troca credenciais por um token.
   *
   * <p>A senha viaja **apenas** no corpo de um POST. Nunca em query string (que
   * entra em log de servidor, em histórico de navegador e em header `Referer`),
   * nunca em log do cliente, e nunca no erro devolvido daqui — por isso o
   * `catchError` descarta a resposta original em vez de repassá-la.
   */
  login(login: string, senha: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(
        `${this.base}/oauth2/token`,
        { grantType: 'password', login, senha },
        // Pedir token novo não pode ir assinado com o token velho.
        { context: new HttpContext().set(SKIP_AUTH_HEADER, true) },
      )
      .pipe(catchError((erro: unknown) => throwError(() => new LoginFalhou(classificar(erro)))));
  }
}

/**
 * Traduz a falha HTTP para o vocabulário da tela.
 *
 * <p>`status === 0` é o caso que mais importa acertar: é o que o navegador
 * reporta quando **não houve resposta alguma** (backend fora, proxy fora, rede
 * caída). Tratá-lo como credencial inválida faria a tela acusar a pessoa de
 * errar a senha justamente quando ela não errou nada.
 */
function classificar(erro: unknown): LoginErro {
  if (!(erro instanceof HttpErrorResponse)) return 'servidor';
  if (erro.status === 0) return 'rede';
  if (erro.status === 401) return 'credencial-invalida';
  return 'servidor';
}
