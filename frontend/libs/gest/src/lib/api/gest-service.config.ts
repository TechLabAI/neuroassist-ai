import { InjectionToken } from '@angular/core';

// Real gest-service port (application.yml) — distinct from the PoC-profile port
// (8082) used by frontend/design-reference/angular-prototype. Overridable per
// environment via provideGest's http adapters DI.
export const GEST_SERVICE_BASE_URL = new InjectionToken<string>('GEST_SERVICE_BASE_URL', {
  providedIn: 'root',
  factory: () => `http://${window.location.hostname}:8084`,
});

// Local edge-agent WS bridge (localhost only — never routed through GWY).
export const EDGE_BRIDGE_WS_URL = new InjectionToken<string>('EDGE_BRIDGE_WS_URL', {
  providedIn: 'root',
  factory: () => 'ws://localhost:8765/live-test',
});
