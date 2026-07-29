import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';

// =============================================================================
// EvntClientPort — SPEC-04 §1 (data-access contract)
// Abstraction over the evnt-service HTTP/SSE API.
// Implementations: EvntClientMockAdapter (dev) / EvntClientHttpAdapter (prod).
// Base path resolved by @neuroassist/shared/access-http-base (adds X-Empresa-Id + Authorization).
// =============================================================================

// ---------------------------------------------------------------------------
// Wire DTOs (mirror BE api-contract §4 — FE never re-defines, only maps to Vm)
// ---------------------------------------------------------------------------

export type ConfirmationVerdict = 'real' | 'falso_positivo';
export type FeedbackStatus = 'nao_avaliado' | 'real' | 'falso_positivo';
export type UrgenciaLevel = 'emergencia' | 'rotina';
export type NotificationTier = 'full_screen_alarm' | 'discreet';

/** Named SSE event types emitted by GET /events/stream. */
export type SseEventKind = 'evento.created' | 'evento.updated' | 'keepalive';

/** Single SSE frame parsed from the live stream. */
export interface SseFrame {
  readonly kind: SseEventKind;
  readonly seq: number;              // id: field — used as Last-Event-ID on reconnect
  readonly payload: EventoSummaryDto | null;  // null for keepalive
}

export interface EventoSummaryDto {
  readonly eventoId: string;
  readonly urgencia: UrgenciaLevel;
  readonly tier: NotificationTier;
  readonly commandLabel: string;
  readonly feedback: FeedbackStatus;
  readonly recognizedAt: string;     // ISO-8601 UTC
}

export interface EventoDto {
  readonly eventoId: string;
  readonly pacienteId: string;
  readonly comandoRef: string;
  readonly commandLabelSnapshot: string;
  readonly urgenciaSnapshot: UrgenciaLevel;
  readonly timingWindow: { startMs: number; endMs: number; durationMs: number };
  readonly feedback: FeedbackStatus;
  readonly confirmadoPor?: string;
  readonly confirmadoAt?: string;
  readonly recognizedAt: string;
}

export interface ActiveEventsDto {
  readonly serverTime: string;
  readonly activeEvent: EventoSummaryDto | null;
  readonly events: EventoSummaryDto[];  // emergency-first
}

export interface ConfirmationRequestDto {
  readonly verdict: ConfirmationVerdict;
}

export interface ConfirmationResponseDto {
  readonly eventoId: string;
  readonly feedback: ConfirmationVerdict;
  readonly confirmadoPor: string;
  readonly confirmadoAt: string;
}

export interface EventQueryParams {
  readonly page?: number;
  readonly size?: number;
  readonly urgencia?: UrgenciaLevel;
  readonly since?: string;
  readonly feedback?: FeedbackStatus;
}

export interface EventoPageDto {
  readonly content: EventoDto[];
  readonly totalElements: number;
  readonly totalPages: number;
  readonly page: number;
  readonly size: number;
}

// ---------------------------------------------------------------------------
// Port interface
// ---------------------------------------------------------------------------

export interface EvntClientPort {
  /** GET /events/stream — SSE live channel.
   * Each Observable emission is a parsed SSE frame.
   * On stream error, caller must handle reconnect / degradation (SPEC-04 §3). */
  openStream(): Observable<SseFrame>;

  /** GET /events/active — hydration on load + poll fallback.
   * @param since optional ISO-8601 for cheap incremental polls */
  listActive(since?: string): Observable<ActiveEventsDto>;

  /** GET /events/{eventoId} — single event snapshot. */
  get(eventoId: string): Observable<EventoDto>;

  /** POST /events/{eventoId}/confirmation — terminal verdict (non-optimistic).
   * 409 with type=/errors/feedback-terminal treated as success-equivalent. */
  confirm(eventoId: string, verdict: ConfirmationVerdict): Observable<ConfirmationResponseDto>;

  /** POST /events/{eventoId}/dispatches/{dispatchId}/ack — delivery acknowledgment. */
  ack(eventoId: string, dispatchId: string): Observable<void>;

  /** GET /events — paged history query. */
  query(params?: EventQueryParams): Observable<EventoPageDto>;
}

export const EVNT_CLIENT_PORT = new InjectionToken<EvntClientPort>('EvntClientPort');
