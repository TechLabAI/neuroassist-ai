// =============================================================================
// DetectionProfilePort — porta de entrada do perfil de detecção (GEST-FE-028)
//
// Espelha o endpoint GET /api/v1/edge/profile do DeviceProfileController
// do edge-service (EDGE-022). O deviceId é derivado do claim `sub` do token
// no backend — não aparece em path, query ou corpo.
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

/**
 * Resposta canônica de GET /api/v1/edge/profile (DeviceProfileResponse.java).
 * Campos obrigatórios do edge-service: version, catalogoDegradado, etag, thresholds,
 * primitives, pacienteId. Campos opcionais são do antigo endpoint gest-service direto
 * e virão undefined — mantidos no tipo para compatibilidade com fixtures de P3.
 */
export interface DetectionProfileDto {
  readonly version: number;
  readonly catalogoDegradado: boolean;
  readonly etag: string | null;
  readonly thresholds: ThresholdsTable;
  readonly primitives: readonly PrimitivoDeteccao[];
  readonly pacienteId: string | null;
  // Campos do antigo endpoint direto gest-service — ausentes na resposta edge-service:
  readonly id?: string;
  readonly active?: boolean;
  readonly contractVersion?: string;
  readonly catalogoVersao?: number | null;
  readonly publishedAt?: string;
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

/**
 * Erro tipado do perfil de detecção — permite ao store distinguir estados:
 * - 'sem-vinculo': 403 edge.device-not-paired — dispositivo sem paciente pareado
 * - 'degradada':  502 edge.profile-unavailable — GEST fora do ar
 * - 'outro':      qualquer outro erro HTTP ou de rede
 */
export class ProfileErro extends Error {
  constructor(readonly tipo: 'sem-vinculo' | 'degradada' | 'outro') {
    super(`detection-profile: ${tipo}`);
    this.name = 'ProfileErro';
  }
}

/** Porta de saída — implementada por DetectionProfileHttpAdapter. */
export interface DetectionProfilePort {
  /**
   * Busca o perfil de detecção do dispositivo autenticado.
   * O deviceId é derivado do claim `sub` do token pelo backend — não é passado aqui.
   * Se `currentEtag` for fornecido e o servidor retornar 304, devolve `{ notModified: true }`.
   *
   * @throws ProfileErro com tipo 'sem-vinculo' em 403 edge.device-not-paired
   * @throws ProfileErro com tipo 'degradada' em 502 edge.profile-unavailable
   * @throws Error em caso de outro erro HTTP ou de rede.
   */
  fetchProfile(currentEtag?: string): Promise<ProfileResult>;
}
