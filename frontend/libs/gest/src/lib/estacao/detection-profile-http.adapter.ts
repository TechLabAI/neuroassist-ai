import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import type {
  DetectionProfilePort,
  DetectionProfileDto,
  ProfileResult,
} from './detection-profile.port';
import { isNotModified, ProfileErro } from './detection-profile.port';

// =============================================================================
// DetectionProfileHttpAdapter — GET /api/v1/edge/profile (GEST-FE-028)
//
// Contrato lido do produtor:
//   DeviceProfileController (edge-service) — GET /api/v1/edge/profile
//   DeviceProfileResponse.java — ver detection-profile.port.ts para o DTO local
//
// Anti-spoofing por construção (EDGE-022):
//   O deviceId NÃO aparece em path, query ou corpo. O backend deriva do claim
//   `sub` do token — quem não pode forjar o token não pode solicitar o perfil
//   de outro dispositivo.
//
// Suporte a GET condicional (ETag / 304):
//   - Se currentEtag fornecido → header If-None-Match enviado
//   - Servidor retorna 304 → devolve { notModified: true } (sem corpo)
//   - Servidor retorna 200 → devolve DetectionProfileDto + novo ETag
//
// Códigos de erro distintos (SPEC-09 §3 — ProblemDetailFactory do edge-service):
//   - 403 edge.device-not-paired → ProfileErro('sem-vinculo')
//     Dispositivo registrado mas sem paciente vinculado — estado de primeira classe,
//     não erro genérico.
//   - 502 edge.profile-unavailable → ProfileErro('degradada')
//     GEST fora do ar — jamais tratar como "nenhum gesto configurado".
//
// Por que "observe: 'response'" é necessário:
//   HttpClient por padrão descarta os headers da resposta e retorna só o corpo.
//   Para ler o ETag de volta do 200 (e para detectar o 304 que vem sem corpo)
//   precisamos do HttpResponse completo.
// =============================================================================

void isNotModified; // evita TS "unused import" em strict mode

@Injectable()
export class DetectionProfileHttpAdapter implements DetectionProfilePort {
  private readonly http = inject(HttpClient);

  async fetchProfile(currentEtag?: string): Promise<ProfileResult> {
    const url = '/api/v1/edge/profile';

    const headers: Record<string, string> = {};
    if (currentEtag) {
      headers['If-None-Match'] = currentEtag;
    }

    let response: HttpResponse<DetectionProfileDto>;
    try {
      response = await firstValueFrom(
        this.http.get<DetectionProfileDto>(url, {
          headers: new HttpHeaders(headers),
          // 'response' para ler ETag e status code completos.
          observe: 'response',
        }),
      );
    } catch (err) {
      if (err instanceof HttpErrorResponse) {
        if (err.status === 304) {
          // HttpClient em versões mais antigas lança em vez de retornar para 304.
          return { notModified: true };
        }
        // Distinguir os dois códigos de erro específicos do endpoint (SPEC-09 §3):
        const code = (err.error as { error?: { code?: string } } | null)?.error?.code;

        if (err.status === 403 && code === 'edge.device-not-paired') {
          // Dispositivo sem paciente vinculado — estado `sem-vinculo`, não erro genérico.
          throw new ProfileErro('sem-vinculo');
        }
        if (err.status === 502 && code === 'edge.profile-unavailable') {
          // GEST fora do ar — estado `degradada`, nunca "nenhum gesto configurado".
          throw new ProfileErro('degradada');
        }
        throw new Error(`detection-profile: HTTP ${err.status}`);
      }
      throw err;
    }

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
  }
}
