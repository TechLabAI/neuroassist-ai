import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { DraftNodeVm, TreiLocale } from '../trei.types';
import { ProposedNodeCardComponent, NodeActionEvent } from './proposed-node-card.component';

// =============================================================================
// ProposalListComponent — SPEC-02 §3 (FE-TREI-019)
//
// Presentational: renders the list of ProposedNodeCards for the active locale.
// Bubbles up approve/reject events from child cards to the smart parent.
//
// INV-1 flows through: ProposedNodeCard guarantees no confirmadoPaciente output.
// =============================================================================

@Component({
  selector: 'trei-proposal-list',
  standalone: true,
  imports: [ProposedNodeCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="flex flex-col gap-3"
      data-testid="proposal-list"
      [attr.aria-label]="'Lista de propostas (' + nodes.length + ')'">
      @for (node of nodes; track node.noId) {
        <trei-proposed-node-card
          [node]="node"
          (approve)="approve.emit($event)"
          (reject)="reject.emit($event)" />
      }
    </div>
  `,
})
export class ProposalListComponent {
  /** Draft nodes to render (already filtered for active locale by facade). */
  @Input({ required: true }) nodes!: DraftNodeVm[];

  /** Active locale — informational, not used in template but available for future use. */
  @Input({ required: true }) activeLocale!: TreiLocale;

  /** Bubbled up from ProposedNodeCardComponent → facade.approve() */
  @Output() readonly approve = new EventEmitter<NodeActionEvent>();

  /** Bubbled up from ProposedNodeCardComponent → facade.discard() */
  @Output() readonly reject = new EventEmitter<NodeActionEvent>();
}
