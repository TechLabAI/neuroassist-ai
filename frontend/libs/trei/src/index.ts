// =============================================================================
// @neuroassist/trei — public API barrel
// Imported by scope:web shell (app.routes.ts, app.config.ts) and shell host pages.
// Modules must NEVER import each other's feature libs directly (nx-boundaries).
// =============================================================================

// Shell descriptor and routes (consumed by scope:web app.routes.ts)
export { TREI_MODULE_DESCRIPTOR, TREI_MODULE_PERMISSION_MAP, TREI_MODULE_FEATURE_FLAGS } from './lib/trei.descriptor';
export { TREI_ROUTES } from './lib/trei.routes';

// Nav config (shell sidebar / breadcrumb registration)
export { TREI_NAV_CONFIG } from './lib/trei-nav-config';

// Facades (scope:web may inject for inter-module composition)
export { TrainingSessionFacade } from './lib/trei.facade';
export { ProposalReviewFacade } from './lib/trei-proposal.facade';

// Providers factory
export { provideTrei } from './lib/trei.providers';
export type { TreiProviderConfig } from './lib/trei.providers';

// Domain types (shared with scope:web for composition)
export type {
  TrainingSession,
  LlmProposal,
  DraftNodeVm,
  ChatMessage,
  TrainingSessionScreenVm,
  ProposalReviewScreenVm,
  TreiLocale,
  TreiNodeTipo,
  SubEstado,
  AppError,
} from './lib/trei.types';

// Access tokens (needed by app.config.ts to override in tests)
export { TREI_AUTH_SESSION, TREI_ACCESS_DECISION } from './lib/access/trei-access-tokens';
export type { TreiAuthSession, TreiAccessDecision } from './lib/access/trei-access-tokens';

// Port tokens (for test overrides)
export { TRAINING_SESSION_PORT } from './lib/api/training-session.port';
export { PROPOSAL_TURN_PORT } from './lib/api/proposal-turn.port';
export { DRAFT_NODE_PORT } from './lib/api/draft-node.port';
export { CAREGIVER_APPROVAL_PORT } from './lib/api/caregiver-approval.port';
