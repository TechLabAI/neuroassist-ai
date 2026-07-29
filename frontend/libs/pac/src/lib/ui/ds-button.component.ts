import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';

// ds-button — local copy for libs/pac (source: prototype ui/ds-button.component.ts)
// TODO: migrate to @neuroassist/shared/ui-components when that lib is fleshed out.

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';
type Size = 'default' | 'sm' | 'icon';

const SIZE: Record<Size, string> = {
  default: 'h-9 px-3.5 text-sm',
  sm:      'h-[30px] px-3 text-xs',
  icon:    'size-9 p-0',
};
const VARIANT: Record<Variant, string> = {
  primary:   'bg-brand text-brand-ink hover:bg-brand-strong shadow-card',
  secondary: 'border-[1.5px] border-line-strong text-ink-soft hover:bg-surface',
  ghost:     'text-ink-soft hover:bg-surface-2',
  danger:    'text-danger hover:bg-danger-bg',
};

@Component({
  selector: 'pac-ds-button',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<button
    class="inline-flex items-center justify-center gap-2 rounded-md font-body font-medium transition-colors disabled:opacity-45 disabled:cursor-not-allowed"
    [class]="cls()"
    [disabled]="disabled()">
    <ng-content />
  </button>`,
})
export class PacDsButtonComponent {
  readonly variant = input<Variant>('primary');
  readonly size = input<Size>('default');
  readonly disabled = input(false);
  protected readonly cls = computed(() => `${SIZE[this.size()]} ${VARIANT[this.variant()]}`);
}
