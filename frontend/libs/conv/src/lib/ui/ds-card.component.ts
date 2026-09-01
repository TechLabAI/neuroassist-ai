import { Component, ChangeDetectionStrategy, input } from '@angular/core';

// =============================================================================
// DsCardComponent — glass-surface card (conv-local copy, W1).
// Promoted from: design-reference/.../ui/ds-card.component.ts
// TODO[Layer-2]: extract to @neuroassist/shared/ui-components once all
// promoted modules are stable. Track: CONV-FE-002 DS extraction.
// =============================================================================

type Variant = 'default' | 'elevated' | 'surface-2';
const VARIANT: Record<Variant, string> = {
  default: 'glass',
  elevated: 'glass',
  'surface-2': 'glass',
};

@Component({
  selector: 'conv-ds-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="rounded-card p-5" [class]="VARIANT[variant()]"><ng-content /></div>`,
})
export class DsCardComponent {
  readonly variant = input<Variant>('default');
  protected readonly VARIANT = VARIANT;
}
