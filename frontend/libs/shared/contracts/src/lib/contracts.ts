// =============================================================================
// @neuroassist/shared/contracts — wire types shared across the FE boundary.
// Authoritative source: docs/architecture/backend/services/gest-service/contracts/event-contracts.md §3.2
// Contract version: 1.0.0 (frozen — do NOT modify without updating BE event-contracts.md)
// =============================================================================

// ---------------------------------------------------------------------------
// EdgeRecognitionSignal (frozen v1.0.0 — SPEC-04 §1)
// ---------------------------------------------------------------------------

/** Stream payload emitted by the edge agent over the localhost WS/SSE bridge.
 * Shape is bound verbatim to BE event-contracts.md §3.2 (contractVersion 1.0.0).
 * The draft shape (recognized/matchedPrimitivo/step) from the FE vision doc is
 * superseded by this frozen contract — see SPEC-04 §1 and GEST-INT-003. */
export interface EdgeRecognitionSignal {
  readonly contractVersion: string;
  readonly signalKind: 'recognized' | 'not_recognized' | 'preview_frame_ack' | 'degraded';
  readonly commandDraftRef: string;
  readonly primitives: ReadonlyArray<{
    readonly engineFeatureRef: string;
    readonly confidence: number; // internal only — never expose raw to UI
  }>;
  readonly timingWindow: {
    readonly startMs: number;
    readonly endMs: number;
    readonly durationMs: number;
  };
  readonly degraded: boolean; // true => mocked; a real "recognized" is never emitted when degraded
  readonly emittedAt: string; // ISO-8601 UTC
}

// ---------------------------------------------------------------------------
// ComandoDraft — SPEC-03 §1 (domain input from host page, e.g. PAC command editor)
// ---------------------------------------------------------------------------

export type Sensibilidade = 'baixa' | 'media' | 'alta';
export type Urgencia = 'emergencia' | 'rotina';

/** The command under test, provided by the host page via @Input to GestureLiveTestComponent.
 * GEST does not own or fetch the catalog — it only receives the draft for the test run. */
export interface ComandoDraft {
  readonly commandDraftRef: string;        // opaque handle; matches EdgeRecognitionSignal.commandDraftRef
  readonly rotulo: string;                 // family label, e.g. "água"
  readonly primitivoPrincipal: string;     // TipoGesto id
  readonly primitivoSecundario: string | null;
  readonly sensibilidade: Sensibilidade;
  readonly urgencia: Urgencia;
  readonly tempoMinimoGestoMs: number;
  readonly tempoMaximoGestoMs: number;
  readonly composite: boolean;             // derived: primitivoSecundario !== null
}

// ---------------------------------------------------------------------------
// GestureTestVM — SPEC-03 §2 (view-model exposed by GestureTestFacade as signals)
// ---------------------------------------------------------------------------

export type GestureTestState =
  | 'idle' | 'testing' | 'recognized' | 'not-recognized' | 'camera-error';

export type GestureTestStep = 'principal' | 'secundario' | null;

export type DegradedReason =
  | 'device-offline' | 'bridge-unreachable' | 'version-mismatch' | null;

export interface GestureTestVM {
  readonly commandDraftRef: string;
  readonly rotulo: string;
  readonly state: GestureTestState;
  readonly step: GestureTestStep;        // composite progress; null when simple gesture
  readonly composite: boolean;
  readonly message: string;              // pt-BR human-language ONLY (no raw confidence)
  readonly deviceOnline: boolean;        // gates real vs degraded test
  readonly degraded: boolean;            // true => mocked verdict, honest banner shown
  readonly degradedReason: DegradedReason;
  readonly contractVersion: string | null; // negotiated with edge bridge on openStream
}

// ---------------------------------------------------------------------------
// GestureTestOutcome — SPEC-03 §3 (emitted to host page on test completion)
// ---------------------------------------------------------------------------

/** Final verdict for a single live test run.
 * Invariant: degraded:true guarantees the verdict was NOT a real recognition,
 * so PAC can distinguish an honest simulated pass from a real edge recognition. */
export interface GestureTestOutcome {
  readonly commandDraftRef: string;
  readonly recognized: boolean;
  readonly degraded: boolean;          // true => verdict was a simulated/mocked result
  readonly composite: boolean;
  readonly completedStep: GestureTestStep; // last step reached
}

// ---------------------------------------------------------------------------
// EligibilityResult — SPEC-04 §4 (consumed by GestEligibilityService)
// ---------------------------------------------------------------------------

export interface EligibilityResult {
  readonly eligible: boolean;
  readonly reason: string | null;
  readonly unsupportedPrimitiveIds: ReadonlyArray<string>;
}
