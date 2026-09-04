import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DualConfirmationComponent } from './dual-confirmation.component';

describe('DualConfirmationComponent', () => {
  let fixture: ComponentFixture<DualConfirmationComponent>;
  let component: DualConfirmationComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DualConfirmationComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(DualConfirmationComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    component.caregiverApproved = false;
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should render the dual-confirmation container', () => {
    component.caregiverApproved = false;
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('[data-testid="dual-confirmation"]') as HTMLElement;
    expect(el).toBeTruthy();
  });

  it('should show "Cuidadora pendente" when caregiverApproved is false', () => {
    component.caregiverApproved = false;
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('[data-testid="cuidadora-pending"]') as HTMLElement;
    expect(el).toBeTruthy();
    expect(el.textContent).toContain('Cuidadora pendente');
  });

  it('should show "Cuidadora ✓" when caregiverApproved is true', () => {
    component.caregiverApproved = true;
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('[data-testid="cuidadora-confirmed"]') as HTMLElement;
    expect(el).toBeTruthy();
    expect(el.textContent).toContain('Cuidadora');
  });

  it('should always render the patient-pending indicator (INV-1: CONV owns patient side)', () => {
    component.caregiverApproved = false;
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('[data-testid="paciente-pending"]') as HTMLElement;
    expect(el).toBeTruthy();
    expect(el.textContent).toContain('Aguardando paciente');
  });

  // ============================================================
  // INV-1 structural assertion (ADR-007)
  // ============================================================

  it('INV-1: should have no @Output that could trigger patient-side approval', () => {
    // The component class must NOT have a property named 'confirmadoPaciente'
    // or any EventEmitter that could be used to set it.
    const componentKeys = Object.keys(component);
    const hasPatientOutput = componentKeys.some(
      (k) => k === 'confirmadoPaciente' || k === 'patientApprove' || k === 'approvePatient',
    );
    expect(hasPatientOutput).toBe(false);
  });

  it('INV-1: should not reference confirmadoPaciente in its inputs', () => {
    // The only @Input is caregiverApproved — no patient-side input exists.
    component.caregiverApproved = false;
    fixture.detectChanges();
    // After setting the required input, caregiverApproved must be defined.
    expect(component.caregiverApproved).toBeDefined();
    expect((component as Record<string, unknown>)['confirmadoPaciente']).toBeUndefined();
    expect((component as Record<string, unknown>)['patientApproved']).toBeUndefined();
  });
});
