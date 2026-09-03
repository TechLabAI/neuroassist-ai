import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { FormsModule } from '@angular/forms';

// =============================================================================
// ChatComposerComponent — SPEC-02 §2 (FE-TREI-014)
//
// Presentational: caregiver text input + send button.
// Disabled while the assistant is thinking (parent passes disabled=true).
// Clears itself on submit — no state leaked to parent.
// =============================================================================

@Component({
  selector: 'trei-chat-composer',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex gap-2" data-testid="chat-composer">
      <input
        [(ngModel)]="text"
        (keyup.enter)="onSubmit()"
        [disabled]="disabled"
        placeholder="Descreva o que Márcio precisa falar…"
        data-testid="composer-input"
        class="flex-1 rounded-xl border border-line bg-surface px-4 py-2.5 text-sm
               text-ink placeholder:text-ink-faint focus:border-primary focus:outline-none
               disabled:opacity-50" />
      <button
        (click)="onSubmit()"
        [disabled]="!text.trim() || disabled"
        data-testid="send-btn"
        class="rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-white
               transition-colors hover:bg-primary-dark disabled:opacity-50">
        <span class="material-symbols-outlined text-[18px]">send</span>
      </button>
    </div>
  `,
})
export class ChatComposerComponent {
  @Input() disabled = false;
  @Output() readonly submitTurn = new EventEmitter<string>();

  protected text = '';

  protected onSubmit(): void {
    const trimmed = this.text.trim();
    if (!trimmed || this.disabled) return;
    this.submitTurn.emit(trimmed);
    this.text = '';
  }
}
