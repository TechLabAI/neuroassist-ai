/**
 * edge-stubs.ts — Shared Playwright route-intercept stubs for EDGE e2e.
 *
 * These stubs intercept HTTP calls and apply when the Angular app is configured
 * with `provideEdge({ adapter: 'http' })`.
 *
 * In the default development build (`adapter: 'mock'`), the mock adapters return
 * data in-memory (no HTTP calls), so route intercepts have no effect. The mock
 * adapter seed data is documented in `device-mock.adapter.ts` / `pairing-mock.adapter.ts`.
 *
 * The fixtures use a closure-based "flippable" stub pattern so individual tests
 * can control what the next response returns (e.g., initially 'not-paired', then
 * 'paired' after a heartbeat flip).
 *
 * FE-EDGE-021 through FE-EDGE-026 (P9 — E2E + Hardening).
 */
import { Route } from '@playwright/test';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

export const EDGE_DEVICE_ID = 'edge-test-01';
export const EDGE_PATIENT_ID = 'pac-test';
export const EDGE_BASE = '/api/v1/edge';

// ---------------------------------------------------------------------------
// Fixture factories
// ---------------------------------------------------------------------------

/** Build a DispositivoEdgeView JSON payload. */
export function makeDevicePayload(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  const now = Date.now();
  return {
    deviceId: EDGE_DEVICE_ID,
    tenantRef: 'tenant-familia-test',
    pacienteId: EDGE_PATIENT_ID,
    pairingState: 'paired',
    status: 'online',
    ultimaSincronizacao: new Date(now - 10_000).toISOString(), // 10s ago → fresh (< 45s staleness)
    versao: '1.4.2',
    runningProfileVersion: 3,
    currentProfileVersion: 3,
    profileDrift: false,
    stalenessThresholdSeconds: 90,
    serverTime: new Date(now).toISOString(),
    ...overrides,
  };
}

/** Build a RegisterDeviceResult JSON payload. */
export function makeRegisterPayload(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    deviceId: EDGE_DEVICE_ID,
    pairingState: 'registered',
    label: 'Dispositivo E2E Test',
    enrollmentToken: 'E2E-TOKEN-ABCD-1234',
    enrollmentExpiresAt: new Date(Date.now() + 5 * 60_000).toISOString(), // 5 min TTL
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

/** Build a LivenessView JSON payload. */
export function makeLivenessPayload(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  const now = Date.now();
  return {
    deviceId: EDGE_DEVICE_ID,
    pacienteId: EDGE_PATIENT_ID,
    status: 'online',
    ultimaSincronizacao: new Date(now - 10_000).toISOString(),
    versao: '1.4.2',
    stalenessThresholdSeconds: 90,
    serverTime: new Date(now).toISOString(),
    ...overrides,
  };
}

/** Build a ProblemDetail JSON payload. */
export function makeProblemDetail(
  type: string,
  status: number,
  detail: string,
): Record<string, unknown> {
  return {
    type,
    status,
    title: type.split('/').pop() ?? 'error',
    detail,
    correlationId: 'e2e-test-correlation-id',
  };
}

// ---------------------------------------------------------------------------
// Flippable stub pattern
// Allows a test to control what the next route response returns.
// Usage:
//   const stub = createFlippableStub();
//   await page.route('**/liveness', stub.handler);
//   // ...test first state...
//   stub.flip({ ultimaSincronizacao: new Date().toISOString() });
//   // next request returns updated payload
// ---------------------------------------------------------------------------

export interface FlippableStub {
  /** Playwright route handler — pass directly to page.route(). */
  handler: (route: Route) => Promise<void>;
  /** Update the response body for subsequent requests. */
  flip(overrides: Record<string, unknown>): void;
  /** Total number of requests matched by this stub. */
  callCount(): number;
}

export function createFlippableLivenessStub(
  initial: Record<string, unknown> = {},
): FlippableStub {
  let currentOverrides: Record<string, unknown> = { ...initial };
  let count = 0;

  return {
    handler: async (route) => {
      count++;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeLivenessPayload(currentOverrides)),
      });
    },
    flip(overrides) {
      currentOverrides = { ...overrides };
    },
    callCount() {
      return count;
    },
  };
}

export function createFlippableDeviceStub(
  initial: Record<string, unknown> = {},
): FlippableStub {
  let currentOverrides: Record<string, unknown> = { ...initial };
  let count = 0;

  return {
    handler: async (route) => {
      count++;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeDevicePayload(currentOverrides)),
      });
    },
    flip(overrides) {
      currentOverrides = { ...overrides };
    },
    callCount() {
      return count;
    },
  };
}

// ---------------------------------------------------------------------------
// Simple one-shot stub helpers (for non-flippable routes)
// ---------------------------------------------------------------------------

/** Stub the POST /api/v1/edge/devices endpoint (register device). */
export async function fulfillRegister(
  route: Route,
  overrides: Record<string, unknown> = {},
): Promise<void> {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(makeRegisterPayload(overrides)),
  });
}

/** Fulfill a route with a ProblemDetail error response. */
export async function fulfillError(
  route: Route,
  type: string,
  status: number,
  detail: string,
): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/problem+json',
    body: JSON.stringify(makeProblemDetail(type, status, detail)),
  });
}
