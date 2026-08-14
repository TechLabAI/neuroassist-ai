// @neuroassist/shared/observability — Wave-0 stub (D-328/FE-4); flesh out in the implement cycle.
import { Injectable } from '@angular/core';

export interface Telemetry { event(name: string): void; }

/** Payload shape for all telemetry events emitted by module services. */
export interface TelemetryEvent {
  readonly event: string;
  readonly [key: string]: unknown;
}

/**
 * ObservabilityService — Wave-0 no-op stub (PAC-FE-022 / D-328).
 *
 * Replace the `emit` implementation when the observability backend is ready.
 * Module telemetry services (e.g. PacTelemetryService) inject this class;
 * specs mock it via `{ provide: ObservabilityService, useValue: { emit: jest.fn() } }`.
 */
@Injectable({ providedIn: 'root' })
export class ObservabilityService {
  /** Emit a structured telemetry event. Currently a no-op (D-328 stub). */
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  emit(_payload: TelemetryEvent): void {
    // TODO: route to analytics/observability backend when D-328 is implemented.
  }
}
