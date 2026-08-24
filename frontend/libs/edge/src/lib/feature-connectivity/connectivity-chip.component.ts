import {
  Component,
  ChangeDetectionStrategy,
  inject,
  input,
  output,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { DeviceConnectivityVM, ConnectivityTone } from '@neuroassist/shared/contracts';
import { DeviceFacade } from '../edge.facade';

// ---------------------------------------------------------------------------
// ConnectivityChipComponent — SPEC-06 §5 / SPEC-01 §1
// Always-visible topbar chip; reflects DerivedState (never raw status flag).
// Clicking navigates to the device-detail route (ADR-005, SPEC-01 §1 note).
//
// Consumed by the scope:web topbar via EdgeShellIntegrationService.
// Standalone; OnPush; no NgModule.
// ---------------------------------------------------------------------------

function toneClass(tone: ConnectivityTone): string {
  switch (tone) {
    case 'ok': return 'bg-success-bg text-success-ink';
    case 'warn': return 'bg-warning-bg text-warning-ink';
    case 'er': return 'bg-danger-bg text-danger-ink';
    default: return 'bg-surface-2 text-ink-soft';
  }
}

function toneIcon(tone: ConnectivityTone): string {
  switch (tone) {
    case 'ok': return 'wifi';
    case 'warn': return 'wifi_find';
    case 'er': return 'wifi_off';
    default: return 'device_unknown';
  }
}

@Component({
  selector: 'edge-connectivity-chip',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (vm(); as connectivity) {
      <a
        [routerLink]="['/edge', 'device']"
        class="flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold transition-opacity hover:opacity-80"
        [class]="toneClass(connectivity.tone)"
        [attr.aria-label]="connectivity.label"
        [attr.title]="connectivity.lastSyncRelative ? ('Última sincronização ' + connectivity.lastSyncRelative) : connectivity.label"
      >
        <span aria-hidden="true" class="material-symbols-outlined text-[16px]">{{ toneIcon(connectivity.tone) }}</span>
        <span class="hidden sm:inline">{{ connectivity.label }}</span>
      </a>
    } @else {
      <span class="flex items-center gap-1.5 rounded-full bg-surface-2 px-2.5 py-1 text-xs font-semibold text-ink-soft">
        <span aria-hidden="true" class="material-symbols-outlined text-[16px]">device_unknown</span>
        <span class="hidden sm:inline">Sem dispositivo</span>
      </span>
    }
  `,
})
export class ConnectivityChipComponent {
  private readonly facade = inject(DeviceFacade);

  /** External VM override (composition slot). When provided, takes precedence over the facade.
   * Consumers bind: <edge-connectivity-chip [vmExternal]="signal()" /> */
  readonly vmExternal = input<DeviceConnectivityVM | null>(null);

  /** Open action emitted when the chip is clicked (for host pages that want to intercept). */
  readonly open = output<void>();

  /** Resolved VM: external override first, then facade signal. */
  readonly vm = () => this.vmExternal() ?? this.facade.connectivity();

  protected readonly toneClass = toneClass;
  protected readonly toneIcon = toneIcon;
}
