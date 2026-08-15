// =============================================================================
// pairing-code-card.component.spec.ts — FE-EDGE-009 DoD
//
// Covers:
//   - Renders token value in the template
//   - Emits `expired` when countdown transitions from > 0 to 0
//   - Does NOT emit `expired` when countdown starts at 0 (guard: no spurious emit)
//   - Emits `copied` when copy button is clicked
// =============================================================================

import { TestBed, ComponentFixture } from '@angular/core/testing';
import { PairingCodeCardComponent } from './pairing-code-card.component';
import { By } from '@angular/platform-browser';

describe('PairingCodeCardComponent', () => {
  let fixture: ComponentFixture<PairingCodeCardComponent>;
  let component: PairingCodeCardComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PairingCodeCardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(PairingCodeCardComponent);
    component = fixture.componentInstance;

    // Set required inputs with a positive countdown
    fixture.componentRef.setInput('token', 'ABCD-1234');
    fixture.componentRef.setInput('countdown', 120);
    fixture.detectChanges();
  });

  // ---------------------------------------------------------------------------
  // Rendering
  // ---------------------------------------------------------------------------

  it('renders the token value in the template', () => {
    const tokenEl = fixture.nativeElement.querySelector('.font-mono');
    expect(tokenEl?.textContent?.trim()).toBe('ABCD-1234');
  });

  it('renders the countdown value', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('120s');
  });

  it('shows a copy button', () => {
    const btn = fixture.debugElement.query(By.css('button'));
    expect(btn).toBeTruthy();
  });

  // ---------------------------------------------------------------------------
  // `expired` output — FE-EDGE-009 DoD: countdown reaches 0 → emit expired
  // ---------------------------------------------------------------------------

  it('emits `expired` when countdown transitions from > 0 to 0', () => {
    const expiredSpy = jest.fn();
    component.expired.subscribe(expiredSpy);

    // Simulate countdown reaching 0
    fixture.componentRef.setInput('countdown', 0);
    fixture.detectChanges();

    expect(expiredSpy).toHaveBeenCalledTimes(1);
  });

  it('does NOT emit `expired` when countdown starts at 0 from the beginning', async () => {
    // Create a fresh component with countdown=0 from the start
    const freshFixture = TestBed.createComponent(PairingCodeCardComponent);
    const freshComponent = freshFixture.componentInstance;

    const expiredSpy = jest.fn();
    freshComponent.expired.subscribe(expiredSpy);

    freshFixture.componentRef.setInput('token', 'XYZ-9999');
    freshFixture.componentRef.setInput('countdown', 0); // starts at 0 — no prior positive value
    freshFixture.detectChanges();

    // hadPositive was never set — expired should NOT fire
    expect(expiredSpy).not.toHaveBeenCalled();
  });

  it('does NOT emit `expired` while countdown is still positive', () => {
    const expiredSpy = jest.fn();
    component.expired.subscribe(expiredSpy);

    // Countdown still positive
    fixture.componentRef.setInput('countdown', 60);
    fixture.detectChanges();

    expect(expiredSpy).not.toHaveBeenCalled();
  });

  // ---------------------------------------------------------------------------
  // `copied` output
  // ---------------------------------------------------------------------------

  it('emits `copied` when the copy button is clicked', () => {
    const copiedSpy = jest.fn();
    component.copied.subscribe(copiedSpy);

    // Stub clipboard API (not available in jsdom)
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: jest.fn().mockResolvedValue(undefined) },
      configurable: true,
    });

    const btn = fixture.debugElement.query(By.css('button'));
    btn.nativeElement.click();
    fixture.detectChanges();

    expect(copiedSpy).toHaveBeenCalledTimes(1);
  });
});
