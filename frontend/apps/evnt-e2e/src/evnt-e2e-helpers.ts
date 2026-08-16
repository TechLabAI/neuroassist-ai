/**
 * evnt-e2e-helpers.ts — shared helpers for all EVNT Playwright specs.
 *
 * Navigation strategy:
 *   All specs navigate to /eventos using navigateToEvnt() which waits for
 *   the first meaningful EVNT element — either the alarm overlay or the
 *   feed page title. This handles the APP_INITIALIZER delay (~400 ms mock timer)
 *   and the EventFeedPageComponent loadHistory() delay (another ~400 ms).
 *
 * Mock adapter flags (set via page.addInitScript BEFORE page.goto):
 *   __EVNT_E2E_NO_EMERGENCY__   → listActive() returns activeEvent: null
 *   __EVNT_E2E_SSE_FAIL__       → openStream() throws → degraded mode
 *   __EVNT_E2E_CONFIRM_FAIL__   → confirm() throws network error
 *   __EVNT_E2E_CONFIRM_409__    → confirm() throws /errors/feedback-terminal
 */
import { Page } from '@playwright/test';

// ---------------------------------------------------------------------------
// Selector constants
// ---------------------------------------------------------------------------

export const SEL = {
  alarmOverlay: '[data-testid="alarm-overlay"]',
  confirmBtn: '[role="alertdialog"] button',
  routineNotice: '[data-testid="routine-notice"]',
  degradedBanner: '[data-testid="degraded-banner"]',
  emptyState: '[data-testid="empty-state"]',
  feedTitle: 'h1.page-title',
} as const;

// ---------------------------------------------------------------------------
// Navigation helpers
// ---------------------------------------------------------------------------

/**
 * Navigate to /eventos and wait for the EVNT module to be ready.
 * Waits up to 8 s for either the alarm overlay (emergency present) or the
 * feed page title (no emergency, content rendered). This covers:
 *   - APP_INITIALIZER delay (~400 ms mock timer in hydrate())
 *   - loadHistory() delay (~400 ms mock timer in EventFeedPageComponent.ngOnInit())
 *   - Angular change detection cycle
 */
export async function navigateToEvnt(page: Page): Promise<void> {
  await page.goto('/eventos');
  // Wait for EVNT module to mount — either overlay (emergency) or feed title
  await page.waitForSelector(
    `${SEL.alarmOverlay}, ${SEL.feedTitle}, ${SEL.routineNotice}, ${SEL.emptyState}`,
    { timeout: 8000 },
  );
}

/**
 * Navigate to /eventos expecting an active alarm to be visible.
 * Use when the default mock seed (emergency active) is in effect.
 */
export async function navigateToEvntWithAlarm(page: Page): Promise<void> {
  await page.goto('/eventos');
  await page.waitForSelector(SEL.alarmOverlay, { timeout: 8000 });
}

/**
 * Navigate to /eventos expecting routine notices (no emergency flag set).
 * Use with __EVNT_E2E_NO_EMERGENCY__ flag.
 */
export async function navigateToEvntNoEmergency(page: Page): Promise<void> {
  await page.goto('/eventos');
  // Wait for content to render (loading=false after loadHistory completes ~400ms)
  await page.waitForSelector(
    `${SEL.routineNotice}, ${SEL.emptyState}`,
    { timeout: 8000 },
  );
}
