import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import type { ReportRequest, ReportResponse } from './report-ingress.port';
import { ReportIngressPort } from './report-ingress.port';

// =============================================================================
// ReportIngressHttpAdapter — POST /api/v1/reports (GEST-FE-030)
//
// Contrato lido do produtor:
//   ReportIngressController (evnt-service) — POST /api/v1/reports
//   Autenticação: edge.device authority (token de dispositivo, não de usuário)
//
// Garantias desta classe:
//   - rawFramePayload NUNCA está no corpo (EVNT-023: backend rejeita com 400)
//   - deviceTenant NUNCA está no corpo (servidor sobrescreve com o tenant do JWT)
//   - 201 → evento criado; 200 → idempotente (mesmo reportId)
//
// URL relativa: /api/v1/reports
//   O proxy de dev e o nginx de produção roteiam /api/v1/ para o gateway (evnt-service).
// =============================================================================

@Injectable()
export class ReportIngressHttpAdapter extends ReportIngressPort {
  private readonly http = inject(HttpClient);

  override async submit(request: ReportRequest): Promise<ReportResponse> {
    // Construir o corpo explicitamente para garantir que rawFramePayload e
    // deviceTenant jamais escapem — se ReportRequest evoluir e alguém adicionar
    // esses campos, eles não chegarão ao backend por esta construção.
    const body = {
      reportId: request.reportId,
      pacienteId: request.pacienteId,
      commandRef: request.commandRef,
      primitives: request.primitives.map(p => ({
        tipoGestoId: p.tipoGestoId,
        confidence: p.confidence,
      })),
      confidence: request.confidence,
      profileVersion: request.profileVersion,
      recognizedAt: request.recognizedAt,
      deviceId: request.deviceId,
      // rawFramePayload: AUSENTE (EVNT-023)
      // deviceTenant: AUSENTE (sobrescrito pelo servidor)
    };

    return firstValueFrom(
      this.http.post<ReportResponse>('/api/v1/reports', body),
    );
  }
}
