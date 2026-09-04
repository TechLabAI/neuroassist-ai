import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { DraftNodeVm } from '../trei.types';
import { DualConfirmationComponent } from './dual-confirmation.component';

// =============================================================================
// ProposedNodeCardComponent — SPEC-02 §3 (FE-TREI-019)
//
// Presentational: renders a single draft node card with approval/reject actions.
//
// INV-1 (ADR-007): this component exposes NO output or action that touches
// the patient side (confirmadoPaciente). Caregiver approval only.
//   - Output approve: EventEmitter<{noId; locale}> → facade.approve()
//   - Output reject:  EventEmitter<{noId; locale}> → facade.discard()
//   - PROHIBITED: @Output() confirmadoPaciente — never present in this class.
//
// The DualConfirmationComponent child receives ONLY caregiverApproved.
// =============================================================================

export interface NodeActionEvent {
  noId: string;
  locale: DraftNodeVm['locale'];
}

@Component({
  selector: 'trei-proposed-node-card',
  standalone: true,
  imports: [DualConfirmationComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="rounded-2xl border border-line bg-surface p-4 shadow-card transition-shadow
             hover:shadow-card-hover"
      [attr.data-testid]="'proposal-card'"
      [attr.data-node-id]="node.noId">

      <div class="flex items-start justify-between gap-4">
        <!-- Node text + metadata -->
        <div class="min-w-0 flex-1">
          <p class="font-semibold text-ink">{{ node.texto }}</p>
          <div class="mt-1 flex flex-wrap items-center gap-2 text-xs text-ink-soft">
            <span class="rounded-md bg-surface-2 px-1.5 py-0.5">{{ node.tipo }}</span>
            <span class="rounded-md bg-surface-2 px-1.5 py-0.5">
              {{ node.locale.toUpperCase() }}
            </span>
            <span class="rounded-md bg-surface-2 px-1.5 py-0.5 capitalize">
              {{ node.subEstado.replace('_', ' ') }}
            </span>
          </div>
        </div>

        <!-- DualConfirmationComponent (INV-1: only caregiverApproved is passed) -->
        <trei-dual-confirmation
          [caregiverApproved]="node.confirmadoCuidadora" />
      </div>

      <!-- Actions -->
      <div class="mt-3 flex flex-wrap gap-2">
        @if (!node.confirmadoCuidadora) {
          <!-- Approve button — CAREGIVER SIDE ONLY (INV-1, ADR-007) -->
          <button
            (click)="onApprove()"
            [disabled]="node.approvalInProgress || node.discardInProgress"
            data-testid="aprovar-btn"
            class="rounded-xl bg-success-bg px-3 py-1.5 text-xs font-semibold text-success-ink
                   transition-colors hover:bg-success-bg/80 disabled:cursor-not-allowed disabled:opacity-50">
            @if (node.approvalInProgress) {
              <span class="flex items-center gap-1">
                <span
                  class="h-3 w-3 animate-spin rounded-full border border-success-ink border-t-transparent"
                  aria-hidden="true"></span>
                Aprovando…
              </span>
            } @else {
              Aprovar (minha parte)
            }
          </button>
        } @else {
          <!-- P7 (FE-024): approved state — shows spinner while optimistic HTTP is in-flight -->
          <div class="flex items-center gap-1.5">
            <span
              class="rounded-xl bg-success-bg px-3 py-1.5 text-xs font-semibold text-success-ink"
              data-testid="aprovado-badge">
              ✓ Aprovado por mim
            </span>
            @if (node.approvalInProgress) {
              <!-- Optimistic pending indicator: visible while backend confirms (FE-024) -->
              <span
                class="h-3 w-3 animate-spin rounded-full border border-success-ink border-t-transparent"
                data-testid="optimistic-pending"
                aria-label="Confirmando aprovação..."
                role="status"></span>
            }
          </div>
        }

        <!-- Reject/discard button — only for rascunho or aguardando_aprovacao -->
        @if (node.subEstado === 'rascunho' || node.subEstado === 'aguardando_aprovacao') {
          <button
            (click)="onReject()"
            [disabled]="node.discardInProgress || node.approvalInProgress"
            data-testid="descartar-btn"
            class="rounded-xl border border-line px-3 py-1.5 text-xs text-ink-soft
                   transition-colors hover:bg-surface-2 disabled:cursor-not-allowed disabled:opacity-50">
            @if (node.discardInProgress) {
              Descartando…
            } @else {
              Descartar
            }
          </button>
        }
      </div>
    </div>
  `,
})
export class ProposedNodeCardComponent {
  /** The draft node to display. confirmadoPaciente is in the VM but NEVER referenced here (INV-1). */
  @Input({ required: true }) node!: DraftNodeVm;

  /** Emits when caregiver clicks approve. Carries noId + locale for facade.approve(). */
  @Output() readonly approve = new EventEmitter<NodeActionEvent>();

  /**
   * Emits when caregiver clicks discard.
   * Named 'reject' per plan P5 contract; smart parent maps to facade.discard().
   * PROHIBITED: a 'confirmadoPaciente' output — INV-1.
   */
  @Output() readonly reject = new EventEmitter<NodeActionEvent>();

  protected onApprove(): void {
    this.approve.emit({ noId: this.node.noId, locale: this.node.locale });
  }

  protected onReject(): void {
    this.reject.emit({ noId: this.node.noId, locale: this.node.locale });
  }
}
