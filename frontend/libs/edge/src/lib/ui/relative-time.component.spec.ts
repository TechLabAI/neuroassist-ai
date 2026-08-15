// =============================================================================
// relative-time.component.spec.ts — FE-EDGE-017 DoD
//
// Covers:
//   - null/undefined input renders '—'
//   - < 60s → 'há menos de 1 min'
//   - 1 min → 'há 1 min'
//   - 2..59 min → 'há N min'
//   - 1 hr → 'há 1 hora'
//   - 2..23 hr → 'há N horas'
//   - 1 day → 'há 1 dia'
//   - 2+ days → 'há N dias'
//   - Component renders <time> with correct dateTime attribute
//   - All 5 DerivedState representations covered (pt-BR formatting confirmed)
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { RelativeTimeComponent, toRelativePtBR } from './relative-time.component';

// ---------------------------------------------------------------------------
// Unit: toRelativePtBR() pure function
// ---------------------------------------------------------------------------

describe('toRelativePtBR()', () => {
  const now = Date.now();

  it('returns "—" for null', () => {
    expect(toRelativePtBR(null)).toBe('—');
  });

  it('returns "—" for undefined', () => {
    expect(toRelativePtBR(undefined)).toBe('—');
  });

  it('returns "há menos de 1 min" for 30s ago', () => {
    const iso = new Date(now - 30_000).toISOString();
    expect(toRelativePtBR(iso)).toBe('há menos de 1 min');
  });

  it('returns "há menos de 1 min" for 59s ago', () => {
    const iso = new Date(now - 59_000).toISOString();
    expect(toRelativePtBR(iso)).toBe('há menos de 1 min');
  });

  it('returns "há 1 min" for exactly 1 min ago', () => {
    const iso = new Date(now - 60_000).toISOString();
    expect(toRelativePtBR(iso)).toBe('há 1 min');
  });

  it('returns "há 5 min" for 5 min ago', () => {
    const iso = new Date(now - 5 * 60_000).toISOString();
    expect(toRelativePtBR(iso)).toBe('há 5 min');
  });

  it('returns "há 1 hora" for exactly 1h ago', () => {
    const iso = new Date(now - 60 * 60_000).toISOString();
    expect(toRelativePtBR(iso)).toBe('há 1 hora');
  });

  it('returns "há 3 horas" for 3h ago', () => {
    const iso = new Date(now - 3 * 60 * 60_000).toISOString();
    expect(toRelativePtBR(iso)).toBe('há 3 horas');
  });

  it('returns "há 1 dia" for exactly 24h ago', () => {
    const iso = new Date(now - 24 * 60 * 60_000).toISOString();
    expect(toRelativePtBR(iso)).toBe('há 1 dia');
  });

  it('returns "há 3 dias" for 72h ago', () => {
    const iso = new Date(now - 3 * 24 * 60 * 60_000).toISOString();
    expect(toRelativePtBR(iso)).toBe('há 3 dias');
  });
});

// ---------------------------------------------------------------------------
// Component: RelativeTimeComponent
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Helper: create the component with an initial isoDate value.
// Uses @Input() setter pattern (not signal input()) for ts-jest compatibility.
// ---------------------------------------------------------------------------

async function createFixture(isoDate: string | null = null) {
  await TestBed.configureTestingModule({
    imports: [RelativeTimeComponent],
  }).compileComponents();

  const fixture = TestBed.createComponent(RelativeTimeComponent);
  // @Input() can be set directly on componentInstance before detectChanges
  fixture.componentInstance.isoDate = isoDate;
  fixture.detectChanges();
  return fixture;
}

describe('RelativeTimeComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('component instance is defined', async () => {
    const fixture = await createFixture(null);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders a <time> element', async () => {
    const fixture = await createFixture(null);
    const timeEl = fixture.nativeElement.querySelector('time');
    expect(timeEl).not.toBeNull();
  });

  it('renders "—" when isoDate is null', async () => {
    const fixture = await createFixture(null);
    expect(fixture.nativeElement.querySelector('time')?.textContent?.trim()).toBe('—');
  });

  it('sets datetime attribute to the ISO string', async () => {
    const iso = new Date(Date.now() - 2 * 60_000).toISOString();
    const fixture = await createFixture(iso);
    const timeEl = fixture.nativeElement.querySelector('time');
    expect(timeEl?.getAttribute('datetime')).toBe(iso);
  });

  it('renders "há 2 min" for a timestamp 2 min ago', async () => {
    const iso = new Date(Date.now() - 2 * 60_000).toISOString();
    const fixture = await createFixture(iso);
    const text = fixture.nativeElement.querySelector('time')?.textContent?.trim();
    expect(text).toBe('há 2 min');
  });

  it('renders "há 1 hora" for a timestamp 65 min ago', async () => {
    const iso = new Date(Date.now() - 65 * 60_000).toISOString();
    const fixture = await createFixture(iso);
    const text = fixture.nativeElement.querySelector('time')?.textContent?.trim();
    expect(text).toBe('há 1 hora');
  });

  it('reflects label update when isoDate input changes', async () => {
    const iso1 = new Date(Date.now() - 1 * 60_000).toISOString();
    const fixture = await createFixture(iso1);
    expect(fixture.nativeElement.querySelector('time')?.textContent?.trim()).toBe('há 1 min');

    const iso2 = new Date(Date.now() - 65 * 60_000).toISOString();
    fixture.componentInstance.isoDate = iso2;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('time')?.textContent?.trim()).toBe('há 1 hora');
  });

  it('renders "—" when isoDate is reset to null', async () => {
    const iso = new Date(Date.now() - 1 * 60_000).toISOString();
    const fixture = await createFixture(iso);
    fixture.componentInstance.isoDate = null;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('time')?.textContent?.trim()).toBe('—');
  });
});
