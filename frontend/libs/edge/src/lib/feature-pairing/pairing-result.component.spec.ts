// =============================================================================
// pairing-result.component.spec.ts — FE-EDGE-010 DoD
//
// Covers:
//   - Renders success state ("Dispositivo pareado!") when phase is "paired"
//   - Renders expiry state ("Código expirado") when phase is "expired"
//   - Renders error state ("Erro no pareamento") when phase is "error"
//   - Shows versao when phase is "paired" and versao is provided
//   - Shows errorMessage when phase is "expired" or "error"
//   - Emits `retry` when "Novo código" button is clicked (expired phase)
//   - Emits `retry` when "Tentar novamente" button is clicked (error phase)
//   - Renders navigation link when phase is "paired"
//   - Renders nothing when phase is "idle" (no outcome yet)
// =============================================================================

import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PairingResultComponent } from './pairing-result.component';
import { PairingPhase } from '@neuroassist/shared/contracts';

describe('PairingResultComponent', () => {
  let fixture: ComponentFixture<PairingResultComponent>;
  let component: PairingResultComponent;

  function setPhase(
    phase: PairingPhase,
    versao: string | null = null,
    errorMessage: string | null = null,
  ): void {
    fixture.componentRef.setInput('phase', phase);
    fixture.componentRef.setInput('versao', versao);
    fixture.componentRef.setInput('errorMessage', errorMessage);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PairingResultComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(PairingResultComponent);
    component = fixture.componentInstance;
    setPhase('idle');
  });

  afterEach(() => TestBed.resetTestingModule());

  // ---------------------------------------------------------------------------
  // Idle — no visible result content
  // ---------------------------------------------------------------------------

  it('renders no result content when phase is "idle"', () => {
    setPhase('idle');
    const text = (fixture.nativeElement as HTMLElement).textContent?.trim() ?? '';
    // None of the outcome labels should appear
    expect(text).not.toContain('Dispositivo pareado!');
    expect(text).not.toContain('Código expirado');
    expect(text).not.toContain('Erro no pareamento');
  });

  // ---------------------------------------------------------------------------
  // Success state — phase "paired"
  // ---------------------------------------------------------------------------

  it('renders "Dispositivo pareado!" when phase is "paired"', () => {
    setPhase('paired');
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Dispositivo pareado!');
  });

  it('shows versao when phase is "paired" and versao is provided', () => {
    setPhase('paired', '2.1.0');
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('2.1.0');
  });

  it('renders a navigation link when phase is "paired"', () => {
    setPhase('paired');
    const link = fixture.nativeElement.querySelector('a[routerLink]');
    expect(link).not.toBeNull();
    expect((link as HTMLElement).textContent).toContain('Ver dispositivo');
  });

  // ---------------------------------------------------------------------------
  // Expiry state — phase "expired"
  // ---------------------------------------------------------------------------

  it('renders "Código expirado" when phase is "expired"', () => {
    setPhase('expired');
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Código expirado');
  });

  it('shows errorMessage under expired state', () => {
    setPhase('expired', null, 'O código de uso único expirou');
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('O código de uso único expirou');
  });

  it('emits `retry` when "Novo código" button is clicked in expired state', () => {
    setPhase('expired');
    const retrySpy = jest.fn();
    component.retry.subscribe(retrySpy);

    const buttons = fixture.nativeElement.querySelectorAll<HTMLElement>('button');
    const btn = Array.from(buttons).find((b) => b.textContent?.includes('Novo código'));
    expect(btn).toBeTruthy();
    btn!.click();
    fixture.detectChanges();

    expect(retrySpy).toHaveBeenCalledTimes(1);
  });

  // ---------------------------------------------------------------------------
  // Error state — phase "error"
  // ---------------------------------------------------------------------------

  it('renders "Erro no pareamento" when phase is "error"', () => {
    setPhase('error');
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Erro no pareamento');
  });

  it('shows errorMessage under error state', () => {
    setPhase('error', null, 'Falha de rede');
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Falha de rede');
  });

  it('emits `retry` when "Tentar novamente" button is clicked in error state', () => {
    setPhase('error');
    const retrySpy = jest.fn();
    component.retry.subscribe(retrySpy);

    const buttons = fixture.nativeElement.querySelectorAll<HTMLElement>('button');
    const btn = Array.from(buttons).find((b) => b.textContent?.includes('Tentar novamente'));
    expect(btn).toBeTruthy();
    btn!.click();
    fixture.detectChanges();

    expect(retrySpy).toHaveBeenCalledTimes(1);
  });

  // ---------------------------------------------------------------------------
  // Mutual exclusivity — only one state block at a time
  // ---------------------------------------------------------------------------

  it('does not render the success block when phase is "error"', () => {
    setPhase('error');
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Dispositivo pareado!');
  });

  it('does not render the error block when phase is "paired"', () => {
    setPhase('paired');
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Erro no pareamento');
  });
});
