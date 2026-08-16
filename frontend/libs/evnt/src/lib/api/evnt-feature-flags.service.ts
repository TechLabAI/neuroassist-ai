import { Injectable, InjectionToken, inject } from '@angular/core';
import { EVNT_MODULE_DESCRIPTOR } from '../evnt.descriptor';

// =============================================================================
// EvntFeatureFlagsService — INT-FE-EVNT-005 / P6-D
//
// Local feature-flag service for the EVNT module.
// Reads runtime flags from EVNT_FEATURE_FLAGS_OVERRIDE token (if provided),
// falling back to EVNT_MODULE_DESCRIPTOR.featureFlags compile-time defaults.
//
// Flags consumed by EventDeliveryFacade:
//   liveChannel    — 'on' | 'off': whether SSE is attempted (default 'on')
//   pollIntervalMs — number: polling interval when degraded (default 15000)
//   routineNotices — 'on' | 'off': whether routine notices are shown (default 'on')
//
// Override in test environment:
//   providers: [{ provide: EVNT_FEATURE_FLAGS_OVERRIDE, useValue: { liveChannel: 'off' } }]
// =============================================================================

/** Shape of overridable EVNT feature flags. */
export interface EvntFeatureFlags {
  liveChannel?: 'on' | 'off';
  pollIntervalMs?: number;
  routineNotices?: 'on' | 'off';
}

/** DI token for runtime feature flag overrides — optional, default null. */
export const EVNT_FEATURE_FLAGS_OVERRIDE = new InjectionToken<EvntFeatureFlags | null>(
  'EVNT_FEATURE_FLAGS_OVERRIDE',
  { providedIn: 'root', factory: () => null },
);

/** Compile-time defaults from the module descriptor. */
const DESCRIPTOR_DEFAULTS = EVNT_MODULE_DESCRIPTOR.featureFlags;

@Injectable({ providedIn: 'root' })
export class EvntFeatureFlagsService {
  private readonly override = inject(EVNT_FEATURE_FLAGS_OVERRIDE, { optional: true });

  /**
   * Read a boolean flag ('on'|'off') by key.
   * Priority: runtime override > descriptor defaults > fallback.
   */
  getBoolean(key: 'liveChannel' | 'routineNotices', fallback: boolean): boolean {
    const overrideVal = this.override?.[key];
    if (overrideVal !== undefined) return overrideVal === 'on';

    const descriptorVal = DESCRIPTOR_DEFAULTS[key as keyof typeof DESCRIPTOR_DEFAULTS];
    if (descriptorVal !== undefined) return descriptorVal === 'on';

    return fallback;
  }

  /**
   * Read a numeric flag by key.
   * Priority: runtime override > fallback (descriptor has no numeric flags yet).
   */
  getNumber(key: 'pollIntervalMs', fallback: number): number {
    const overrideVal = this.override?.[key];
    if (overrideVal !== undefined) return overrideVal;
    return fallback;
  }

  /** Is the SSE live channel enabled? (default: true per descriptor) */
  isLiveChannelEnabled(): boolean {
    return this.getBoolean('liveChannel', true);
  }

  /** Polling interval in milliseconds when SSE is degraded (default: 15000ms) */
  getPollIntervalMs(): number {
    return this.getNumber('pollIntervalMs', 15_000);
  }

  /** Are routine notices enabled? (default: true per descriptor) */
  areRoutineNoticesEnabled(): boolean {
    return this.getBoolean('routineNotices', true);
  }
}
