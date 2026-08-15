// =============================================================================
// pairing-progress.component.spec.ts — FE-EDGE-010 step indicator
//
// Covers:
//   - Renders all 4 wizard step bubbles
//   - The correct step bubble carries aria-current="step" for each phase
// =============================================================================

import { TestBed, ComponentFixture } from '@angular/core/testing';
import { PairingProgressComponent } from './pairing-progress.component';
import { PairingPhase } from '@neuroassist/shared/contracts';

describe('PairingProgressComponent', () => {
  let fixture: ComponentFixture<PairingProgressComponent>;

  function activeBubbleIndex(): number {
    const bubbles = fixture.nativeElement.querySelectorAll<HTMLElement>('[aria-current="step"]');
    if (!bubbles.length) return -1;
    // Find index of the active bubble among the <li> items
    const listItems: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('li');
    return Array.from(listItems).findIndex(
      (li) => li.querySelector('[aria-current="step"]') !== null,
    );
  }

  function setPhase(phase: PairingPhase): void {
    fixture.componentRef.setInput('phase', phase);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PairingProgressComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(PairingProgressComponent);
    setPhase('registering');
  });

  it('renders 4 wizard step items', () => {
    const items = fixture.nativeElement.querySelectorAll('li');
    expect(items.length).toBe(4);
  });

  it('marks step 0 (Solicitar código) as active when phase is "registering"', () => {
    setPhase('registering');
    expect(activeBubbleIndex()).toBe(0);
  });

  it('marks step 1 (Aguardar agente) as active when phase is "awaiting-claim"', () => {
    setPhase('awaiting-claim');
    expect(activeBubbleIndex()).toBe(1);
  });

  it('marks step 2 (Primeiro heartbeat) as active when phase is "awaiting-first-heartbeat"', () => {
    setPhase('awaiting-first-heartbeat');
    expect(activeBubbleIndex()).toBe(2);
  });

  it('marks step 3 (Pareado) as active when phase is "paired"', () => {
    setPhase('paired');
    expect(activeBubbleIndex()).toBe(3);
  });

  it('shows check icons for completed steps', () => {
    // When phase = 'awaiting-claim', step 0 (registering) should be completed
    setPhase('awaiting-claim');
    const checkIcons = fixture.nativeElement.querySelectorAll('.material-symbols-outlined');
    const checkTexts = Array.from(checkIcons as NodeListOf<HTMLElement>)
      .map((el) => el.textContent?.trim());
    expect(checkTexts).toContain('check');
  });
});
