import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { GestureTestFacade } from '../gest.facade';

// ---------------------------------------------------------------------------
// gestDeviceOnlineGuard — GEST-FE-017
// Non-blocking: navigation always proceeds. Its only job is to seed the
// facade's deviceOnline/degraded state from browser connectivity before the
// live-test surface mounts, so an offline device starts in honest-degraded
// mode instead of only discovering it once openStream() fails.
// ---------------------------------------------------------------------------

export const gestDeviceOnlineGuard: CanActivateFn = () => {
  const facade = inject(GestureTestFacade);
  facade.setDeviceOnline(navigator.onLine, navigator.onLine ? null : 'device-offline');
  return true;
};
