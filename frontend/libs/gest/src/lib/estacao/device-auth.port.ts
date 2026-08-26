import { InjectionToken } from '@angular/core';

// =============================================================================
// DeviceAuthPort — inbound port (P2 — GEST-FE-027 / ADR-008 D1)
//
// A estação autentica-se como DISPOSITIVO via POST /oauth2/token/device do
// acs-service. Este port abstrai o mecanismo de transporte; o adapter HTTP
// é a implementação real; um adapter de teste é o mock.
//
// Contrato lido do produtor: AutenticacaoController.deviceToken() +
// DeviceTokenRequest.java + AutenticacaoResponse.java — NUNCA deduzido pelo nome.
// =============================================================================

/**
 * Corpo da requisição de token de dispositivo.
 *
 * Espelha {@code DeviceTokenRequest.java} campo por campo.
 * NÃO tem tenantId: o tenant vem de edge-service, não do chamador não autenticado.
 */
export interface DeviceTokenRequest {
  /** ID do dispositivo, na forma edge-<deviceId>, gerado no enroll. */
  readonly clientId: string;
  /**
   * Segredo do dispositivo, gerado no enroll e verificado por edge-service.
   *
   * LGPD / segurança: nunca aparece em log, em mensagem de erro ou no DOM.
   * ADR-008 P1: não pode morar no browser — piloto fechado em máquina dedicada apenas.
   */
  readonly clientSecret: string;
}

/** Resultado tipado do processo de autenticação de dispositivo. */
export interface DeviceAuthResult {
  readonly accessToken: string;
  /** Sempre null para tokens de dispositivo: não existe rotação de token. */
  readonly refreshToken: null;
  readonly tokenType: string;
  /** Duração em segundos — usado para agendar reautenticação proativa. */
  readonly expiresIn: number;
  /** UUID do tenant — extraído do campo `tenant` da resposta. */
  readonly tenantId: string;
  /** UUID do dispositivo — extraído do campo `sub` da resposta. */
  readonly deviceId: string;
  /** Authorities concedidas ao dispositivo. Deve conter 'edge.device'. */
  readonly authorities: readonly string[];
}

/** Contrato do port de autenticação de dispositivo. */
export abstract class DeviceAuthPort {
  abstract autenticar(request: DeviceTokenRequest): Promise<DeviceAuthResult>;
}

/** Token DI para o port de autenticação de dispositivo. */
export const DEVICE_AUTH_PORT = new InjectionToken<DeviceAuthPort>('DEVICE_AUTH_PORT');
