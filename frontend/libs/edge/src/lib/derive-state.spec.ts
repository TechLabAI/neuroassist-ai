// =============================================================================
// derive-state.spec.ts — FE-EDGE-007 DoD: all 6 state-transition cases
//
// Tests the pure deriveState() function (ADR-005, SPEC-03 §2).
// Safety-critical gate: connectivity state is computed server-side from heartbeat
// freshness — never from a BE status flag and never from the client clock.
//
// 6 canonical cases:
//   [1] not-paired      — null device
//   [2] not-paired      — unpaired/retired pairingState or status='n/a'
//   [3] unknown         — pollOk=false (honesty invariant: unknown ≠ online)
//   [4] offline-never-reported — paired but ultimaSincronizacao is null
//   [5] online          — server-relative age < stalenessThresholdMs
//   [6] stale           — stalenessThresholdMs ≤ age < offlineWindowMs
//   [alt] offline       — age ≥ offlineWindowMs
// =============================================================================

import { deriveState, MODULE_FEATURE_FLAGS } from './edge.facade';
import { DispositivoEdgeView } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// Constants derived from live feature-flag defaults
// ---------------------------------------------------------------------------

const STALE_MS = MODULE_FEATURE_FLAGS.stalenessThresholdMs.default;   // 45_000
const OFFLINE_MS = MODULE_FEATURE_FLAGS.offlineWindowMs.default;      // 90_000

// ---------------------------------------------------------------------------
// Fixture factory — creates a valid paired device; callers override per-test
// We use a fixed epoch (1_000_000_000_000ms) for server timestamps so age
// computations are deterministic and independent of system clock.
// ---------------------------------------------------------------------------

const FIXED_EPOCH = 1_000_000_000_000; // 2001-09-08 — fixed "now" for server

function makeDevice(overrides: Partial<DispositivoEdgeView> = {}): DispositivoEdgeView {
  return {
    deviceId: 'test-device',
    tenantRef: 'tenant-test',
    pacienteId: 'pac-test',
    pairingState: 'paired',
    status: 'online',
    ultimaSincronizacao: new Date(FIXED_EPOCH - 10_000).toISOString(), // 10s before epoch
    versao: '1.0.0',
    runningProfileVersion: 1,
    currentProfileVersion: 1,
    profileDrift: false,
    stalenessThresholdSeconds: 90,
    serverTime: new Date(FIXED_EPOCH).toISOString(),
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Case 1 & 2 — not-paired
// ---------------------------------------------------------------------------

describe('deriveState() — [case 1+2] not-paired', () => {
  it('[case-1] returns not-paired when device is null, regardless of pollOk', () => {
    expect(deriveState(null, true)).toBe('not-paired');
    expect(deriveState(null, false)).toBe('not-paired');
  });

  it('[case-2a] returns not-paired when pairingState is "unpaired"', () => {
    expect(deriveState(makeDevice({ pairingState: 'unpaired' }), true)).toBe('not-paired');
  });

  it('[case-2b] returns not-paired when pairingState is "retired"', () => {
    expect(deriveState(makeDevice({ pairingState: 'retired' }), true)).toBe('not-paired');
  });

  it('[case-2c] returns not-paired when BE status is "n/a" (device removed)', () => {
    expect(
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      deriveState(makeDevice({ status: 'n/a' as any }), true),
    ).toBe('not-paired');
  });
});

// ---------------------------------------------------------------------------
// Case 3 — unknown (honesty invariant)
// pollOk=false → ALWAYS unknown, even when the device was recently synced.
// This is the primary safety guard: a stale poll must never produce 'online'.
// ---------------------------------------------------------------------------

describe('deriveState() — [case 3] unknown (honesty invariant)', () => {
  it('[case-3] returns unknown when pollOk=false, even for a very recently synced device', () => {
    const dev = makeDevice({
      serverTime: new Date(FIXED_EPOCH).toISOString(),
      ultimaSincronizacao: new Date(FIXED_EPOCH - 5_000).toISOString(), // 5s ago — very fresh
    });
    expect(deriveState(dev, false)).toBe('unknown');
  });

  it('[invariant] pollOk=false NEVER produces online — regardless of sync freshness', () => {
    const freshDevice = makeDevice({
      serverTime: new Date(FIXED_EPOCH).toISOString(),
      ultimaSincronizacao: new Date(FIXED_EPOCH - 1_000).toISOString(), // 1s ago
    });
    const result = deriveState(freshDevice, false);
    expect(result).not.toBe('online');
    expect(result).toBe('unknown');
  });

  it('[invariant] pollOk=false with stale device still returns unknown (not stale)', () => {
    const staleDevice = makeDevice({
      serverTime: new Date(FIXED_EPOCH).toISOString(),
      ultimaSincronizacao: new Date(FIXED_EPOCH - (STALE_MS + 10_000)).toISOString(),
    });
    expect(deriveState(staleDevice, false)).toBe('unknown');
  });
});

// ---------------------------------------------------------------------------
// Case 4 — offline-never-reported
// Paired device where the agent has never sent a heartbeat (ultimaSincronizacao=null).
// ---------------------------------------------------------------------------

describe('deriveState() — [case 4] offline-never-reported', () => {
  it('[case-4] returns offline when paired device has never reported a heartbeat', () => {
    const device = makeDevice({ ultimaSincronizacao: null });
    expect(deriveState(device, true)).toBe('offline');
  });

  it('[case-4] offline-never-reported is distinct from not-paired — device IS registered', () => {
    const device = makeDevice({ pairingState: 'registered', ultimaSincronizacao: null });
    expect(deriveState(device, true)).toBe('offline');
  });
});

// ---------------------------------------------------------------------------
// Case 5 — online (server-relative age < stalenessThresholdMs)
// Case 6 — stale (stalenessThresholdMs ≤ age < offlineWindowMs)
// Alt — offline (age ≥ offlineWindowMs)
// All use fixed epoch to avoid system-clock dependence.
// ---------------------------------------------------------------------------

describe('deriveState() — [cases 5/6/alt] freshness-derived states', () => {
  const server = new Date(FIXED_EPOCH).toISOString();

  it('[case-5] returns online when server-relative age is within staleness window', () => {
    const recentSync = new Date(FIXED_EPOCH - (STALE_MS - 5_000)).toISOString(); // 40s ago
    expect(deriveState(makeDevice({ serverTime: server, ultimaSincronizacao: recentSync }), true))
      .toBe('online');
  });

  it('[case-6] returns stale when age is in the stale band (between staleness and offline windows)', () => {
    const staleSync = new Date(FIXED_EPOCH - (STALE_MS + 10_000)).toISOString(); // 55s ago
    expect(deriveState(makeDevice({ serverTime: server, ultimaSincronizacao: staleSync }), true))
      .toBe('stale');
  });

  it('[alt-offline] returns offline when server-relative age exceeds offline window', () => {
    const oldSync = new Date(FIXED_EPOCH - (OFFLINE_MS + 10_000)).toISOString(); // 100s ago
    expect(deriveState(makeDevice({ serverTime: server, ultimaSincronizacao: oldSync }), true))
      .toBe('offline');
  });

  it('uses server-clock, not client Date.now() — avoids client skew (doc 06 R5)', () => {
    // Server is 1 hour ahead of the sync time we construct.
    // By client clock, the device looks fresh; by server clock it is stale/offline.
    const futureServer = new Date(FIXED_EPOCH + 3_600_000).toISOString(); // 1h in the future
    const syncByClientClock = new Date(FIXED_EPOCH - (STALE_MS - 5_000)).toISOString(); // looks fresh by client
    // Server-relative age = 3_600_000 + (STALE_MS - 5_000) >> OFFLINE_MS → offline
    const device = makeDevice({ serverTime: futureServer, ultimaSincronizacao: syncByClientClock });
    expect(deriveState(device, true)).toBe('offline');
  });
});

// ---------------------------------------------------------------------------
// Threshold boundary conditions
// ---------------------------------------------------------------------------

describe('deriveState() — threshold boundary verification', () => {
  const server = new Date(FIXED_EPOCH).toISOString();

  it('stale lower bound: age exactly equal to stalenessThresholdMs → stale (not online)', () => {
    // age = STALE_MS → NOT < STALE_MS → check next band
    const boundarySync = new Date(FIXED_EPOCH - STALE_MS).toISOString();
    expect(deriveState(makeDevice({ serverTime: server, ultimaSincronizacao: boundarySync }), true))
      .toBe('stale');
  });

  it('offline lower bound: age exactly equal to offlineWindowMs → offline (not stale)', () => {
    // age = OFFLINE_MS → NOT < OFFLINE_MS → offline
    const boundarySync = new Date(FIXED_EPOCH - OFFLINE_MS).toISOString();
    expect(deriveState(makeDevice({ serverTime: server, ultimaSincronizacao: boundarySync }), true))
      .toBe('offline');
  });
});

// =============================================================================
// FE-EDGE-027 — G5: serverTime absent → 'unknown' (safe fallback, not a crash)
//
// Gap G5: No test verifies that the FE derivation falls back correctly if
// `serverTime` is absent from the LivenessView response (defensive coding).
// The spec requires: serverTime absent → derivedState = 'unknown' (never crash
// or silently fall back to client clock, which would violate doc 06 R5).
// =============================================================================

describe('G5 — deriveState() with absent serverTime (FE-EDGE-027)', () => {
  it('[G5] serverTime absent (undefined): returns unknown, never throws', () => {
    // G5 fix (FE-EDGE-027): when serverTime is absent/undefined, isNaN(NaN) guard
    // triggers early-return 'unknown' — preventing a false-green 'online'.
    const deviceWithoutServerTime = makeDevice({
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      serverTime: undefined as any,
      ultimaSincronizacao: new Date(FIXED_EPOCH - 10_000).toISOString(),
    });

    // Must not throw.
    let result: string | undefined;
    expect(() => {
      result = deriveState(deviceWithoutServerTime, true);
    }).not.toThrow();

    // With the G5 fix: undefined serverTime → serverMs = NaN → 'unknown' (safe fallback).
    expect(result).toBe('unknown');
    expect(result).not.toBe('online');
  });

  it('[G5] serverTime absent: result is unknown (honesty invariant + safe fallback)', () => {
    const deviceWithoutServerTime = makeDevice({
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      serverTime: undefined as any,
      pairingState: 'paired',
      ultimaSincronizacao: new Date(FIXED_EPOCH - 5_000).toISOString(),
    });

    const result = deriveState(deviceWithoutServerTime, true);

    // G5 fix: absent serverTime → 'unknown' (never 'online' — no false positive).
    expect(result).toBe('unknown');
    expect(result).not.toBe('online');
  });

  it('[G5] serverTime null: returns unknown (G5 fix prevents false-green)', () => {
    const deviceWithNullServerTime = makeDevice({
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      serverTime: null as any,
      ultimaSincronizacao: new Date(FIXED_EPOCH - 5_000).toISOString(),
    });

    const result = deriveState(deviceWithNullServerTime, true);

    // BEFORE G5 fix: null serverTime → new Date(null) = epoch → negative ageMs →
    // ageMs < stalenessMs (negative < positive) → FALSE 'online'. This was the bug.
    // AFTER G5 fix: null serverTime → NaN (via `serverTime ? ... : NaN`) → 'unknown'.
    expect(result).toBe('unknown');
    expect(result).not.toBe('online');
  });

  it('[G5] serverTime empty string: returns unknown (empty string is falsy → NaN guard)', () => {
    const deviceWithEmptyServerTime = makeDevice({
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      serverTime: '' as any,
      ultimaSincronizacao: new Date(FIXED_EPOCH - 5_000).toISOString(),
    });

    const result = deriveState(deviceWithEmptyServerTime, true);

    // G5 fix: empty string is falsy → serverMs = NaN → 'unknown'.
    expect(result).toBe('unknown');
    expect(result).not.toBe('online');
  });

  it('[G5] pollOk=false with absent serverTime: still returns unknown (not crash)', () => {
    const device = makeDevice({
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      serverTime: undefined as any,
      ultimaSincronizacao: new Date(FIXED_EPOCH - 5_000).toISOString(),
    });

    // pollOk=false → unknown guard fires first (before serverTime calculation).
    expect(() => {
      const result = deriveState(device, false);
      // unknown fires before any arithmetic — pollOk guard takes precedence.
      expect(result).toBe('unknown');
    }).not.toThrow();
  });
});
