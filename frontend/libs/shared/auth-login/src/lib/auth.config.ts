import { InjectionToken } from '@angular/core';
import { HttpContextToken } from '@angular/common/http';

// =============================================================================
// Configuração da autenticação.
//
// Nenhuma URL de backend aparece em componente: o componente injeta o serviço,
// o serviço injeta o token, e o token é preenchido em `app.config.ts`. Trocar de
// ambiente é trocar um provider.
// =============================================================================

/**
 * Prefixo das chamadas ao acs-service (`POST <base>/oauth2/token`).
 *
 * <p><b>Por que um caminho relativo e não `http://localhost:8088`:</b> o
 * acs-service **rejeita CORS** — um `OPTIONS` vindo de `http://localhost:4200`
 * responde `403 Invalid CORS request`. Chamar a porta 8088 direto do navegador
 * não funciona, e "arrumar" isso seria mexer no backend. A saída é manter tudo
 * na mesma origem, e o navegador nunca conhece a porta do serviço.
 *
 * <p><b>Por que vazio, e não `/acs-api`:</b> a primeira versão inventou o
 * prefixo `/acs-api` e deixou como pendência "alguém precisa rotear isso em
 * produção" — uma dependência de implantação criada por nós, que faria o login
 * falhar no container servindo HTML no lugar do JSON (o fallback da SPA responde
 * 200, então o sintoma seria um erro de parse, não um 404).
 *
 * <p>Não era preciso: o nginx do container já encaminha `/oauth2/` para o
 * gateway, e o gateway já leva ao acs-service — verificado com uma chamada real
 * a `http://localhost:14200/oauth2/token`, que devolve o token completo. Com a
 * base vazia o caminho é `/oauth2/token` em dev e em produção, e o dev-server
 * faz proxy do MESMO caminho. Um caminho só, nada a lembrar na implantação.
 */
export const AUTH_API_BASE = new InjectionToken<string>('AUTH_API_BASE', {
  providedIn: 'root',
  factory: () => '',
});

/**
 * Prefixos de URL que recebem o header `Authorization`.
 *
 * <p><b>Por que uma lista e não "todas as requisições":</b> o token é uma
 * credencial. Anexá-lo a tudo o que sai do `HttpClient` o entregaria também a
 * qualquer origem terceira que o app venha a chamar — CDN, telemetria, um asset
 * carregado por HTTP. A lista fecha isso por construção: o header só vai para
 * caminhos que sabidamente são o nosso backend.
 */
export const AUTH_BEARER_URL_PREFIXES = new InjectionToken<readonly string[]>(
  'AUTH_BEARER_URL_PREFIXES',
  { providedIn: 'root', factory: () => ['/oauth2', '/api'] },
);

/**
 * Marca uma requisição que **não** deve levar `Authorization`.
 *
 * <p>Usado no próprio login: pedir um token novo carregando o token velho é, na
 * melhor das hipóteses, ruído; na pior, faz o servidor decidir por uma
 * credencial que a pessoa está justamente tentando substituir.
 */
export const SKIP_AUTH_HEADER = new HttpContextToken<boolean>(() => false);

/** Rota da tela de login. Um lugar só, para guard e interceptor concordarem. */
export const ROTA_LOGIN = '/login';

/** Para onde vai quem acabou de entrar e não pediu destino nenhum. */
export const ROTA_HOME = '/';
