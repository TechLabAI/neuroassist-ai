// =============================================================================
// DetectionProfilePort — porta de entrada do perfil de detecção (GEST-FE-028)
//
// Espelha o endpoint GET /gest-service/detection-profile/devices/{deviceId}
// do DetectionProfileController do gest-service.
// =============================================================================

import type { DetectionThresholds } from '@neuroassist/shared/contracts';

/** Faixa de sensibilidade — espelha Sensibilidade.java */
export type SensibilidadeNivel = 'baixa' | 'media' | 'alta';

/** Origem do limiar de um primitivo — espelha DetectionProfilePrimitiveResponse.Origem */
export type OrigemPerfil = 'PACIENTE' | 'PERFIL_EMPRESA' | 'INDISPONIVEL';

/** Um gesto ativo no perfil com a faixa de sensibilidade a aplicar. */
export interface PrimitivoDeteccao {
  /** UUID do tipo de gesto no catálogo. */
  readonly tipoGestoId: string;
  readonly nome: string;
  /** Referência do motor — deve existir em ENGINE_FEATURE para ser detectável. */
  readonly engineFeatureRef: string;
  /** Faixa de sensibilidade a usar ao detectar este gesto. */
  readonly sensibilidade: SensibilidadeNivel;
  /**
   * De onde veio a faixa:
   * - PACIENTE: da calibração deste paciente
   * - PERFIL_EMPRESA: ninguém calibrou; usa a faixa do perfil da empresa
   * - INDISPONIVEL: catálogo não pôde ser lido — primitivo deve ser descartado
   */
  readonly origem: OrigemPerfil;
}

/** Tabela completa das três faixas de sensibilidade do perfil. */
export interface ThresholdsTable {
  readonly baixa: DetectionThresholds;
  readonly media: DetectionThresholds;
  readonly alta: DetectionThresholds;
}

/** Resposta canônica de GET /gest-service/detection-profile/devices/{deviceId} */
export interface DetectionProfileDto {
  readonly id: string;
  readonly version: number;
  readonly active: boolean;
  readonly contractVersion: string;
  readonly thresholds: ThresholdsTable;
  readonly primitives: readonly PrimitivoDeteccao[];
  readonly pacienteId: string | null;
  readonly catalogoVersao: number | null;
  readonly catalogoDegradado: boolean;
  readonly publishedAt: string;
  readonly etag: string | null;
}

/** 304 Not Modified — ETag bate; perfil em memória está atual. */
export interface ProfileNotModified {
  readonly notModified: true;
}

/** Resultado de fetchProfile. */
export type ProfileResult = DetectionProfileDto | ProfileNotModified;

/** Discriminador de tipo. */
export function isNotModified(r: ProfileResult): r is ProfileNotModified {
  return (r as ProfileNotModified).notModified === true;
}

/** InjectionToken para a porta — ver gest.providers.ts */
export const DETECTION_PROFILE_PORT = Symbol('DETECTION_PROFILE_PORT');

/** Porta de saída — implementada por DetectionProfileHttpAdapter. */
export interface DetectionProfilePort {
  /**
   * Busca o perfil de detecção do dispositivo.
   * Se `currentEtag` for fornecido e o servidor retornar 304, devolve `{ notModified: true }`.
   *
   * @throws Error em caso de falha de rede ou resposta inesperada.
   */
  fetchProfile(deviceId: string, currentEtag?: string): Promise<ProfileResult>;
}
