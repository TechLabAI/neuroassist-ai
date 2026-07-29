import { Component, ChangeDetectionStrategy, input } from '@angular/core';

// ds-card — local copy for libs/pac (source: prototype ui/ds-card.component.ts)
// TODO: migrate to @neuroassist/shared/ui-components when that lib is fleshed out.

type Variant = 'default' | 'elevated' | 'surface-2';
const VARIANT: Record<Variant, string> = {
  default:     'glass',
  elevated:    'glass',
  'surface-2': 'glass',
};

@Component({
  selector: 'pac-ds-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="rounded-card p-5" [class]="VARIANT[variant()]"><ng-content /></div>`,
})
export class PacDsCardComponent {
  readonly variant = input<Variant>('default');
  protected readonly VARIANT = VARIANT;
}
