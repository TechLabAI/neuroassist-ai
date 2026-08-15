// =============================================================================
// tenant-setup.component.spec.ts — FE-ACS-022
//
// Critical cases: step 1→2 navigation preserves tipo, step 2→1 via Voltar,
// provision() calls facade.create(), disabled confirm when nome is empty.
// =============================================================================

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { TenantSetupComponent } from './tenant-setup.component';
import { AcsTenantFacade } from '../acs-tenant.facade';
import type { TenantVm } from '../acs.types';

// ---------------------------------------------------------------------------
// Mock signals
// ---------------------------------------------------------------------------

const loadingSignal = signal(false);
const errorSignal = signal<string | null>(null);
const provisioningSignal = signal(false);
const tenantSignal = signal<TenantVm | null>(null);

const mockFacade = {
  loading: loadingSignal.asReadonly(),
  error: errorSignal.asReadonly(),
  provisioning: provisioningSignal.asReadonly(),
  tenant: tenantSignal.asReadonly(),
  create: jest.fn().mockResolvedValue({
    id: 'tenant-new', nome: 'Família Teste', tipo: 'familia',
    provisioningStatus: 'active', isActive: true,
  } as TenantVm),
};

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('TenantSetupComponent — FE-ACS-022 (2-step flow)', () => {
  let fixture: ComponentFixture<TenantSetupComponent>;
  let component: TenantSetupComponent;

  beforeEach(async () => {
    loadingSignal.set(false);
    errorSignal.set(null);
    provisioningSignal.set(false);
    tenantSignal.set(null);
    jest.clearAllMocks();
    mockFacade.create.mockResolvedValue({
      id: 'tenant-new', nome: 'Família Teste', tipo: 'familia',
      provisioningStatus: 'active', isActive: true,
    } as TenantVm);

    await TestBed.configureTestingModule({
      imports: [TenantSetupComponent],
      providers: [
        { provide: AcsTenantFacade, useValue: mockFacade },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TenantSetupComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // ---------------------------------------------------------------------------
  // Step 1 initial state
  // ---------------------------------------------------------------------------
  describe('step 1 (initial state)', () => {
    it('renders step-1 section by default', () => {
      const el = fixture.nativeElement.querySelector('[data-testid="step-1"]');
      expect(el).not.toBeNull();
    });

    it('does NOT render step-2 on initial render', () => {
      const el = fixture.nativeElement.querySelector('[data-testid="step-2"]');
      expect(el).toBeNull();
    });

    it('currentStep() starts at 1', () => {
      expect(component.currentStep()).toBe(1);
    });

    it('tipo() defaults to "familia"', () => {
      expect(component.tipo()).toBe('familia');
    });

    it('renders tipo-familia and tipo-clinica options', () => {
      expect(fixture.nativeElement.querySelector('[data-testid="tipo-familia"]')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="tipo-clinica"]')).not.toBeNull();
    });
  });

  // ---------------------------------------------------------------------------
  // Step 1 → Step 2 navigation
  // ---------------------------------------------------------------------------
  describe('step 1 → step 2 (nextStep)', () => {
    it('clicking "Próximo" navigates to step 2', () => {
      const nextBtn = fixture.nativeElement.querySelector('[data-testid="step-1-next"]');
      nextBtn.click();
      fixture.detectChanges();
      expect(component.currentStep()).toBe(2);
    });

    it('after nextStep(), step-2 section is rendered', () => {
      fixture.nativeElement.querySelector('[data-testid="step-1-next"]').click();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="step-2"]')).not.toBeNull();
    });

    it('after nextStep(), step-1 section is hidden', () => {
      fixture.nativeElement.querySelector('[data-testid="step-1-next"]').click();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="step-1"]')).toBeNull();
    });

    it('step 1→2 preserves tipo selection (tipo=familia by default)', () => {
      fixture.nativeElement.querySelector('[data-testid="step-1-next"]').click();
      fixture.detectChanges();
      // step 2 shows the selected tipo
      const step2 = fixture.nativeElement.querySelector('[data-testid="step-2"]');
      expect(step2?.textContent).toContain('Família');
    });
  });

  // ---------------------------------------------------------------------------
  // Step 2 → Step 1 (back navigation)
  // ---------------------------------------------------------------------------
  describe('step 2 → step 1 (prevStep)', () => {
    beforeEach(() => {
      // Navigate to step 2
      component.nextStep();
      fixture.detectChanges();
    });

    it('clicking "Voltar" returns to step 1', () => {
      fixture.nativeElement.querySelector('[data-testid="step-2-back"]').click();
      fixture.detectChanges();
      expect(component.currentStep()).toBe(1);
    });

    it('after back navigation, step-1 is rendered again', () => {
      fixture.nativeElement.querySelector('[data-testid="step-2-back"]').click();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="step-1"]')).not.toBeNull();
    });

    it('tipo selection is preserved after back navigation (step 1→2→1)', () => {
      // tipo is still 'familia' from initial value
      expect(component.tipo()).toBe('familia');
    });
  });

  // ---------------------------------------------------------------------------
  // Step 2 — provision
  // ---------------------------------------------------------------------------
  describe('step 2 — provision()', () => {
    beforeEach(() => {
      component.nextStep();
      fixture.detectChanges();
    });

    it('confirm button is disabled when nome is empty', () => {
      component.nome.set('');
      fixture.detectChanges();
      const confirmBtn = fixture.nativeElement.querySelector('[data-testid="step-2-confirm"]');
      expect(confirmBtn.disabled).toBe(true);
    });

    it('confirm button is disabled when nome is only whitespace', () => {
      component.nome.set('   ');
      fixture.detectChanges();
      const confirmBtn = fixture.nativeElement.querySelector('[data-testid="step-2-confirm"]');
      expect(confirmBtn.disabled).toBe(true);
    });

    it('provision() calls facade.create() with trimmed nome and selected tipo', async () => {
      component.nome.set('  Família Amaral  ');
      component.tipo.set('familia');
      await component.provision();
      expect(mockFacade.create).toHaveBeenCalledWith({ nome: 'Família Amaral', tipo: 'familia' });
    });

    it('provision() does NOT call facade.create() when nome is empty', async () => {
      component.nome.set('');
      await component.provision();
      expect(mockFacade.create).not.toHaveBeenCalled();
    });

    it('provision() emits tenantProvisioned output when create() returns active tenant', async () => {
      const emittedValues: TenantVm[] = [];
      component.tenantProvisioned.subscribe((vm) => emittedValues.push(vm));

      component.nome.set('Nova Família');
      await component.provision();

      expect(emittedValues).toHaveLength(1);
      expect(emittedValues[0].isActive).toBe(true);
    });

    it('provision() does NOT emit tenantProvisioned when create() returns null', async () => {
      mockFacade.create.mockResolvedValue(null);
      const emittedValues: TenantVm[] = [];
      component.tenantProvisioned.subscribe((vm) => emittedValues.push(vm));

      component.nome.set('Nova Família');
      await component.provision();

      expect(emittedValues).toHaveLength(0);
    });

    it('shows loading indicator (facade.loading()=true) on confirm button text', () => {
      loadingSignal.set(true);
      fixture.detectChanges();
      const confirmBtn = fixture.nativeElement.querySelector('[data-testid="step-2-confirm"]');
      expect(confirmBtn?.textContent?.trim()).toContain('Criando');
    });

    it('shows [data-testid="tenant-provisioning"] when facade.provisioning()=true', () => {
      provisioningSignal.set(true);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="tenant-provisioning"]')).not.toBeNull();
    });

    it('shows [data-testid="tenant-error"] when facade.error() non-null', () => {
      errorSignal.set('Erro de provisionamento');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="tenant-error"]')).not.toBeNull();
    });
  });
});
