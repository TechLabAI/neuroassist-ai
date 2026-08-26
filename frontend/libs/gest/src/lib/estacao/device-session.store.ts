import { Injectable, signal, computed, inject, isDevMode } from '@angular/core';
import type { DeviceAuthPort, DeviceTokenRequest } from './device-auth.port';
import { DeviceAuthErro, DeviceAuthHttpAdapter } from './device-auth-http.adapter';

// =============================================================================
// DeviceSessionStore — identidade do dispositivo (P2 — GEST-FE-027 / ADR-008 D1)
//
// A estação autentica-se como DISPOSITIVO, não como usuário.
// Máquina de estados:
//
//   'desconhecido' ──autenticar()──> 'autenticando'
//   'autenticando' ──sucesso──────> 'autenticado' (+ timer de reauth proativa)
//   'autenticando' ──401──────────> 'credencial-invalida' (TERMINAL — sem retry)
//   'autenticando' ──rede/servidor> 'desconhecido' (pode tentar novamente)
//   'autenticado'  ──timer@80%───> 'autenticando' (reauth proativa silenciosa)
//   'autenticado'  ──erro reauth──> 'credencial-invalida' (TERMINAL se 401)
//
// Invariantes (ADR-008 P1 + RULE-NX-07):
//   - clientSecret nunca persiste, nunca aparece em log, nunca entra em signals
//   - 401 → estado terminal 'credencial-invalida'; zero retry silencioso
//   - Reauth proativa a 80% do expiresIn evita expiração durante operação
//   - deviceId vem do campo `sub` da resposta (nunca de configuração local)
//   - tenantId vem do campo `tenant` da resposta (nunca de configuração local)
//
// AVISO DE SEGURANÇA (ADR-008 P1 — pendência aberta):
//   clientSecret de client-credentials NÃO pode morar no browser de uso geral.
//   Esta implementação vale SOMENTE para piloto fechado em máquina dedicada,
//   onde a credencial vem de variável de ambiente configurada no sistema operacional.
//   Uso em produção geral está BLOQUEADO até que exista esquema de credencial
//   por dispositivo com armazenamento auditável e limpável (ADR-008 P1).
// =============================================================================

export type EstadoSessao =
  | 'autenticando'
  | 'autenticado'
  | 'credencial-invalida'
  | 'desconhecido';

/** Aviso obrigatório exibido em qualquer tela de configuração de credencial. */
export const AVISO_CREDENCIAL_DISPOSITIVO =
  'AVISO DE SEGURANÇA: a credencial do dispositivo (clientSecret) não pode morar ' +
  'no browser de uso geral. Esta estação é válida SOMENTE para piloto fechado em ' +
  'máquina dedicada, onde a credencial vem de variável de ambiente do sistema ' +
  'operacional. Uso em produção geral está BLOQUEADO (ADR-008 P1 — pendência aberta).';

@Injectable({ providedIn: 'root' })
export class DeviceSessionStore {
  // Usamos o adapter HTTP diretamente como default — quem quiser trocar injeta
  // DEVICE_AUTH_PORT. Testes usam TestBed.overrideProvider.
  // DeviceAuthHttpAdapter é @Injectable() sem providedIn, então precisamos declará-lo.
  private readonly authPort: DeviceAuthPort = inject(DeviceAuthHttpAdapter);

  // ---------------------------------------------------------------------------
  // Gancho E2E (dev mode only — GEST-FE-033)
  //
  // Em build de desenvolvimento, `sessionStorage` pode conter uma semente com
  // token, deviceId e tenantId. Quando encontrada, o store arranca já no estado
  // 'autenticado', permitindo que specs Playwright naveguem para /estacao sem
  // precisar chamar POST /oauth2/token/device contra o backend real.
  //
  // Chave: '__gest.e2e.device-session'
  // Formato: { token: string, deviceId: string, tenantId: string }
  //
  // `isDevMode()` é falso em build de produção; o bloco é removido pelo tree-
  // shaker. Um dispositivo de paciente nunca chega a este código.
  // ---------------------------------------------------------------------------

  // ---------------------------------------------------------------------------
  // Estado interno (privado)
  // ---------------------------------------------------------------------------

  private readonly _token = signal<string | null>(null);
  private readonly _deviceId = signal<string | null>(null);
  private readonly _tenantId = signal<string | null>(null);
  private readonly _estado = signal<EstadoSessao>('desconhecido');

  constructor() {
    if (isDevMode()) {
      try {
        const raw = sessionStorage.getItem('__gest.e2e.device-session');
        if (raw) {
          const seed = JSON.parse(raw) as { token: string; deviceId: string; tenantId: string };
          this._token.set(seed.token);
          this._deviceId.set(seed.deviceId);
          this._tenantId.set(seed.tenantId);
          this._estado.set('autenticado');
        }
      } catch {
        // sessionStorage indisponível ou JSON inválido — arrancar como desconhecido.
      }
    }
  }

  private reauthTimer: ReturnType<typeof setTimeout> | null = null;
  private reauthCredential: DeviceTokenRequest | null = null;

  // ---------------------------------------------------------------------------
  // API pública — signals readonly
  // ---------------------------------------------------------------------------

  /** Token de acesso atual, ou null se não autenticado. */
  readonly token = this._token.asReadonly();

  /**
   * ID do dispositivo, extraído do campo `sub` da resposta do ACS.
   * NUNCA de configuração local — o backend rejeita corpo que declare outro
   * dispositivo (403 edge.identity-mismatch no heartbeat).
   */
  readonly deviceId = this._deviceId.asReadonly();

  /**
   * ID do tenant, extraído do campo `tenant` da resposta do ACS.
   * NUNCA de configuração local.
   */
  readonly tenantId = this._tenantId.asReadonly();

  /** Estado atual da sessão de dispositivo. */
  readonly estado = this._estado.asReadonly();

  /** True quando autenticado e token não expirou (alias de conveniência). */
  readonly autenticado = computed(() => this._estado() === 'autenticado');

  /**
   * Aviso de segurança obrigatório (ADR-008 P1).
   * Deve ser exibido em toda tela de configuração de credencial de dispositivo.
   */
  readonly avisoSeguranca = AVISO_CREDENCIAL_DISPOSITIVO;

  // ---------------------------------------------------------------------------
  // Autenticação
  // ---------------------------------------------------------------------------

  /**
   * Autentica o dispositivo com as credenciais fornecidas.
   *
   * - 401 → estado 'credencial-invalida' (TERMINAL — não tente novamente automaticamente)
   * - rede/servidor → estado 'desconhecido' (pode tentar novamente)
   * - sucesso → estado 'autenticado'; agenda reauth proativa a 80% do expiresIn
   *
   * SEGURANÇA: clientSecret nunca é persistido, nunca entra em signal,
   * nunca aparece em log nem em mensagem de erro.
   */
  async autenticar(clientId: string, clientSecret: string): Promise<void> {
    // Guardar credencial para reauth proativa (referência mantida em memória,
    // nunca exposta via signal ou DOM).
    this.reauthCredential = { clientId, clientSecret };

    this._estado.set('autenticando');
    this.cancelarTimer();

    try {
      const resultado = await this.authPort.autenticar({ clientId, clientSecret });

      this._token.set(resultado.accessToken);
      this._deviceId.set(resultado.deviceId);
      this._tenantId.set(resultado.tenantId);
      this._estado.set('autenticado');

      // Agendar reautenticação proativa a 80% do tempo de vida do token.
      // Não há refreshToken: a única forma de renovar é reautenticar.
      this.agendarReauth(resultado.expiresIn);
    } catch (err) {
      this.cancelarTimer();

      if (err instanceof DeviceAuthErro && err.motivo === 'credencial-invalida') {
        // Estado terminal. Um dispositivo que tenta reautenticar para sempre com
        // credencial inválida parece vivo e não está (ADR-008 consequência de D1).
        this._token.set(null);
        this._deviceId.set(null);
        this._tenantId.set(null);
        this._estado.set('credencial-invalida');
        // Limpar credencial armazenada para reauth — credencial inválida não deve ser retentada.
        this.reauthCredential = null;
      } else {
        // Falha de rede ou servidor — pode tentar novamente.
        this._token.set(null);
        this._estado.set('desconhecido');
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Reautenticação proativa (privada)
  // ---------------------------------------------------------------------------

  private agendarReauth(expiresIn: number): void {
    // 80% do tempo de vida → reautenticar antes da expiração
    const delayMs = Math.floor(expiresIn * 0.8 * 1000);

    this.reauthTimer = setTimeout(() => {
      const cred = this.reauthCredential;
      if (cred && this._estado() === 'autenticado') {
        // clientSecret segue em memória apenas — nunca em signal, nunca em log.
        this.autenticar(cred.clientId, cred.clientSecret).catch(() => {
          // Erro de reauth já tratado dentro de autenticar().
          // 401 na reauth → credencial-invalida (terminal).
          // Rede/servidor → desconhecido; próxima tentativa manual.
        });
      }
    }, delayMs);
  }

  private cancelarTimer(): void {
    if (this.reauthTimer !== null) {
      clearTimeout(this.reauthTimer);
      this.reauthTimer = null;
    }
  }
}
