import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal, WritableSignal } from '@angular/core';
import { StatusChipComponent } from './status-chip.component';
import type { ChipStatus } from './status-chip.component';

// =============================================================================
// StatusChipComponent spec — FE-TREI-021 (P6)
//
// Signal-input testing strategy for ts-jest (no jest-preset-angular):
//
// This workspace uses ts-jest without the Angular compiler transforms that
// jest-preset-angular would apply. As a result, Angular's JIT compiler cannot
// wire signal input.required<T>() metadata during TestBed compilation:
//   - ComponentRef.setInput() throws NG0303 (input not in ɵcmp.inputs map)
//   - A host-wrapper template binding ([status]="val") leaves the signal
//     uninitialised, causing NG0950 when status() is first called
//
// The established project workaround (see gesture-live-test.component.spec.ts)
// for required signal inputs is to supply the value through a mechanism that
// bypasses the JIT wiring gap. Here we replace the component instance's
// `status` property with a WritableSignal before the first detectChanges():
//   - TypeScript readonly is compile-time only; the JS property is writable
//   - Angular's computed() is lazy: config() and chipClass() capture their
//     reactive dependency the first time they are evaluated, so the writable
//     signal is picked up instead of the original InputSignal
//   - Calling statusSignal.set(newValue) invalidates the computed cache and
//     triggers a correct re-render on the next detectChanges()
//
// The component's public API is NOT changed: input.required<ChipStatus>() stays
// required; the workaround is entirely in the spec scaffolding.
// =============================================================================

describe('StatusChipComponent', () => {
  let fixture: ComponentFixture<StatusChipComponent>;
  let nativeEl: HTMLElement;
  let statusSignal: WritableSignal<ChipStatus>;

  function setStatus(status: ChipStatus): void {
    statusSignal.set(status);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusChipComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(StatusChipComponent);

    // Replace the required InputSignal with a writable signal that has an
    // initial value. This prevents NG0950 on the first detectChanges() while
    // keeping all computed() signals reactive (they lazily capture statusSignal
    // as their dependency on first evaluation).
    statusSignal = signal<ChipStatus>('ACTIVE');
    (fixture.componentInstance as Record<string, unknown>)['status'] = statusSignal;

    fixture.detectChanges();
    nativeEl = fixture.nativeElement as HTMLElement;
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render chip element', () => {
    const chip = nativeEl.querySelector('[data-status]');
    expect(chip).not.toBeNull();
  });

  describe('SessionStatus variants', () => {
    it('should render ACTIVE with label "Ativa"', () => {
      setStatus('ACTIVE');
      const chip = nativeEl.querySelector('[data-status="ACTIVE"]');
      expect(chip).not.toBeNull();
      expect(chip?.textContent?.trim()).toBe('Ativa');
    });

    it('should apply chip--active class for ACTIVE', () => {
      setStatus('ACTIVE');
      const chip = nativeEl.querySelector('.chip--active');
      expect(chip).not.toBeNull();
    });

    it('should render ENDED with label "Encerrada"', () => {
      setStatus('ENDED');
      expect(nativeEl.querySelector('[data-status="ENDED"]')?.textContent?.trim()).toBe('Encerrada');
    });

    it('should render ERROR with label "Erro"', () => {
      setStatus('ERROR');
      expect(nativeEl.querySelector('[data-status="ERROR"]')?.textContent?.trim()).toBe('Erro');
    });
  });

  describe('NodeSubEstado variants', () => {
    it('should render PENDING with label "Pendente"', () => {
      setStatus('PENDING');
      expect(nativeEl.querySelector('[data-status="PENDING"]')?.textContent?.trim()).toBe('Pendente');
    });

    it('should render APPROVED_CAREGIVER with expected label', () => {
      setStatus('APPROVED_CAREGIVER');
      expect(nativeEl.querySelector('[data-status="APPROVED_CAREGIVER"]')?.textContent?.trim()).toBe(
        'Aprovado (cuidadora)',
      );
    });

    it('should render APPROVED_PATIENT with expected label', () => {
      setStatus('APPROVED_PATIENT');
      expect(nativeEl.querySelector('[data-status="APPROVED_PATIENT"]')?.textContent?.trim()).toBe(
        'Aprovado (paciente)',
      );
    });

    it('should render PUBLISHED with label "Publicado"', () => {
      setStatus('PUBLISHED');
      expect(nativeEl.querySelector('[data-status="PUBLISHED"]')?.textContent?.trim()).toBe('Publicado');
    });

    it('should render REJECTED with label "Rejeitado"', () => {
      setStatus('REJECTED');
      expect(nativeEl.querySelector('[data-status="REJECTED"]')?.textContent?.trim()).toBe('Rejeitado');
    });
  });

  it('should NOT contain any hardcoded hex color values in the template', () => {
    // The chip must use CSS custom properties, never inline color: #xxx
    const chip = nativeEl.querySelector('.chip') as HTMLElement | null;
    expect(chip?.style.color).toBeFalsy();
    expect(chip?.style.backgroundColor).toBeFalsy();
  });
});
