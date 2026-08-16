// =============================================================================
// hist.types.ts — HIST domain types and view-model records
// SPEC-03 §1/§2 — state contracts for HistoryFacade + HistoryTimelineComponent
// =============================================================================

// ---------------------------------------------------------------------------
// Domain primitives
// ---------------------------------------------------------------------------

export type Period       = 'hoje' | 'semana' | 'mes';
export type Verdict      = 'real' | 'falso_positivo';
export type Urgency      = 'rotina' | 'atencao' | 'critico';
export type ScreenStatus = 'idle' | 'loading' | 'loaded' | 'empty' | 'error';
export type MarkingState = 'idle' | 'marking' | 'mark-error' | 'marked';

// ---------------------------------------------------------------------------
// View-model records (SPEC-03 §2)
// ---------------------------------------------------------------------------

/**
 * One timeline row — AuditRecordResponse mapped to a human-readable VM.
 * `label` is Comando.rotulo; the raw `factType` code is never the primary label.
 */
export interface HistoryItemVm {
  readonly id: string;                  // HIST record id (AuditRecordResponse.id)
  readonly factType: string;            // e.g. 'evento.confirmado' (not primary label)
  readonly label: string;               // Comando.rotulo — human-readable, never a code
  readonly urgency: Urgency;            // resolved from read model (PAC), badge-driven
  readonly occurredAtLabel: string;     // localized "14h32" from occurredAt
  readonly recordedAtIso: string;       // authoritative order key (recordedAt)
  readonly confirmationLine: string;    // "Você confirmou às 14h33" from actor + payload
  readonly verdict: Verdict | null;     // terminal feedback verdict; null = nao_avaliado
  readonly correctsRecordId: string | null; // links corrective record to its original (ADR-004)
}

/** Per-item feedback sub-state, keyed by item id. */
export interface ItemMarking {
  readonly id: string;
  readonly state: MarkingState;
  readonly reconciledBy?: string;       // "Ana Costa" — set on 409 terminal reconcile
  readonly reconciledAtLabel?: string;  // "às 14h33"
}

/** Screen view-model consumed by HistoryTimelineComponent. */
export interface HistoryScreenVm {
  readonly items: readonly HistoryItemVm[];
  readonly period: Period;
  readonly status: ScreenStatus;
  readonly hasMore: boolean;
  readonly error: AppError | null;
}

/** Minimal AppError (aligned with @neuroassist/shared/error-model). */
export interface AppError {
  readonly code: string;
  readonly message: string;
  readonly correlationId?: string;
  readonly status?: number;
}

// ---------------------------------------------------------------------------
// Pagination (api-contract §1)
// ---------------------------------------------------------------------------

export interface AuditRecordSubject {
  readonly type: 'paciente' | 'comando' | 'cuidador' | 'dispositivo';
  readonly id: string;
}

export interface AuditRecordActor {
  readonly caregiverId: string;
  readonly displayNameSnapshot: string;
}

/** Wire DTO from GET /api/v1/hist/history (api-contract §3.1). */
export interface AuditRecordResponse {
  readonly id: string;
  readonly factId: string;
  readonly factType: string;
  readonly subject: AuditRecordSubject;
  readonly actor: AuditRecordActor;
  readonly payload: Record<string, unknown>;
  readonly occurredAt: string;
  readonly recordedAt: string;
  readonly streamKey: string;
  readonly sequenceNo: number;
  readonly prevHash: string;
  readonly recordHash: string;
  readonly correctsRecordId: string | null;
}

export interface PagedResponse<T> {
  readonly items: T[];
  readonly page: number;
  readonly size: number;
  readonly total: number;
}

/** Wire DTO from GET /api/v1/hist/history/verify (api-contract §3.2). */
export interface ChainVerificationResult {
  readonly streamKey: string;
  readonly status: 'intact' | 'broken' | 'empty';
  readonly recordsVerified: number;
  readonly brokenAtSequenceNo: number | null;
  readonly verifiedAt: string;
}

// ---------------------------------------------------------------------------
// Feedback DTO (SPEC-04 §2.4 — targets evnt-service)
// ---------------------------------------------------------------------------

export interface FeedbackWriteDto {
  readonly verdict: Verdict;
}

export interface FeedbackResponse {
  readonly eventId: string;
  readonly verdict: Verdict;
  readonly confirmedAt: string;
}

// ---------------------------------------------------------------------------
// HistoryQueryParams (SPEC-04 §2.1)
// ---------------------------------------------------------------------------

export interface HistoryQueryParams {
  readonly from: string;              // ISO-8601 UTC
  readonly to: string;                // ISO-8601 UTC
  readonly order?: 'asc' | 'desc';
  readonly page?: number;
  readonly size?: number;
  readonly subjectType?: AuditRecordSubject['type'];
  readonly subjectId?: string;
  readonly factType?: string;
  readonly actorCaregiverId?: string;
}
