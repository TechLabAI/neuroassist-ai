import { AppError } from '@neuroassist/shared/error-model';

// =============================================================================
// EVNT view-model types — SPEC-03
// Derived from EventoDto (SPEC-04 §2) by EventDeliveryFacade.
// Never carries clinical/biometric content.
// =============================================================================

export type Urgencia = 'emergencia' | 'rotina';
export type Feedback = 'nao_avaliado' | 'real' | 'falso_positivo';
export type Tier = 'full_screen_alarm' | 'discreet';
export type DeliveryMode = 'live' | 'degraded';
export type ConfirmationVerdict = 'real' | 'falso_positivo';

// ---------------------------------------------------------------------------
// Core view-model — SPEC-03 §1
// ---------------------------------------------------------------------------

export type EventoVm = {
  readonly eventoId: string;
  readonly urgencia: Urgencia;
  readonly tier: Tier;
  readonly commandLabel: string;       // rótulo livre (pt-BR), from PAC snapshot (BE)
  readonly patientName: string;        // resolved for display ("Márcio")
  readonly recognizedAt: string;       // ISO-8601 UTC
  readonly displayTime: string;        // "às 14h32" (derived, pt-BR)
  readonly feedback: Feedback;
  readonly confirmadoPor: string | null;
  readonly confirmadoAt: string | null;
};

// ---------------------------------------------------------------------------
// Alarm view-model — SPEC-03 §2
// ---------------------------------------------------------------------------

export type AlarmVm = {
  readonly event: EventoVm | null;     // non-null emergencia ⇒ overlay shown
  readonly visible: boolean;           // computed: event != null
  readonly confirming: boolean;        // POST /confirmation in flight
  readonly confirmError: AppError | null;
  readonly alreadyConfirmed: boolean;  // 409 path: already confirmed by someone
};

// ---------------------------------------------------------------------------
// Routine feed view-model — SPEC-03 §3
// ---------------------------------------------------------------------------

export type RoutineFeedVm = {
  readonly routineQueue: readonly EventoVm[];  // discreet, dismissible
  readonly recentEvents: readonly EventoVm[];  // GET /events history page
  readonly deliveryMode: DeliveryMode;
  readonly loading: boolean;
  readonly error: AppError | null;
  readonly empty: boolean;             // computed: !loading && routineQueue.length === 0
};

// ---------------------------------------------------------------------------
// Event detail view-model — SPEC-03 §4
// ---------------------------------------------------------------------------

export type EventDetailVm = {
  readonly event: EventoVm | null;
  readonly loading: boolean;
  readonly error: AppError | null;
  readonly canConfirm: boolean;        // computed: feedback === 'nao_avaliado'
  readonly confirming: boolean;
};

// ---------------------------------------------------------------------------
// Helper: derive displayTime from ISO-8601 string (pt-BR "às HHhMM")
// ---------------------------------------------------------------------------

export function toDisplayTime(iso: string): string {
  const d = new Date(iso);
  const h = String(d.getHours()).padStart(2, '0');
  const m = String(d.getMinutes()).padStart(2, '0');
  return `às ${h}h${m}`;
}
