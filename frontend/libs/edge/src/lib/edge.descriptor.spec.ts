// =============================================================================
// edge.descriptor.spec.ts — P1 DoD unit assertions (FE-EDGE-001, FE-EDGE-003)
// Verifies the module contract constants and composition slots without
// importing Angular internals (no TestBed needed here).
// =============================================================================

import {
  MODULE_KEY,
  MODULE_MENU_METADATA,
  MODULE_PERMISSION_MAP,
  MODULE_FEATURE_FLAGS,
  EDGE_MODULE_DESCRIPTOR,
} from './edge.descriptor';

describe('EDGE Module Contract — FE-EDGE-001', () => {
  it('MODULE_KEY is "edge"', () => {
    expect(MODULE_KEY).toBe('edge');
  });

  it('MODULE_MENU_METADATA.showInNav is false — EDGE is a composed surface, not a primary nav item', () => {
    expect(MODULE_MENU_METADATA.showInNav).toBe(false);
  });

  it('MODULE_MENU_METADATA.key matches MODULE_KEY', () => {
    expect(MODULE_MENU_METADATA.key).toBe(MODULE_KEY);
  });

  it('MODULE_PERMISSION_MAP defines edge.device.read', () => {
    expect(MODULE_PERMISSION_MAP['edge.device.read']).toBeDefined();
    expect(MODULE_PERMISSION_MAP['edge.device.read'].length).toBeGreaterThan(0);
  });

  it('MODULE_PERMISSION_MAP defines edge.manage (conductor-only)', () => {
    expect(MODULE_PERMISSION_MAP['edge.manage']).toBeDefined();
    expect(MODULE_PERMISSION_MAP['edge.manage']).toContain('CONDUCTOR');
  });
});

describe('EDGE Feature Flags — threshold invariant (FE-EDGE-001)', () => {
  it('stalenessThresholdMs < offlineWindowMs (stale band precedes offline; false-green impossible)', () => {
    const { stalenessThresholdMs, offlineWindowMs } = MODULE_FEATURE_FLAGS;
    expect(stalenessThresholdMs.default).toBeLessThan(offlineWindowMs.default);
  });

  it('pollIntervalMs < stalenessThresholdMs (poll cadence fits inside the stale window)', () => {
    const { pollIntervalMs, stalenessThresholdMs } = MODULE_FEATURE_FLAGS;
    expect(pollIntervalMs.default).toBeLessThan(stalenessThresholdMs.default);
  });
});

describe('EDGE_MODULE_DESCRIPTOR — composition slots (FE-EDGE-003)', () => {
  it('key matches MODULE_KEY', () => {
    expect(EDGE_MODULE_DESCRIPTOR.key).toBe(MODULE_KEY);
  });

  it('connectivityReadModel slot is defined and is a function (typed, not any/undefined)', () => {
    expect(EDGE_MODULE_DESCRIPTOR.connectivityReadModel).toBeDefined();
    expect(typeof EDGE_MODULE_DESCRIPTOR.connectivityReadModel).toBe('function');
  });

  it('connectivityReadModel returns a Promise (lazy-loaded chip component)', () => {
    // The function must return a thenable — scope:web mounts it via NgComponentOutlet
    const result = EDGE_MODULE_DESCRIPTOR.connectivityReadModel();
    expect(result).toBeInstanceOf(Promise);
  });

  it('pairingComponent slot is defined and is a function (typed, not any/undefined)', () => {
    expect(EDGE_MODULE_DESCRIPTOR.pairingComponent).toBeDefined();
    expect(typeof EDGE_MODULE_DESCRIPTOR.pairingComponent).toBe('function');
  });

  it('pairingComponent returns a Promise (lazy-loaded pairing page)', () => {
    const result = EDGE_MODULE_DESCRIPTOR.pairingComponent();
    expect(result).toBeInstanceOf(Promise);
  });

  it('entry function is defined (lazy routes loader for shell registration)', () => {
    expect(EDGE_MODULE_DESCRIPTOR.entry).toBeDefined();
    expect(typeof EDGE_MODULE_DESCRIPTOR.entry).toBe('function');
  });

  it('descriptor menu showInNav is false (no top-level nav slot)', () => {
    expect(EDGE_MODULE_DESCRIPTOR.menu.showInNav).toBe(false);
  });
});
