import type { AdapterMode } from './adapter-mode';

// ---------------------------------------------------------------------------
// adapter-mode-env.ts — build-time adapter mode constant (default: mock).
//
// This file is replaced by adapter-mode-env.integrated.ts via fileReplacements
// in project.json when the 'integrated' Nx configuration is active.
//
// NEVER import from this file in library code — it lives in apps/ (scope:web).
// ---------------------------------------------------------------------------

export const CURRENT_ADAPTER_MODE: AdapterMode = 'mock';
