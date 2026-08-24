import { Injectable, computed, signal } from '@angular/core';

// =============================================================================
// @neuroassist/shared/auth-session — a sessão do usuário no navegador.
//
// Era um stub de uma linha (`interface AuthSession { token: string | null }`), e
// isso tinha consequência: os guards do ACS documentam um "Wave-0 bypass" que
// deixa passar quando o token de sessão resolve para `null` — o padrão. Ou seja,
// os guards existiam e não guardavam nada.
//
// O que este arquivo NÃO faz, de propósito:
//
//   - não valida a assinatura do JWT. Quem valida é o resource server, com a
//     chave pública. Um navegador que "validasse" o token estaria se
//     autoconvencendo: as claims aqui servem para DESENHAR A TELA, nunca para
//     decidir acesso. Todo endpoint continua verificando por conta própria.
//   - não decide permissão. Ele expõe as authorities; quem decide o que fazer
//     com elas é quem consome.
// =============================================================================

/** Os três papéis que o acs-service emite, no mesmo texto do backend. */
export type Papel = 'COORDENADOR_CLINICA' | 'CUIDADORA' | 'PACIENTE_APOIO';

/** O que o token diz sobre quem está usando o sistema. */
export interface SessionUser {
  /** `sub` — o id da cuidadora/coordenadora/apoio. */
  readonly id: string;
  readonly nome: string;
  readonly papel: Papel;
  /** Empresa. Claim canônico `tenant_id` — os aliases foram removidos (E2E-021). */
  readonly tenantId: string;
  /** Authorities finas, como o ACS as emite: prefixo vazio, nome literal. */
  readonly authorities: readonly string[];
  /** Quando o token expira. */
  readonly expiraEm: Date;
}

/** Resposta de `POST /oauth2/token` do acs-service, espelhada campo por campo. */
export interface TokenResponse {
  readonly accessToken: string;
  readonly refreshToken: string | null;
  readonly tokenType: string;
  readonly expiresIn: number;
  readonly tenant: string | null;
  readonly sub: string | null;
  readonly papel: string | null;
  /** Nome de exibição. `null` em token de serviço/dispositivo — não há pessoa a nomear. */
  readonly nome: string | null;
  readonly authorities: readonly string[] | null;
}

/** Onde a sessão é guardada entre recarregamentos da página. */
const STORAGE_KEY = 'neuroassist.session';

/**
 * Estado da sessão no navegador.
 *
 * <p><b>Sobre guardar o token em `sessionStorage` e não em `localStorage`:</b> a
 * sessão morre quando a aba fecha. Num contexto clínico o mesmo computador é
 * usado por mais de uma pessoa, e um token que sobrevive ao fechamento da aba é
 * um token que a próxima pessoa herda. O custo é ter de entrar de novo ao
 * reabrir; o benefício é que "fechei o navegador" significa "saí".
 *
 * <p>Isto não protege contra XSS — nada em `sessionStorage` protege. O que
 * protege contra XSS é não ter XSS.
 */
@Injectable({ providedIn: 'root' })
export class AuthSessionStore {
  private readonly _token = signal<string | null>(null);
  private readonly _user = signal<SessionUser | null>(null);

  /** O token cru, para o interceptor anexar. */
  readonly token = this._token.asReadonly();

  /** Quem está usando o sistema, ou `null` se ninguém entrou. */
  readonly user = this._user.asReadonly();

  /** Há alguém autenticado e o token ainda não expirou. */
  readonly autenticado = computed(() => {
    const u = this._user();
    return u !== null && u.expiraEm.getTime() > Date.now();
  });

  constructor() {
    this.restaurar();
  }

  /**
   * Registra a sessão a partir da resposta do ACS.
   *
   * <p>As claims vêm do corpo da resposta e não do token decodificado. Isso evita
   * decodificar JWT no navegador — o que convidaria alguém, mais tarde, a "só
   * verificar rapidinho" a assinatura aqui, que é justamente o que o navegador
   * não pode fazer.
   *
   * <p>O `nome` faltava no corpo (existia só como claim), e a primeira versão
   * deste arquivo o deixava vazio. Acrescentei-o ao `AutenticacaoResponse` do
   * acs-service em vez de decodificar o token: escrever "Olá, Fulana" na tela não
   * justifica abrir a porta de decodificar JWT no cliente.
   */
  entrar(resposta: TokenResponse): void {
    const user: SessionUser = {
      id: resposta.sub ?? '',
      nome: resposta.nome ?? '',
      papel: (resposta.papel as Papel) ?? 'CUIDADORA',
      tenantId: resposta.tenant ?? '',
      authorities: resposta.authorities ?? [],
      expiraEm: new Date(Date.now() + resposta.expiresIn * 1000),
    };
    this._token.set(resposta.accessToken);
    this._user.set(user);
    this.persistir(resposta.accessToken, user);
  }

  /** Encerra a sessão. Idempotente. */
  sair(): void {
    this._token.set(null);
    this._user.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // sessionStorage indisponível (modo privado, política do navegador). A
      // sessão em memória já foi limpa, que é o que importa para sair.
    }
  }

  /** A pessoa tem esta authority? Comparação literal, sem prefixo e sem normalização. */
  pode(authority: string): boolean {
    return this._user()?.authorities.includes(authority) ?? false;
  }

  // ---------------------------------------------------------------------------

  private persistir(token: string, user: SessionUser): void {
    try {
      sessionStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ token, user: { ...user, expiraEm: user.expiraEm.toISOString() } }),
      );
    } catch {
      // Não poder persistir degrada para "a sessão dura enquanto a página viver".
      // Recusar o login por causa disso seria pior.
    }
  }

  private restaurar(): void {
    try {
      const cru = sessionStorage.getItem(STORAGE_KEY);
      if (!cru) return;
      const { token, user } = JSON.parse(cru) as {
        token: string;
        user: Omit<SessionUser, 'expiraEm'> & { expiraEm: string };
      };
      const expiraEm = new Date(user.expiraEm);
      // Token vencido não é sessão: descartar aqui evita que a tela desenhe um
      // menu de alguém que vai levar 401 na primeira chamada.
      if (expiraEm.getTime() <= Date.now()) {
        sessionStorage.removeItem(STORAGE_KEY);
        return;
      }
      this._token.set(token);
      this._user.set({ ...user, expiraEm });
    } catch {
      // Conteúdo corrompido: começar deslogado é a degradação correta.
    }
  }
}

/**
 * Contrato mínimo que os guards do ACS já esperam (`ACS_AUTH_SESSION`).
 *
 * <p>Mantido para que providenciar o store real acabe com o "Wave-0 bypass" sem
 * precisar tocar em cada guard.
 */
export interface AuthSession {
  readonly token: string | null;
}
