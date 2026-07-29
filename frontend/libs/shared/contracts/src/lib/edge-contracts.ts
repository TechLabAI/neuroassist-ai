// =============================================================================
// @neuroassist/shared/contracts — EDGE wire types (device + liveness + pairing)
// Authoritative source: backend/services/edge-service/contracts/api-contract.md
// Contract base path: /api/v1/edge (routed via GWY, api-contract §1.1)
// =============================================================================

// ---------------------------------------------------------------------------
// Wire enums (from api-contract §3.2 / §4)
// ---------------------------------------------------------------------------

export type PairingState = 'registered' | 'paired' | 'unpaired' | 'retired';
export type WireStatus = 'online' | 'offline' | 'n/a'; // BE-derived, binary (api-contract §3.2)

// ---------------------------------------------------------------------------
// DispositivoEdgeView — verbatim mirror of GET /api/v1/edge/devices/{deviceId}
// (api-contract §4.1). Lives here so scope:edge and scope:web topbar share the type.
// ---------------------------------------------------------------------------

export interface DispositivoEdgeView {
  readonly deviceId: string;
  readonly tenantRef: string;
  readonly pacienteId: string | null;
  readonly pairingState: PairingState;
  readonly status: WireStatus; // BE hint only — NOT FE truth (ADR-005)
  readonly ultimaSincronizacao: string | null; // ISO-8601 UTC; null => never reported
  readonly versao: string | null;
  readonly runningProfileVersion: number | null;
  readonly currentProfileVersion: number | null;
  readonly profileDrift: boolean;
  readonly stalenessThresholdSeconds: number; // BE effective window (api-contract §5)
  readonly serverTime: string; // authoritative clock for freshness (avoids client skew)
}

// ---------------------------------------------------------------------------
// RegisterDeviceResult — response from POST /api/v1/edge/devices (api-contract §2.1)
// ---------------------------------------------------------------------------

export interface RegisterDeviceResult {
  readonly deviceId: string;
  readonly pairingState: PairingState;
  readonly label: string;
  readonly enrollmentToken: string; // one-time claim code; shown once, then discarded
  readonly enrollmentExpiresAt: string; // ISO-8601 UTC; drives the countdown
  readonly createdAt: string;
}

// ---------------------------------------------------------------------------
// LivenessView — response from GET /api/v1/edge/patients/{id}/liveness
// or GET /api/v1/edge/devices/{id}/liveness (api-contract §4.4)
// ETag / 304-capable: the ETag boundary incorporates the derived-status bucket
// so an online→offline crossing always invalidates it (SPEC-04 §3).
// ---------------------------------------------------------------------------

export interface LivenessView {
  readonly deviceId: string;
  readonly pacienteId: string;
  readonly status: WireStatus;
  readonly ultimaSincronizacao: string | null;
  readonly versao: string | null;
  readonly stalenessThresholdSeconds: number;
  readonly serverTime: string; // authoritative clock (api-contract §4.4)
}

export interface NotModified {
  readonly notModified: true;
}

// ---------------------------------------------------------------------------
// DerivedState — FE-only; computed client-side from heartbeat freshness (ADR-005)
// SPEC-03 §2. The BE never sends 'stale' or 'unknown' — only the FE computes them.
// ---------------------------------------------------------------------------

export type DerivedState = 'online' | 'stale' | 'offline' | 'unknown' | 'not-paired';
export type ConnectivityTone = 'ok' | 'warn' | 'er' | 'muted';

// ---------------------------------------------------------------------------
// DeviceConnectivityVM — the single read-model the topbar chip and Home consume
// (SPEC-03 §3). Only surface exposed to scope:web (never raw DispositivoEdgeView).
// ---------------------------------------------------------------------------

export interface DeviceConnectivityVM {
  readonly paired: boolean;
  readonly derivedState: DerivedState;
  readonly lastSyncIso: string | null;
  readonly lastSyncRelative: string; // pt-BR relative label ("há 2 min")
  readonly versao: string | null;
  readonly tone: ConnectivityTone; // ok | warn | er | muted
  readonly label: string; // pt-BR label for the chip
}

// ---------------------------------------------------------------------------
// PairingVM — SPEC-03 §4; owned by PairingFacade
// ---------------------------------------------------------------------------

export type PairingPhase =
  | 'idle'
  | 'registering'
  | 'awaiting-claim'
  | 'awaiting-first-heartbeat'
  | 'paired'
  | 'expired'
  | 'error';

export interface PairingVM {
  readonly phase: PairingPhase;
  readonly deviceId: string | null;
  readonly enrollmentToken: string | null; // one-time code; shown once (api-contract §2.1)
  readonly enrollmentExpiresAtIso: string | null; // TTL boundary; drives countdown
  readonly pacienteId: string | null; // pairing target (from pacienteHint / onboarding)
  readonly versao: string | null; // agent version at first heartbeat
  readonly derivedState: DerivedState; // computed once device begins reporting
  readonly errorMessage: string | null; // pt-BR user-facing (SPEC-07)
}

// ---------------------------------------------------------------------------
// DeviceDetailVM — SPEC-03 §5
// ---------------------------------------------------------------------------

export interface DeviceDetailVM {
  readonly device: DispositivoEdgeView;
  readonly derivedState: DerivedState;
  readonly lastSyncRelative: string;
  readonly profileDrift: boolean; // running !== current profile version
  readonly canManage: boolean; // conductor entitlement gates action buttons
}

// ---------------------------------------------------------------------------
// PairedDeviceOutcome — SPEC-03 §6; emitted by PairingFacade to host page
// ---------------------------------------------------------------------------

export interface PairedDeviceOutcome {
  readonly deviceId: string;
  readonly pacienteId: string;
  readonly versao: string | null;
  readonly derivedState: DerivedState; // guaranteed 'online' only after real first heartbeat
}

// ---------------------------------------------------------------------------
// EdgeReadModelDeps — deps type required to resolve the connectivity read-model
// from the module descriptor (SPEC-06 §1)
// ---------------------------------------------------------------------------

export interface EdgeReadModelDeps {
  readonly pacienteId: string;
}
