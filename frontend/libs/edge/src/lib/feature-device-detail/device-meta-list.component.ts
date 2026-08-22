import {
  Component,
  ChangeDetectionStrategy,
  Input,
  signal,
} from '@angular/core';
import { DispositivoEdgeView } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// DeviceMetaListComponent — FE-EDGE-013
// Presentational: renders a metadata list for the paired device.
// Displays device ID, version, last sync, and profile drift indicator.
//
// NOTE: @Input() is used instead of the input() signal API (ts-jest limitation).
//
// Standalone; OnPush; Signals-first; no facade dependency.
// ---------------------------------------------------------------------------

const PLACEHOLDER_DEVICE: DispositivoEdgeView = {
  deviceId: '',
  tenantRef: '',
  pacienteId: null,
  pairingState: 'registered',
  status: 'n/a',
  ultimaSincronizacao: null,
  versao: null,
  runningProfileVersion: null,
  currentProfileVersion: null,
  profileDrift: false,
  stalenessThresholdSeconds: 90,
  serverTime: new Date(0).toISOString(),
};

@Component({
  selector: 'edge-device-meta-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dl class="grid grid-cols-2 gap-4 sm:grid-cols-3">
      <div>
        <dt class="text-xs font-medium uppercase tracking-wider text-ink-faint">
          ID do dispositivo
        </dt>
        <dd class="mt-0.5 font-mono text-sm text-ink">{{ _device().deviceId }}</dd>
      </div>

      <div>
        <dt class="text-xs font-medium uppercase tracking-wider text-ink-faint">
          Versão
        </dt>
        <dd class="mt-0.5 text-sm font-medium text-ink">
          {{ _device().versao ?? '—' }}
        </dd>
      </div>

      <div>
        <dt class="text-xs font-medium uppercase tracking-wider text-ink-faint">
          Última sincronização
        </dt>
        <dd class="mt-0.5 text-sm font-medium text-ink">{{ _lastSyncRelative() }}</dd>
      </div>

      <div>
        <dt class="text-xs font-medium uppercase tracking-wider text-ink-faint">
          Perfil de detecção
        </dt>
        <dd class="mt-0.5 text-sm font-medium">
          @if (_device().profileDrift) {
            <span class="text-warning-ink">Desatualizado</span>
          } @else {
            <span class="text-ink">Atualizado</span>
          }
        </dd>
      </div>

      @if (_device().runningProfileVersion !== null) {
        <div>
          <dt class="text-xs font-medium uppercase tracking-wider text-ink-faint">
            Perfil (ativo / atual)
          </dt>
          <dd class="mt-0.5 font-mono text-sm text-ink">
            {{ _device().runningProfileVersion }} / {{ _device().currentProfileVersion }}
          </dd>
        </div>
      }

      <div>
        <dt class="text-xs font-medium uppercase tracking-wider text-ink-faint">
          Tenant
        </dt>
        <dd class="mt-0.5 font-mono text-xs text-ink-soft">{{ _device().tenantRef }}</dd>
      </div>
    </dl>
  `,
})
export class DeviceMetaListComponent {
  /** Full device snapshot from DeviceFacade. */
  @Input({ required: true }) set device(value: DispositivoEdgeView) {
    this._device.set(value);
  }

  /** Human-readable relative sync time, e.g. "há 2 min". */
  @Input() set lastSyncRelative(value: string) {
    this._lastSyncRelative.set(value);
  }

  protected readonly _device          = signal<DispositivoEdgeView>(PLACEHOLDER_DEVICE);
  protected readonly _lastSyncRelative = signal<string>('—');
}
