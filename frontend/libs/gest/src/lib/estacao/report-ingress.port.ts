// =============================================================================
// ReportIngressPort — porta de saída para POST /api/v1/reports (GEST-FE-030)
//
// Contrato lido do produtor:
//   ReportIngressController (evnt-service) — POST /api/v1/reports
//   ReportIngressUseCase.ReportIngressRequest.java
//
// Campos ausentes por design:
//   rawFramePayload  — NUNCA incluir; backend rejeita com 400 (EVNT-023).
//                      Invariante de privacidade: quadro biométrico fica no dispositivo.
//   deviceTenant     — NUNCA declarar; servidor sobrescreve com tenant_id do token.
//   patientTenant    — omitido; servidor usa o tokenTenant como default.
//
// Idempotência (I4 — SPEC-09):
//   reportId é derivado de deviceId + engineFeatureRef + janela de cooldown.
//   O mesmo gesto dentro da janela gera o mesmo reportId; o backend responde 200.
//   Um reenvio pela fila (P5) não vira segundo alarme para o mesmo pedido de ajuda.
// =============================================================================

/** Corpo de POST /api/v1/reports — espelha ReportIngressRequest.java */
export interface ReportRequest {
  /**
   * Identificador idempotente do relatório.
   * Derivado de `deviceId + engineFeatureRef + janela de cooldown (30s)`.
   * Mesmo reportId → backend responde 200 em vez de 201.
   */
  readonly reportId: string;
  /** UUID do paciente monitorado — vem do perfil (DetectionProfileStore). */
  readonly pacienteId: string;
  /**
   * Referência do comando no catálogo PAC (tipoGestoId UUID).
   * O EVNT usa este campo para buscar label e urgência via CommandLookupPort.
   * NUNCA inventar — vem do primitivo do perfil.
   */
  readonly commandRef: string;
  /** Primitivos detectados com confidence individual. */
  readonly primitives: readonly { readonly tipoGestoId: string; readonly confidence: number }[];
  /** Confidence combinada do gesto reconhecido (0.0–1.0). */
  readonly confidence: number;
  /** Versão do perfil em uso — vem de DetectionProfileStore.profileVersion(). */
  readonly profileVersion: number;
  /** Timestamp ISO 8601 de quando o gesto foi reconhecido. */
  readonly recognizedAt: string;
  /**
   * UUID do dispositivo — vem do JWT sub (DeviceSessionStore.deviceId()).
   * O backend valida contra o tenant do token; campo obrigatório.
   */
  readonly deviceId: string;
  // rawFramePayload: AUSENTE — EVNT-023 rejeita com 400 qualquer campo não declarado
  // deviceTenant: AUSENTE — sobrescrito pelo servidor via tokenTenant
}

/** Resposta de POST /api/v1/reports — espelha ReportIngressResponse.java */
export interface ReportResponse {
  readonly eventoId: string;
  /** 'created' = 201 (novo evento); 'idempotent' = 200 (mesmo reportId). */
  readonly status: 'created' | 'idempotent';
  readonly notification: { readonly tier: string; readonly caregiverId: string } | null;
  readonly createdAt: string;
}

/** Porta de saída — implementada por ReportIngressHttpAdapter. */
export abstract class ReportIngressPort {
  abstract submit(request: ReportRequest): Promise<ReportResponse>;
}
