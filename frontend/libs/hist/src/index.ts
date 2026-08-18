// =============================================================================
// @neuroassist/hist — public API barrel
// Imported by scope:web shell (app.routes.ts, app.config.ts) and shell host pages.
// Modules must NEVER import each other's feature libs directly (nx-boundaries).
// =============================================================================

// Shell descriptor and routes (consumed by scope:web app.routes.ts)
export { HIST_MODULE_DESCRIPTOR, HIST_MODULE_PERMISSION_MAP, HIST_MODULE_FEATURE_FLAGS } from './lib/hist.descriptor';
export { HIST_ROUTES } from './lib/hist.routes';

// Nav config (shell sidebar / breadcrumb registration)
export { HIST_NAV_CONFIG } from './lib/hist-nav-config';

// Facade (scope:web may inject directly for inter-module composition)
export { HistoryFacade } from './lib/hist.facade';

// View-model types (shared with scope:web for composition)
export type {
  HistoryItemVm,
  HistoryScreenVm,
  ItemMarking,
  Period,
  Verdict,
  Urgency,
  ScreenStatus,
  MarkingState,
  AppError,
  AuditRecordResponse,
  ChainVerificationResult,
  PagedResponse,
} from './lib/hist.types';

// Providers factory
export { provideHist } from './lib/hist.providers';
export type { HistProviderConfig } from './lib/hist.providers';

// Access layer — DI tokens
export { HIST_AUTH_SESSION, HIST_ACCESS_DECISION } from './lib/access/hist-access-tokens';
export type { HistAuthSession, HistAccessDecision } from './lib/access/hist-access-tokens';

// Port tokens (needed by app.config.ts to override adapters in tests)
export { HIST_CLIENT_PORT } from './lib/api/hist-client.port';
export { HIST_FEEDBACK_PORT } from './lib/api/hist-feedback.port';
export type { HistClientPort } from './lib/api/hist-client.port';
export type { HistFeedbackPort } from './lib/api/hist-feedback.port';
