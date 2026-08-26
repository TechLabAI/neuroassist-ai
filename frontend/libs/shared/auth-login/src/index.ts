// =============================================================================
// @neuroassist/shared/auth-login — a tela de login e o que a sessão precisa
// para valer no resto do app (interceptor, guards, botão de sair).
//
// Mora em `shared` e não num módulo de negócio porque autenticar é ANTERIOR a
// qualquer módulo: não há empresa, paciente nem dispositivo antes de existir
// alguém autenticado. Por isso também não passa pelo `provideAcs()` — o login
// fala com o acs-service por conta própria (ver `auth-api.service.ts`).
// =============================================================================

export { LoginPageComponent } from './lib/login-page.component';
export { SairComponent } from './lib/sair.component';

export { AuthApiService, LoginFalhou } from './lib/auth-api.service';
export type { LoginErro } from './lib/auth-api.service';

export { authBearerInterceptorFn } from './lib/auth.interceptor';
export { sessaoRequeridaGuard, sessaoAusenteGuard } from './lib/auth.guard';

export { provideAuth, AUTH_ROUTES } from './lib/auth.providers';
export type { AuthConfig } from './lib/auth.providers';

export {
  AUTH_API_BASE,
  AUTH_BEARER_URL_PREFIXES,
  SKIP_AUTH_HEADER,
  ROTA_LOGIN,
  ROTA_HOME,
} from './lib/auth.config';

// Apoio a teste — usado pelas suítes de e2e para semear sessão antes do primeiro
// `goto`. Exportado daqui (e não por caminho profundo) para não obrigar as
// suítes a furar a fronteira do Nx. Ver `testing/e2e-session.ts`.
// Helpers de e2e vivem em '@neuroassist/shared/auth-login/testing' — fora deste
// barrel de propósito: ele exporta componentes Angular, que o runner do Playwright
// não consegue carregar, e fixture de token não deve ser alcançável do aplicativo.
export { RESPOSTA_TOKEN_COORD } from './testing/token-fixtures';
