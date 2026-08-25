import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import type {
  DetectionProfilePort,
  DetectionProfileDto,
  ProfileResult,
} from './detection-profile.port';
import { isNotModified } from './detection-profile.port';
import { GEST_SERVICE_BASE_URL } from '../api/gest-service.config';

// =============================================================================
// DetectionProfileHttpAdapter — GET /gest-service/detection-profile/devices/{deviceId}
// (GEST-FE-028)
//
// Contrato lido do produtor:
//   DetectionProfileController.findForDevice()
//   DetectionProfileResponse.java — ver detection-profile.port.ts para o DTO local
//
// Suporte a GET condicional (ETag / 304):
//   - Se currentEtag fornecido → header If-None-Match enviado
//   - Servidor retorna 304 → devolve { notModified: true } (sem corpo)
//   - Servidor retorna 200 → devolve DetectionProfileDto + novo ETag
//
// Por que "observe: 'response'" é necessário:
//   HttpClient por padrão descarta os headers da resposta e retorna só o corpo.
//   Para ler o ETag de volta do 200 (e para detectar o 304 que vem sem corpo)
//   precisamos do HttpResponse completo. O adapter lê os headers e normaliza.
//
// TODO[GEST-FE-028][blocked] — transporte pendente de decisão de backend.
//   O token de dispositivo (`edge.device`) recebe 403 neste endpoint porque
//   `DeviceTokenService.DEVICE_AUTHORITIES = List.of("edge.device")` e o
//   endpoint exige `gest.detection-profile.read`.
//   Um token `edge.device` alcança exatamente dois endpoints: POST /api/v1/edge/heartbeat
//   e POST /api/v1/reports. O DTO, o adapter e o store estão prontos para quando
//   a autoridade for concedida ao dispositivo (via backend ADR) ou o perfil chegar
//   por outro canal (ex.: edge-service como proxy). NÃO implementar chamada em
//   produção enquanto o backend não conceder a autoridade — 403 silencioso.
// =============================================================================

/** Alias interno — não reexportamos; o store usa o DTO via porta. */
void isNotModified; // apenas para evitar TS "unused import" em strict mode

@Injectable()
export class DetectionProfileHttpAdapter implements DetectionProfilePort {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(GEST_SERVICE_BASE_URL);

  async fetchProfile(deviceId: string, currentEtag?: string): Promise<ProfileResult> {
    const url = `${this.baseUrl}/gest-service/detection-profile/devices/${deviceId}`;

    const headers: Record<string, string> = {};
    if (currentEtag) {
      headers['If-None-Match'] = currentEtag;
    }

    try {
      const response = await firstValueFrom(
        this.http.get<DetectionProfileDto>(url, {
          headers: new HttpHeaders(headers),
          // 'response' para ler ETag e status code completos.
          observe: 'response',
        }),
      );

      if (response.status === 304) {
        return { notModified: true };
      }

      const body = response.body;
      if (!body) {
        throw new Error('detection-profile: resposta 200 sem corpo');
      }

      // Propagar o ETag da resposta para o store poder usá-lo na próxima chamada.
      const etag = response.headers.get('ETag') ?? body.etag ?? null;
      return { ...body, etag };
    } catch (err) {
      if (err instanceof HttpErrorResponse) {
        if (err.status === 304) {
          // HttpClient em versões mais antigas lança em vez de retornar para 304.
          return { notModified: true };
        }
        // 404: dispositivo sem perfil publicado — erro legítimo, o store decide.
        // 403: não pareado — idem.
        // 0:   rede.
        throw new Error(
          `detection-profile: HTTP ${err.status} ao buscar perfil do dispositivo ${deviceId}`,
        );
      }
      throw err;
    }
  }
}
