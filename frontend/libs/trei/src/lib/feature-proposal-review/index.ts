// =============================================================================
// feature-proposal-review barrel — SPEC-02 §3 (P5: FE-TREI-018..020)
// Internal exports for libs/trei.  Not re-exported from the public index.ts
// (lazy-loaded via router — consumed only by TREI_ROUTES).
//
// INV-1 (ADR-007): ProposalReviewPageComponent and all children guarantee
// that confirmadoPaciente is never written from TREI.  CONV (Tela 09) owns
// the patient-side approval.
// =============================================================================

export { ProposalReviewPageComponent } from './proposal-review-page.component';
export { ProposalListComponent } from './proposal-list.component';
export { ProposedNodeCardComponent } from './proposed-node-card.component';
export type { NodeActionEvent } from './proposed-node-card.component';
export { DualConfirmationComponent } from './dual-confirmation.component';
export { ApprovalSummaryBarComponent } from './approval-summary-bar.component';
export { ProposalEmptyStateComponent } from './proposal-empty-state.component';
export { ProposalEditDialogComponent } from './proposal-edit-dialog.component';
