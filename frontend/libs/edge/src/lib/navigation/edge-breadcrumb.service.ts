import { Injectable, inject } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';
import { EDGE_BREADCRUMB_PORT, BreadcrumbItem } from './breadcrumb.port';

// ---------------------------------------------------------------------------
// EdgeBreadcrumbService — FE-EDGE-016 (SPEC-01 §2, SPEC-06 §3)
//
// Pushes a breadcrumb trail to the shell's BreadcrumbPort after every
// NavigationEnd event that lands on an EDGE-owned route (/edge/*).
//
// Composed context (topbar chip, onboarding step-3):
//   The current URL will NOT start with /edge/ because the host page owns the
//   router state.  When the URL is outside /edge/*, the service stays silent —
//   no breadcrumb is contributed and the host page trail is left untouched.
//
// Three breadcrumb paths (SPEC-01 §2 Breadcrumb column):
//   /edge/device           → [Dispositivo]
//   /edge/device/:deviceId → [Dispositivo]           (detail; same segment)
//   /edge/pairing          → [Dispositivo › Parear]
//   /edge/device/history   → [Dispositivo › Histórico]  (reserved — no route yet)
//
// The service is providedIn: 'root' so it is created on first injection and
// lives for the entire app lifetime.  The Router subscription needs no manual
// teardown because both Router and this service share the same lifetime.
// ---------------------------------------------------------------------------

const EDGE_BASE = '/edge';

@Injectable({ providedIn: 'root' })
export class EdgeBreadcrumbService {
  private readonly router = inject(Router);
  private readonly breadcrumbPort = inject(EDGE_BREADCRUMB_PORT);

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(() => this.handleNavigationEnd());
  }

  private handleNavigationEnd(): void {
    // Strip query string to get the clean pathname.
    const url = this.router.url.split('?')[0];

    // Composed context guard: EDGE surfaces composed into onboarding step-3 or
    // the topbar chip live inside the host shell — the host URL is NOT /edge/*.
    // Stay silent so the host page breadcrumb trail is never clobbered.
    const isEdgeRoute =
      url === EDGE_BASE ||
      url.startsWith(`${EDGE_BASE}/`);

    if (!isEdgeRoute) {
      return;
    }

    this.breadcrumbPort.push(this.buildTrail(url));
  }

  /**
   * Maps an EDGE URL to its full breadcrumb trail.
   *
   * Rules (SPEC-01 §2):
   *  /edge/pairing          → [Dispositivo, Parear]
   *  /edge/device/history   → [Dispositivo, Histórico]   (reserved path)
   *  everything else        → [Dispositivo]
   */
  buildTrail(url: string): BreadcrumbItem[] {
    if (url === `${EDGE_BASE}/pairing`) {
      return [
        { label: 'Dispositivo', url: `${EDGE_BASE}/device` },
        { label: 'Parear', url: `${EDGE_BASE}/pairing` },
      ];
    }

    if (url === `${EDGE_BASE}/device/history`) {
      return [
        { label: 'Dispositivo', url: `${EDGE_BASE}/device` },
        { label: 'Histórico', url: `${EDGE_BASE}/device/history` },
      ];
    }

    // /edge, /edge/device, /edge/device/:deviceId → root-level trail
    return [{ label: 'Dispositivo', url: `${EDGE_BASE}/device` }];
  }
}
