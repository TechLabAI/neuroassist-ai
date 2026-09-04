// =============================================================================
// trei.types.ts — TREI domain types (FE)
// Source of truth: docs/architecture/backend/services/trei-service/contracts/api-contract.md
// DREI has no BA prototype; types are derived directly from the api-contract (D-493).
//
// Two distinct authoring surfaces:
//   1. Training session conversation (ChatConversationPanel) — managed by TrainingSessionFacade
//   2. Proposal review (ProposedNodeCard list)              — managed by ProposalReviewFacade
// =============================================================================

// ---------------------------------------------------------------------------
// Core domain primitives
// ---------------------------------------------------------------------------

export type TreiLocale = 'pt' | 'en';
export type TreiNodeTipo = 'frase' | 'categoria';
export type SubEstado = 'rascunho' | 'aguardando_aprovacao' | 'publicando' | 'ativo' | 'descartado';
export type ContentStatus = 'pendente' | 'ativo';
export type ScreenStatus = 'idle' | 'loading' | 'loaded' | 'empty' | 'error';

// ---------------------------------------------------------------------------
// Training Session — §2.1 POST /api/v1/trei/sessoes
// ---------------------------------------------------------------------------

/** Request to start a training session. */
export interface StartSessionRequest {
  pacienteId: string;
  localeAutoria: TreiLocale;
}

/** Response from POST /api/v1/trei/sessoes (201). */
export interface TrainingSession {
  sessaoId: string;
  pacienteId: string;
  localeAutoria: TreiLocale;
  estado: 'ativa' | 'encerrada';
  llmModelId: string;
  iniciadaEm: string;
}

// ---------------------------------------------------------------------------
// Proposal Turn — §2.2 POST /api/v1/trei/sessoes/{sessaoId}/propostas
// ---------------------------------------------------------------------------

/** Request body for a proposal turn. */
export interface ProposeRequest {
  instrucao: string;
  parentNoId: string;
  tipo: TreiNodeTipo;
}

/** A single LLM-generated proposal (inert until materialized). */
export interface LlmProposal {
  propostaId: string;
  texto: string;
  tipo: TreiNodeTipo;
  locale: TreiLocale;
}

/** Response from POST /api/v1/trei/sessoes/{sessaoId}/propostas (200). */
export interface ProposalTurnResponse {
  propostas: LlmProposal[];
  aiDisclosed: boolean;
}

// ---------------------------------------------------------------------------
// Draft Node — §2.3 POST /api/v1/trei/nos (materialize)
// ---------------------------------------------------------------------------

/** Request to materialize a proposal into a draft node. */
export interface MaterializeRequest {
  pacienteId: string;
  propostaId: string | null;  // null for hand-typed phrases
  parentNoId: string;
  tipo: TreiNodeTipo;
  locale: TreiLocale;
  texto: string;
  engineFeatureRef: string | null;
}

/** Response from POST /api/v1/trei/nos (201). */
export interface DraftNodeCreated {
  noId: string;
  locale: TreiLocale;
  subEstado: SubEstado;
  confirmadoCuidadora: boolean;
  confirmadoPaciente: boolean;
  contentStatus: ContentStatus;
}

// ---------------------------------------------------------------------------
// Node Detail — §2.6 GET /api/v1/trei/nos/{noId}
// ---------------------------------------------------------------------------

export interface NodeLocaleDetail {
  locale: TreiLocale;
  texto: string | null;
  subEstado: SubEstado;
  confirmadoCuidadora: boolean;
  confirmadoPaciente: boolean;
  embeddingModelId?: string;
  audioRef: { present: boolean; formato?: string };
  publicadoEm?: string;
}

export interface NodeDetail {
  noId: string;
  pacienteId: string;
  tipo: TreiNodeTipo;
  parentNoId: string;
  locales: NodeLocaleDetail[];
}

// ---------------------------------------------------------------------------
// Caregiver Approval — §2.4 POST /api/v1/trei/nos/{noId}/aprovacoes/cuidadora
// ---------------------------------------------------------------------------

export interface ApprovalRequest {
  locale: TreiLocale;
}

export interface CaregiverApprovalResponse {
  noId: string;
  locale: TreiLocale;
  confirmadoCuidadora: boolean;
  confirmadoPaciente: boolean;
  subEstado: SubEstado;
  publicacaoDisparada: boolean;
}

// ---------------------------------------------------------------------------
// Publication Status — §2.8 GET /api/v1/trei/nos/{noId}/publicacao
// ---------------------------------------------------------------------------

export type PublicationPassoEstado = 'ok' | 'falha' | 'pendente';

export interface PublicationStep {
  passo: 'embed' | 'tts' | 'index';
  estado: PublicationPassoEstado;
  tentativas?: number;
}

export interface PublicationStatus {
  noId: string;
  locale: TreiLocale;
  subEstado: SubEstado;
  passos: PublicationStep[];
}

// ---------------------------------------------------------------------------
// Health — §2.9 GET /api/v1/trei/health
// ---------------------------------------------------------------------------

export type ServiceReachability = 'REACHABLE' | 'UNREACHABLE';

export interface TreiHealth {
  status: 'UP' | 'DEGRADED';
  anthropic: ServiceReachability;
  elevenlabs: ServiceReachability;
  embeddingSidecar: ServiceReachability;
  elasticsearch: ServiceReachability;
  audioStore: ServiceReachability;
  acs: ServiceReachability;
  embedModelId: string;
  degradedCapabilities: string[];
}

// ---------------------------------------------------------------------------
// FE-only view models (assembled by facades from API DTOs)
// ---------------------------------------------------------------------------

/** A message in the chat conversation — assembled from session + turns. */
export interface ChatMessage {
  id: string;
  role: 'caregiver' | 'assistant';
  content: string;
  timestamp: string;
  proposals?: LlmProposal[];  // only on assistant messages that proposed nodes
  aiDisclosed?: boolean;
}

/** View model for a draft node card (ProposedNodeCard — SPEC-04). */
export interface DraftNodeVm {
  noId: string;
  locale: TreiLocale;
  texto: string;
  tipo: TreiNodeTipo;
  subEstado: SubEstado;
  confirmadoCuidadora: boolean;
  confirmadoPaciente: boolean;
  approvalInProgress: boolean;   // true while POST /aprovacoes/cuidadora is in-flight
  discardInProgress: boolean;    // true while DELETE /nos/{noId}?locale= is in-flight
}

/** Assembled training session screen state. */
export interface TrainingSessionScreenVm {
  session: TrainingSession | null;
  messages: ChatMessage[];
  activeLocale: TreiLocale;
  status: ScreenStatus;
  isAssistantThinking: boolean;  // LLM call in-flight
  error: AppError | null;
  // P7 (FE-025 / G4): patient-not-found specific detection — avoids generic error panel
  isPatientNotFound: boolean;
  // P7 (FE-025): health-degraded affordance — true when running on mock adapter
  isDegraded: boolean;
  // P7 (INT-005 / G5-link): embed model readiness for CONV integration display
  embedModelId: string | null;
}

/** Assembled proposal review screen state. */
export interface ProposalReviewScreenVm {
  nodes: DraftNodeVm[];
  activeLocale: TreiLocale;
  status: ScreenStatus;
  error: AppError | null;
}

/** Shared error model (matches shared/error-model pattern). */
export interface AppError {
  code: string;
  message: string;
  status?: number;
  correlationId?: string;
}

// ---------------------------------------------------------------------------
// View model types required by the plan P2 contract (SPEC-03 §2)
// These sit alongside the existing FE-only view models above.
// ---------------------------------------------------------------------------

/** Session lifecycle status (view-model layer). */
export type SessionStatus = 'ACTIVE' | 'ENDED' | 'ERROR';

/**
 * Node approval sub-state (view-model layer).
 * Maps from the BE SubEstado (api-contract §2.3) to a caregiver-facing
 * readability-first set of values.
 */
export type NodeSubEstado =
  | 'PENDING'
  | 'APPROVED_CAREGIVER'
  | 'APPROVED_PATIENT'
  | 'PUBLISHED'
  | 'REJECTED';

/** BCP-47 locale codes supported by TREI (SPEC-03 §2, api-contract §1.3). */
export type Locale = 'pt-BR' | 'en-US' | 'es-ES';

/** A single conversation turn in the training session (view-model layer). */
export interface TurnVM {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt: string;
}

/** Assembled training session view model (SPEC-03 §2). */
export interface SessionVM {
  id: string;
  patientId: string;
  status: SessionStatus;
  locale: Locale;
  turns: TurnVM[];
}

/**
 * Draft-node view model for the proposal review panel (SPEC-03 §2).
 * Distinct from DraftNodeVm (which has approvalInProgress/discardInProgress
 * flags for optimistic UI in P7). ProposalNodeVM is the simpler read model.
 */
export interface ProposalNodeVM {
  id: string;
  text: string;
  category: string;
  subEstado: NodeSubEstado;
  approvedCaregiver: boolean;
  approvedPatient: boolean;
}

/** Summary of approval status across locales (view-model layer). */
export interface ApprovalStatusVM {
  nodeId: string;
  caregiverApproved: boolean;
  patientApproved: boolean;
  subEstado: NodeSubEstado;
}

// ---------------------------------------------------------------------------
// ProblemDetail (RFC 7807) — used by TreiErrorMapper
// ---------------------------------------------------------------------------

/**
 * RFC 7807 ProblemDetail shape as returned by trei-service (api-contract §3).
 * The `correlationId` is echoed from the `X-Correlation-Id` request header by GWY.
 */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  correlationId?: string;
}
