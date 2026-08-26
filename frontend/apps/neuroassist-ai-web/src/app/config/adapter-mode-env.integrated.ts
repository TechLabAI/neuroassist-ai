import type { AdapterMode } from './adapter-mode';

// ---------------------------------------------------------------------------
// adapter-mode-env.integrated.ts — build-time adapter mode for 'integrated'.
//
// Replaces adapter-mode-env.ts via fileReplacements in project.json
// (configuration: integrated). All 7 modules flip to HTTP adapters.
// Requires the stack to be running: docker compose -f docker/docker-compose.dev.yml up -d
//
// NEVER import this file directly — it is loaded via Nx fileReplacements.
// ---------------------------------------------------------------------------

export const CURRENT_ADAPTER_MODE: AdapterMode = 'http';
