import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { GestureTestFacade } from '../gest.facade';

// ---------------------------------------------------------------------------
// gestDeviceOnlineGuard — SPEC-05, GEST-FE-003
// Non-blocking guard: the GEST live-test thin host route (/gest) is always
// accessible (even when the edge device is offline) — it opens in degraded mode.
// The guard updates the facade's deviceOnline signal so the UI reflects the
// current device state without blocking navigation.
//
// PAC/onboarding composition slots do not use this guard (they embed
// GestureLiveTestComponent directly as a child; the host page owns navigation).
// ---------------------------------------------------------------------------

export const gestDeviceOnlineGuard: CanActivateFn = () => {
  const facade = inject(GestureTestFacade);

  // TODO[GEST-FE-003]: read actual device online status from a shared DeviceStatusService
  // (which polls EDGE service heartbeat). For now, assume online (optimistic default).
  // The facade will receive a degraded signal from the edge bridge if the device
  // is actually offline, triggering the honest-degraded banner in the UI.
  facade.setDeviceOnline(true);

  // Always allow navigation — never block the route (non-blocking guard, SPEC-05)
  return true;
};
