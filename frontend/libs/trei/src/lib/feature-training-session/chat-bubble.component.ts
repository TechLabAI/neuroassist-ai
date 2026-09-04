import {
  ChangeDetectionStrategy,
  Component,
  Input,
} from '@angular/core';
import { ChatMessage } from '../trei.types';

// =============================================================================
// ChatBubbleComponent — SPEC-02 §2 (FE-TREI-013)
//
// Presentational: renders a single conversation turn (caregiver or assistant).
// Shows proposals list and AI-disclosure badge when present (ADR-017).
// No business logic — Input only.
// =============================================================================

@Component({
  selector: 'trei-chat-bubble',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="flex gap-2"
      [class]="turn.role === 'caregiver' ? 'justify-end' : 'justify-start'"
      [attr.data-testid]="'msg-' + turn.role"
      [attr.data-message-id]="turn.id">
      <div
        class="max-w-[75%] rounded-2xl px-4 py-2.5 text-sm"
        [class]="turn.role === 'caregiver'
          ? 'bg-primary text-white'
          : 'border border-line bg-surface-2 text-ink'">

        {{ turn.content }}

        <!-- AI disclosure badge (TECH-007 / ADR-017) -->
        @if (turn.aiDisclosed) {
          <div class="mt-2 flex items-center gap-1 text-[11px] opacity-70">
            <span class="material-symbols-outlined text-[12px]">psychology</span>
            Gerado por IA — verifique antes de aprovar
          </div>
        }

        <!-- Proposals list (if any) -->
        @if (turn.proposals && turn.proposals.length > 0) {
          <div class="mt-3 flex flex-col gap-1" data-testid="proposals-list">
            @for (p of turn.proposals; track p.propostaId) {
              <div
                class="flex items-center justify-between gap-3 rounded-xl border border-line-soft bg-surface px-3 py-2 text-ink"
                [attr.data-proposal-id]="p.propostaId">
                <span class="text-sm">{{ p.texto }}</span>
                <span class="shrink-0 rounded-md bg-surface-2 px-1.5 py-0.5 text-[10px] text-ink-soft">
                  {{ p.tipo }}
                </span>
              </div>
            }
          </div>
        }
      </div>
    </div>
  `,
})
export class ChatBubbleComponent {
  @Input({ required: true }) turn!: ChatMessage;
}
