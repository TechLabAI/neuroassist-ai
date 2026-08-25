import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpContext } from '@angular/common/http';
import { SKIP_AUTH_HEADER } from '@neuroassist/shared/contracts';
import { firstValueFrom } from 'rxjs';
import type { DeviceAuthPort, DeviceAuthResult, DeviceTokenRequest } from './device-auth.port';

// =============================================================================
// DeviceAuthHttpAdapter — POST /oauth2/token/device (P2 — GEST-FE-027)
//
// Contrato lido do produtor:
//   AutenticacaoController.deviceToken() (acs-service)
//   DeviceTokenRequest.java  — { clientId: @NotBlank, clientSecret: @NotBlank }
//   AutenticacaoResponse.java — { accessToken, refreshToken=null, tokenType,
//                                  expiresIn, tenant, sub, papel=null, nome=null, authorities }
//
// DTO local (DeviceAuthResponseDto) espelha AutenticacaoResponse.java campo por
// campo — a resposta de dispositivo usa um subconjunto dela (refreshToken, papel e
// nome vêm nulos), e o DTO local torna esse subconjunto explícito no tipo.
//
// Segurança (ADR-008 P1 + RULE-NX-07):
//   - clientSecret nunca aparece em log, em mensagem de erro ou no DOM
//   - Todo erro 401 retorna uma exceção genérica sem detalhar o motivo
//
// POR QUE SKIP_AUTH_HEADER É OBRIGATÓRIO AQUI — e não redundante
//
// A primeira versão deste arquivo o omitia, argumentando que "a estação é máquina
// dedicada, não há sessão de usuário, então o header nunca seria anexado". Isso é
// uma suposição sobre a implantação, não uma garantia do código — e ela quebra no
// fluxo mais provável de todos: alguém entra no sistema NAQUELA máquina para parear
// o dispositivo, o token dessa pessoa fica no sessionStorage, e depois expira.
//
// A partir daí, `authBearerInterceptorFn` anexa o token velho a esta requisição:
// `AUTH_BEARER_URL_PREFIXES` inclui `/oauth2`, e `/oauth2/token/device` casa com o
// prefixo. E o endpoint, embora `permitAll`, fica ATRÁS do filtro do resource
// server — que rejeita o token inválido ANTES de o controller ser alcançado.
//
// Medido contra o acs-service em execução (2026-08-25), e os códigos denunciam a
// diferença:
//
//   sem Authorization ............ 401 acs.auth.invalid-credentials
//                                  (chegou ao endpoint; credencial conferida)
//   com Bearer de usuário velho .. 401 acs.auth-unauthenticated
//                                  "Malformed payload" — NÃO chegou ao endpoint
//
// Os dois são 401, e é isso que torna o defeito cruel: `DeviceSessionStore` trata
// 401 como estado TERMINAL `credencial-invalida`, sem retry. A estação recusaria
// iniciar acusando a credencial do dispositivo, quando a causa é um resto de sessão
// de usuário — diagnóstico errado, num estado do qual ela não sai sozinha.
//
// O token vem de `@neuroassist/shared/contracts`, e não de `auth-login`, por dois
// motivos que só apareceram no build da APLICAÇÃO (o Jest passava): o
// `tsconfig.lib.json` do gest deixa passar apenas `contracts`, e `auth-login` é
// `type:feature` como o GEST — feature importando feature viola a fronteira do Nx.
// `auth-login` reexporta o mesmo símbolo, então há uma instância só do token.
//
// Por que URL relativa `/oauth2/token/device`:
//   acs-service rejeita CORS em chamadas diretas a :8088.
//   O proxy de dev e o nginx de produção roteiam `/oauth2/` para o gateway,
//   que encaminha para o acs-service. Mesmo caminho em dev e em produção.
// =============================================================================

/**
 * DTO local — espelha AutenticacaoResponse.java campo por campo.
 * Para tokens de dispositivo: refreshToken=null, papel=null, nome=null.
 */
interface DeviceAuthResponseDto {
  readonly accessToken: string;
  readonly refreshToken: string | null;
  readonly tokenType: string;
  readonly expiresIn: number;
  /** UUID do tenant — claim `tenant_id` no JWT, campo `tenant` na resposta. */
  readonly tenant: string | null;
  /** UUID do dispositivo — claim `sub` no JWT, campo `sub` na resposta. */
  readonly sub: string | null;
  readonly papel: string | null;
  readonly nome: string | null;
  readonly authorities: readonly string[] | null;
}

/** Erro tipado para autenticação de dispositivo — sem detalhe que revele credencial. */
export class DeviceAuthErro extends Error {
  constructor(readonly motivo: 'credencial-invalida' | 'rede' | 'servidor') {
    super(`device-auth falhou: ${motivo}`);
    this.name = 'DeviceAuthErro';
  }
}

@Injectable()
export class DeviceAuthHttpAdapter implements DeviceAuthPort {
  private readonly http = inject(HttpClient);

  async autenticar(request: DeviceTokenRequest): Promise<DeviceAuthResult> {
    try {
      const resposta = await firstValueFrom(
        this.http.post<DeviceAuthResponseDto>(
          '/oauth2/token/device',
          // Corpo espelha DeviceTokenRequest.java campo por campo.
          // Nenhum campo extra — backend valida apenas clientId e clientSecret.
          { clientId: request.clientId, clientSecret: request.clientSecret },
          // Ver o bloco no topo: sem isto, um token de usuário esquecido no
          // navegador derruba a autenticação do dispositivo com um 401 que a
          // estação interpreta como "credencial inválida" — e é terminal.
          { context: new HttpContext().set(SKIP_AUTH_HEADER, true) },
        ),
      );

      // sub e tenant vêm do corpo da resposta — não decodificamos o JWT no browser.
      // Ver AuthSessionStore.entrar() pelo mesmo raciocínio: quem valida assinatura
      // é o resource server. Decodificar no cliente é convidar alguém a "validar" aqui.
      const deviceId = resposta.sub ?? '';
      const tenantId = resposta.tenant ?? '';

      return {
        accessToken: resposta.accessToken,
        refreshToken: null,
        tokenType: resposta.tokenType,
        expiresIn: resposta.expiresIn,
        deviceId,
        tenantId,
        authorities: resposta.authorities ?? [],
      };
    } catch (err) {
      if (err instanceof HttpErrorResponse) {
        if (err.status === 401) {
          // clientSecret nunca revelado. 401 pode ser clientId errado,
          // clientSecret errado, dispositivo aposentado ou edge-service indisponível.
          // Todas as causas retornam o mesmo motivo (LGPD + segurança).
          throw new DeviceAuthErro('credencial-invalida');
        }
        if (err.status === 0) {
          throw new DeviceAuthErro('rede');
        }
        throw new DeviceAuthErro('servidor');
      }
      throw new DeviceAuthErro('rede');
    }
  }
}
