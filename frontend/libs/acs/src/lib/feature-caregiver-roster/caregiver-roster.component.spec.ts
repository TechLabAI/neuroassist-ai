// =============================================================================
// caregiver-roster.component.spec.ts — FE-ACS-022
//
// Tests: loading state, error state, roster rendering, empty state,
// inactive-caregiver filtering via activeCaregivers computed,
// two-step deactivate flow (requestDeactivate → confirmDeactivate).
//
// Facade is mocked with Angular signals to precisely control template state.
// =============================================================================

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal, computed, Signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { CaregiverRosterComponent } from './caregiver-roster.component';
import { AcsCaregiverFacade } from '../acs-caregiver.facade';
import type { CuidadorVm } from '../acs.types';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeCuidadorVm(overrides: Partial<CuidadorVm> = {}): CuidadorVm {
  return {
    id: 'cuid-1',
    nome: 'Jamilly Amaral',
    email: 'jamilly@test.com',
    papel: 'cuidadora',
    status: 'active',
    isActive: true,
    papelLabel: 'Cuidadora',
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Shared mock signals (created once; reset in beforeEach)
// ---------------------------------------------------------------------------
const loadingSignal = signal(false);
const errorSignal = signal<string | null>(null);
const savingSignal = signal(false);
const caregiversSignal = signal<readonly CuidadorVm[]>([]);
const activeCaregivers: Signal<readonly CuidadorVm[]> = computed(
  () => caregiversSignal().filter((c) => c.isActive),
);

const mockFacade = {
  loading: loadingSignal.asReadonly(),
  error: errorSignal.asReadonly(),
  saving: savingSignal.asReadonly(),
  caregivers: caregiversSignal.asReadonly(),
  activeCaregivers,
  loadRoster: jest.fn().mockResolvedValue(undefined),
  deactivate: jest.fn().mockResolvedValue(undefined),
  // coverage helpers (not used by roster directly, but referenced by facade type)
  coverageFor: jest.fn().mockReturnValue({ pacienteId: '', coveredBy: [], isCovered: false }),
  linkIdFor: jest.fn().mockReturnValue(null),
};

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('CaregiverRosterComponent — FE-ACS-022', () => {
  let fixture: ComponentFixture<CaregiverRosterComponent>;

  beforeEach(async () => {
    // Reset all signals to clean state
    loadingSignal.set(false);
    errorSignal.set(null);
    savingSignal.set(false);
    caregiversSignal.set([]);
    jest.clearAllMocks();
    mockFacade.loadRoster.mockResolvedValue(undefined);
    mockFacade.deactivate.mockResolvedValue(undefined);

    await TestBed.configureTestingModule({
      imports: [CaregiverRosterComponent],
      providers: [
        provideRouter([]),
        { provide: AcsCaregiverFacade, useValue: mockFacade },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CaregiverRosterComponent);
    fixture.detectChanges(); // triggers ngOnInit → loadRoster()
  });

  // ---------------------------------------------------------------------------
  // Initialization
  // ---------------------------------------------------------------------------
  describe('ngOnInit', () => {
    it('calls facade.loadRoster() once on init', () => {
      expect(mockFacade.loadRoster).toHaveBeenCalledTimes(1);
    });
  });

  // ---------------------------------------------------------------------------
  // Loading state
  // ---------------------------------------------------------------------------
  describe('loading state', () => {
    it('renders [data-testid="roster-loading"] when facade.loading()=true', () => {
      loadingSignal.set(true);
      fixture.detectChanges();
      const el = fixture.nativeElement.querySelector('[data-testid="roster-loading"]');
      expect(el).not.toBeNull();
    });

    it('does NOT render loading when facade.loading()=false', () => {
      loadingSignal.set(false);
      fixture.detectChanges();
      const el = fixture.nativeElement.querySelector('[data-testid="roster-loading"]');
      expect(el).toBeNull();
    });

    it('loading indicator has role=status for a11y', () => {
      loadingSignal.set(true);
      fixture.detectChanges();
      const el = fixture.nativeElement.querySelector('[data-testid="roster-loading"]');
      expect(el?.getAttribute('role')).toBe('status');
    });
  });

  // ---------------------------------------------------------------------------
  // Error state
  // ---------------------------------------------------------------------------
  describe('error state', () => {
    it('renders [data-testid="roster-error"] when facade.error() is non-null', () => {
      errorSignal.set('Erro ao carregar cuidadores');
      fixture.detectChanges();
      const el = fixture.nativeElement.querySelector('[data-testid="roster-error"]');
      expect(el).not.toBeNull();
    });

    it('error banner contains the error message text', () => {
      errorSignal.set('Erro de rede');
      fixture.detectChanges();
      const el = fixture.nativeElement.querySelector('[data-testid="roster-error"]');
      expect(el?.textContent).toContain('Erro de rede');
    });

    it('does NOT render error banner when facade.error()=null', () => {
      errorSignal.set(null);
      fixture.detectChanges();
      const el = fixture.nativeElement.querySelector('[data-testid="roster-error"]');
      expect(el).toBeNull();
    });

    it('error banner contains retry button with aria-label', () => {
      errorSignal.set('fail');
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="roster-error"] button');
      expect(btn).not.toBeNull();
      expect(btn?.getAttribute('aria-label')).toContain('Tentar');
    });

    it('retry button calls loadRoster()', () => {
      errorSignal.set('fail');
      fixture.detectChanges();
      const btn = fixture.nativeElement.querySelector('[data-testid="roster-error"] button');
      btn.click();
      expect(mockFacade.loadRoster).toHaveBeenCalledTimes(2); // once in ngOnInit, once on retry
    });
  });

  // ---------------------------------------------------------------------------
  // Roster rendering
  // ---------------------------------------------------------------------------
  describe('roster rendering', () => {
    it('renders one [data-testid="roster-row"] per active caregiver', () => {
      caregiversSignal.set([
        makeCuidadorVm({ id: 'cuid-1' }),
        makeCuidadorVm({ id: 'cuid-2', nome: 'Second' }),
      ]);
      fixture.detectChanges();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="roster-row"]');
      expect(rows.length).toBe(2);
    });

    it('does NOT render inactive caregivers in roster rows', () => {
      caregiversSignal.set([
        makeCuidadorVm({ id: 'cuid-active' }),
        makeCuidadorVm({ id: 'cuid-inactive', status: 'inactive', isActive: false }),
      ]);
      fixture.detectChanges();
      const rows = fixture.nativeElement.querySelectorAll('[data-testid="roster-row"]');
      expect(rows.length).toBe(1);
    });

    it('renders [data-testid="roster-empty"] when no active caregivers', () => {
      caregiversSignal.set([]);
      fixture.detectChanges();
      const el = fixture.nativeElement.querySelector('[data-testid="roster-empty"]');
      expect(el).not.toBeNull();
    });

    it('does NOT render roster-empty when caregivers exist', () => {
      caregiversSignal.set([makeCuidadorVm()]);
      fixture.detectChanges();
      const el = fixture.nativeElement.querySelector('[data-testid="roster-empty"]');
      expect(el).toBeNull();
    });
  });

  // ---------------------------------------------------------------------------
  // Two-step deactivate flow
  // ---------------------------------------------------------------------------
  describe('deactivate flow (two-step inline confirmation)', () => {
    beforeEach(() => {
      caregiversSignal.set([makeCuidadorVm({ id: 'cuid-1' })]);
      fixture.detectChanges();
    });

    it('shows [data-testid="deactivate-btn"] initially', () => {
      const btn = fixture.nativeElement.querySelector('[data-testid="deactivate-btn"]');
      expect(btn).not.toBeNull();
    });

    it('clicking deactivate-btn shows confirm and cancel buttons', () => {
      const deactivateBtn = fixture.nativeElement.querySelector('[data-testid="deactivate-btn"]');
      deactivateBtn.click();
      fixture.detectChanges();

      expect(
        fixture.nativeElement.querySelector('[data-testid="confirm-deactivate-btn"]'),
      ).not.toBeNull();
      expect(
        fixture.nativeElement.querySelector('[data-testid="cancel-deactivate-btn"]'),
      ).not.toBeNull();
    });

    it('clicking cancel-deactivate-btn hides confirmation and shows deactivate-btn again', () => {
      // Open confirmation
      fixture.nativeElement.querySelector('[data-testid="deactivate-btn"]').click();
      fixture.detectChanges();

      // Cancel
      fixture.nativeElement.querySelector('[data-testid="cancel-deactivate-btn"]').click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="deactivate-btn"]')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="confirm-deactivate-btn"]')).toBeNull();
    });

    it('clicking confirm-deactivate-btn calls facade.deactivate() with the caregiver id', async () => {
      // Open confirmation
      fixture.nativeElement.querySelector('[data-testid="deactivate-btn"]').click();
      fixture.detectChanges();

      // Confirm
      fixture.nativeElement.querySelector('[data-testid="confirm-deactivate-btn"]').click();
      fixture.detectChanges();

      await fixture.whenStable();
      expect(mockFacade.deactivate).toHaveBeenCalledWith('cuid-1');
    });

    it('after confirm-deactivate, pendingDeactivateId resets (confirm btn hidden)', async () => {
      fixture.nativeElement.querySelector('[data-testid="deactivate-btn"]').click();
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="confirm-deactivate-btn"]').click();
      fixture.detectChanges();

      await fixture.whenStable();
      fixture.detectChanges();

      // After deactivate resolves, pendingDeactivateId is set to null
      expect(
        fixture.nativeElement.querySelector('[data-testid="confirm-deactivate-btn"]'),
      ).toBeNull();
    });
  });
});
