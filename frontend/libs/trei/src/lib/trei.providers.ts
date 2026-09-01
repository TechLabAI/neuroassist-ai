import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { TRAINING_SESSION_PORT } from './api/training-session.port';
import { PROPOSAL_TURN_PORT } from './api/proposal-turn.port';
import { DRAFT_NODE_PORT } from './api/draft-node.port';
import { CAREGIVER_APPROVAL_PORT } from './api/caregiver-approval.port';
import { TrainingSessionMockAdapter } from './api/training-session-mock.adapter';
import { TrainingSessionHttpAdapter } from './api/training-session-http.adapter';
import { ProposalTurnMockAdapter } from './api/proposal-turn-mock.adapter';
import { ProposalTurnHttpAdapter } from './api/proposal-turn-http.adapter';
import { DraftNodeMockAdapter } from './api/draft-node-mock.adapter';
import { DraftNodeHttpAdapter } from './api/draft-node-http.adapter';
import { CaregiverApprovalMockAdapter } from './api/caregiver-approval-mock.adapter';
import { CaregiverApprovalHttpAdapter } from './api/caregiver-approval-http.adapter';

// =============================================================================
// provideTrei() — factory that wires TREI ports to adapters.
// adapter: 'mock'  → dev/test (default); no backend required
// adapter: 'http'  → production; requires trei-service + GWY running
//
// Usage in app.config.ts:
//   providers: [..., provideTrei({ adapter: 'mock' })]
//
// TODO[INT-FE-TREI-001..004]: switch to 'http' once trei-service is deployed.
//
// INV-1 (ADR-007 FE / ADR-003 BE): NO patient-approval port is wired here.
// Patient confirmation is CONV's domain — a different module, a different actor,
// a different scope. Adding a patient-approval adapter here would break the
// dual-authorization invariant.
// =============================================================================

export interface TreiProviderConfig {
  adapter: 'mock' | 'http';
}

export function provideTrei(
  config: TreiProviderConfig = { adapter: 'mock' },
): EnvironmentProviders {
  const isMock = config.adapter !== 'http';
  return makeEnvironmentProviders([
    {
      provide: TRAINING_SESSION_PORT,
      useClass: isMock ? TrainingSessionMockAdapter : TrainingSessionHttpAdapter,
    },
    {
      provide: PROPOSAL_TURN_PORT,
      useClass: isMock ? ProposalTurnMockAdapter : ProposalTurnHttpAdapter,
    },
    {
      provide: DRAFT_NODE_PORT,
      useClass: isMock ? DraftNodeMockAdapter : DraftNodeHttpAdapter,
    },
    {
      provide: CAREGIVER_APPROVAL_PORT,
      useClass: isMock ? CaregiverApprovalMockAdapter : CaregiverApprovalHttpAdapter,
    },
  ]);
}
