// =============================================================================
// dash.types.ts — DASH domain types and view-model records
// SPEC-01..08 — state contracts for DashHomeFacade + HomeStatusComponent
// BFF surface: /panel/** (dash-service, port 8090)
// =============================================================================

// ---------------------------------------------------------------------------
// Domain primitives (aligned to api-contract.md §2/§3/§4)
// ---------------------------------------------------------------------------

export type DeviceStatus       = 'online' | 'offline' | 'unknown';
export type EventUrgencia      = 'emergencia' | 'rotina';
export type EventFeedback      = 'nao_avaliado' | 'real' | 'falso_positivo';
export type NotificationTier   = 'full_screen_alarm' | 'discreet';
export type ScreenStatus       = 'idle' | 'loading' | 'loaded' | 'empty' | 'error';

// ---------------------------------------------------------------------------
// BFF summary types (GET /panel/summary — api-contract.md §2.1)
// ---------------------------------------------------------------------------

export interface DeviceVm {
  readonly status: DeviceStatus;
  readonly lastSeenAt: string | null;   // ISO-8601
  readonly degraded: boolean;
}

export interface LatestEventVm {
  readonly eventId: string;
  readonly commandLabel: string;
  readonly urgencia: EventUrgencia;
  readonly feedback: EventFeedback;
  readonly at: string;                  // ISO-8601
}

export interface PatientSummaryVm {
  readonly patientId: string;
  readonly name: string;
  readonly device: DeviceVm;
  readonly latestEvent: LatestEventVm | null;
  readonly unacknowledgedCount: number;
}

export interface CaregiverVm {
  readonly id: string;
  readonly name: string;
  readonly role: string;
}

export interface DashSummaryResponse {
  readonly tenantId: string;
  readonly caregiver: CaregiverVm;
  readonly patients: PatientSummaryVm[];
  readonly degradedSections: string[];   // e.g. ['evnt', 'edge']
}

// ---------------------------------------------------------------------------
// Notification types (GET /panel/notifications/unacknowledged — §4.1)
// ---------------------------------------------------------------------------

export interface NotificationEnvelope {
  readonly dispatchId: string;
  readonly tier: NotificationTier;
  readonly patientId: string;
  readonly commandLabel: string;
  readonly urgencia: EventUrgencia;
  readonly raisedAt: string;            // ISO-8601
}

export interface UnacknowledgedResponse {
  readonly notifications: NotificationEnvelope[];
}

// ---------------------------------------------------------------------------
// Home screen view-model (assembled by DashHomeFacade)
// ---------------------------------------------------------------------------

export interface HomeScreenVm {
  readonly status: ScreenStatus;
  readonly summary: DashSummaryResponse | null;
  readonly error: AppError | null;
}

// ---------------------------------------------------------------------------
// Notifications screen view-model (assembled by NotificationsFacade)
// ---------------------------------------------------------------------------

export interface NotificationsScreenVm {
  readonly status: ScreenStatus;
  readonly notifications: NotificationEnvelope[];
  readonly error: AppError | null;
}

// ---------------------------------------------------------------------------
// Shared error model (aligned with @neuroassist/shared/error-model)
// ---------------------------------------------------------------------------

export interface AppError {
  readonly code: string;
  readonly message: string;
  readonly correlationId?: string;
  readonly status?: number;
}
