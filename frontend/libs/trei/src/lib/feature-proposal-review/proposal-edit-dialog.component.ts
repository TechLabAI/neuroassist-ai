import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  signal,
} from '@angular/core';

// =============================================================================
// ProposalEditDialogComponent — SPEC-02 §3 (FE-TREI-020)
//
// Presentational: inline edit dialog for a proposed node's text before final
// approval. The parent smart component controls visibility via isOpen.
//
// No business logic — inputs + outputs only.
// Accepts draftText as initial value; tracks edits in a local signal.
// =============================================================================

@Component({
  selector: 'trei-proposal-edit-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen) {
      <!-- Backdrop -->
      <div
        class="fixed inset-0 z-40 bg-ink/30 backdrop-blur-sm"
        (click)="dismiss.emit()"
        data-testid="dialog-backdrop"
        aria-hidden="true">
      </div>

      <!-- Dialog panel -->
      <div
        class="fixed left-1/2 top-1/2 z-50 w-full max-w-lg -translate-x-1/2 -translate-y-1/2
               rounded-2xl bg-surface p-6 shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="edit-dialog-title"
        data-testid="proposal-edit-dialog">

        <h2
          id="edit-dialog-title"
          class="mb-4 font-display text-lg font-bold text-ink">
          Editar proposta
        </h2>

        <!-- Text area — no FormsModule; uses event binding to signal -->
        <textarea
          [value]="editedText()"
          (input)="onInput($event)"
          [attr.maxlength]="maxLen"
          rows="4"
          class="w-full rounded-xl border border-line bg-surface-2 p-3 text-sm text-ink
                 focus:border-primary focus:outline-none resize-none"
          data-testid="edit-textarea"
          aria-label="Texto da proposta"
          aria-describedby="char-count">
        </textarea>

        <p
          id="char-count"
          class="mt-1 text-right text-xs"
          [class]="editedText().length >= maxLen ? 'text-error-ink' : 'text-ink-faint'"
          data-testid="char-count">
          {{ editedText().length }}/{{ maxLen }}
        </p>

        <div class="mt-4 flex justify-end gap-2">
          <button
            (click)="dismiss.emit()"
            data-testid="cancel-btn"
            class="rounded-xl border border-line px-4 py-2 text-sm text-ink-soft
                   transition-colors hover:bg-surface-2">
            Cancelar
          </button>
          <button
            (click)="onSave()"
            [disabled]="editedText().trim().length === 0"
            data-testid="save-btn"
            class="rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-white
                   transition-colors hover:bg-primary-dark disabled:cursor-not-allowed disabled:opacity-50">
            Salvar
          </button>
        </div>
      </div>
    }
  `,
})
export class ProposalEditDialogComponent implements OnChanges {
  /** Initial text of the draft node; reset to this when draftText changes. */
  @Input({ required: true }) draftText!: string;

  /** Maximum character count enforced in the textarea. */
  @Input() maxLen = 120;

  /** Whether the dialog is visible (controlled by smart parent). */
  @Input() isOpen = false;

  /** Emitted with the trimmed edited text when the user saves. */
  @Output() readonly save = new EventEmitter<string>();

  /** Emitted when the user dismisses (backdrop click or Cancel button). */
  @Output() readonly dismiss = new EventEmitter<void>();

  /** Local editing state — a signal so the template updates efficiently. */
  protected readonly editedText = signal('');

  ngOnChanges(changes: SimpleChanges): void {
    // Reset editing buffer whenever the parent swaps the draftText.
    if (changes['draftText'] != null) {
      this.editedText.set(this.draftText ?? '');
    }
  }

  protected onInput(event: Event): void {
    this.editedText.set((event.target as HTMLTextAreaElement).value);
  }

  protected onSave(): void {
    const trimmed = this.editedText().trim();
    if (trimmed.length > 0) {
      this.save.emit(trimmed);
    }
  }
}
