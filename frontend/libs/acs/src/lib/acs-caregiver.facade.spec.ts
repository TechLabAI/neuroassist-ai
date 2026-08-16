// =============================================================================
// acs-caregiver.facade.spec.ts — FE-ACS-020
//
// Tests: AcsCaregiverFacade signals lifecycle, loadRoster, invite, deactivate,
// revokeLink (ADR-005 409 block), linkIdFor (_linkMap), coverageFor, activeCaregivers.
//
// Ports are mocked with synchronous of() Observables to avoid timer complexity.
// TestBed re-creates the facade for each test via destroyAfterEach behavior.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AcsCaregiverFacade } from './acs-caregiver.facade';
import { ACS_CUIDADOR_PORT } from './api/acs-cuidador.port';
import type { CuidadorDto } from './api/acs-cuidador.port';
import { ACS_LINK_PORT } from './api/acs-link.port';
import type { CaregiverLinkDto } from './api/acs-link.port';

// ---------------------------------------------------------------------------
// Seed data
// ---------------------------------------------------------------------------

const SEED_CUIDADOR: CuidadorDto = {
  id: 'cuid-test',
  tenantId: 'tenant-test',
  nome: 'Jamilly Amaral',
  email: 'jamilly@test.com',
  papel: 'cuidadora',
  status: 'active',
};

const SEED_LINK: CaregiverLinkDto = {
  id: 'link-test-001',
  cuidadorId: 'cuid-test',
  pacienteId: 'pac-marcio',
  active: true,
  createdAt: '2026-01-01T00:00:00Z',
};

const INACTIVE_CUIDADOR: CuidadorDto = {
  ...SEED_CUIDADOR,
  id: 'cuid-inactive',
  status: 'inactive',
};

// ---------------------------------------------------------------------------
// Spec suite
// ---------------------------------------------------------------------------

describe('AcsCaregiverFacade — FE-ACS-020', () => {
  let facade: AcsCaregiverFacade;
  let cuidadorPort: {
    list: jest.Mock;
    get: jest.Mock;
    create: jest.Mock;
    deactivate: jest.Mock;
  };
  let linkPort: {
    listForCaregiver: jest.Mock;
    link: jest.Mock;
    revoke: jest.Mock;
  };

  beforeEach(() => {
    cuidadorPort = {
      list: jest.fn().mockReturnValue(of([SEED_CUIDADOR])),
      get: jest.fn().mockReturnValue(of(SEED_CUIDADOR)),
      create: jest.fn().mockReturnValue(
        of({ ...SEED_CUIDADOR, id: 'cuid-new', nome: 'New Caregiver', email: 'new@test.com' }),
      ),
      deactivate: jest.fn().mockReturnValue(of({ ...SEED_CUIDADOR, status: 'inactive' })),
    };

    linkPort = {
      listForCaregiver: jest.fn().mockReturnValue(of([SEED_LINK])),
      link: jest.fn().mockReturnValue(of(SEED_LINK)),
      revoke: jest.fn().mockReturnValue(of(undefined)),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: ACS_CUIDADOR_PORT, useValue: cuidadorPort },
        { provide: ACS_LINK_PORT, useValue: linkPort },
      ],
    });

    facade = TestBed.inject(AcsCaregiverFacade);
  });

  // ---------------------------------------------------------------------------
  // Initial state
  // ---------------------------------------------------------------------------
  describe('initial state', () => {
    it('caregivers() is empty array', () => {
      expect(facade.caregivers()).toHaveLength(0);
    });

    it('loading() is false', () => {
      expect(facade.loading()).toBe(false);
    });

    it('error() is null', () => {
      expect(facade.error()).toBeNull();
    });

    it('saving() is false', () => {
      expect(facade.saving()).toBe(false);
    });

    it('activeCaregivers() is empty computed', () => {
      expect(facade.activeCaregivers()).toHaveLength(0);
    });

    it('linkIdFor() returns null before loadRoster()', () => {
      expect(facade.linkIdFor('cuid-test', 'pac-marcio')).toBeNull();
    });

    it('coverageFor() returns isCovered=false before loadRoster()', () => {
      const vm = facade.coverageFor('pac-marcio');
      expect(vm.isCovered).toBe(false);
      expect(vm.coveredBy).toHaveLength(0);
    });
  });

  // ---------------------------------------------------------------------------
  // loadRoster()
  // ---------------------------------------------------------------------------
  describe('loadRoster()', () => {
    it('populates caregivers() with Vm objects mapped from DTOs', async () => {
      await facade.loadRoster();
      expect(facade.caregivers()).toHaveLength(1);
      expect(facade.caregivers()[0].id).toBe('cuid-test');
      expect(facade.caregivers()[0].nome).toBe('Jamilly Amaral');
    });

    it('sets isActive=true for status=active cuidador', async () => {
      await facade.loadRoster();
      expect(facade.caregivers()[0].isActive).toBe(true);
    });

    it('sets papelLabel="Cuidadora" for papel=cuidadora', async () => {
      await facade.loadRoster();
      expect(facade.caregivers()[0].papelLabel).toBe('Cuidadora');
    });

    it('loading() is false after successful completion', async () => {
      await facade.loadRoster();
      expect(facade.loading()).toBe(false);
    });

    it('clears error() after a successful load that follows a failed one', async () => {
      cuidadorPort.list.mockReturnValueOnce(throwError(() => new Error('prev error')));
      await facade.loadRoster();
      cuidadorPort.list.mockReturnValue(of([SEED_CUIDADOR]));
      await facade.loadRoster();
      expect(facade.error()).toBeNull();
    });

    it('sets error() when adapter throws with Error instance', async () => {
      cuidadorPort.list.mockReturnValue(throwError(() => new Error('Network error')));
      await facade.loadRoster();
      expect(facade.error()).toBe('Network error');
    });

    it('falls back to generic message when adapter throws non-Error', async () => {
      cuidadorPort.list.mockReturnValue(throwError(() => 'string-error'));
      await facade.loadRoster();
      expect(facade.error()).toBe('Erro ao carregar cuidadores');
    });

    it('loading() is false even on error (finally block)', async () => {
      cuidadorPort.list.mockReturnValue(throwError(() => new Error('fail')));
      await facade.loadRoster();
      expect(facade.loading()).toBe(false);
    });

    it('activeCaregivers() only includes active caregivers', async () => {
      cuidadorPort.list.mockReturnValue(of([SEED_CUIDADOR, INACTIVE_CUIDADOR]));
      linkPort.listForCaregiver.mockReturnValue(of([]));
      await facade.loadRoster();
      expect(facade.activeCaregivers()).toHaveLength(1);
      expect(facade.activeCaregivers()[0].id).toBe('cuid-test');
    });
  });

  // ---------------------------------------------------------------------------
  // linkIdFor() — _linkMap (P4: new capability)
  // ---------------------------------------------------------------------------
  describe('linkIdFor()', () => {
    it('returns correct linkId for a known cuidador-patient pair after loadRoster()', async () => {
      await facade.loadRoster();
      expect(facade.linkIdFor('cuid-test', 'pac-marcio')).toBe('link-test-001');
    });

    it('returns null for a pair where link is inactive', async () => {
      linkPort.listForCaregiver.mockReturnValue(
        of([{ ...SEED_LINK, active: false }]),
      );
      await facade.loadRoster();
      expect(facade.linkIdFor('cuid-test', 'pac-marcio')).toBeNull();
    });

    it('returns null for an unknown cuidador-patient pair', async () => {
      await facade.loadRoster();
      expect(facade.linkIdFor('cuid-test', 'pac-unknown')).toBeNull();
    });
  });

  // ---------------------------------------------------------------------------
  // coverageFor()
  // ---------------------------------------------------------------------------
  describe('coverageFor()', () => {
    it('returns isCovered=true and correct coveredBy after loadRoster()', async () => {
      await facade.loadRoster();
      const vm = facade.coverageFor('pac-marcio');
      expect(vm.isCovered).toBe(true);
      expect(vm.coveredBy).toContain('cuid-test');
      expect(vm.pacienteId).toBe('pac-marcio');
    });

    it('returns isCovered=false for a patient with no active link', async () => {
      await facade.loadRoster();
      const vm = facade.coverageFor('pac-unknown');
      expect(vm.isCovered).toBe(false);
      expect(vm.coveredBy).toHaveLength(0);
    });

    it('returns isCovered=false when listForCaregiver returns empty links', async () => {
      linkPort.listForCaregiver.mockReturnValue(of([]));
      await facade.loadRoster();
      const vm = facade.coverageFor('pac-marcio');
      expect(vm.isCovered).toBe(false);
    });
  });

  // ---------------------------------------------------------------------------
  // invite()
  // ---------------------------------------------------------------------------
  describe('invite()', () => {
    it('grows caregivers() by 1', async () => {
      await facade.loadRoster();
      const before = facade.caregivers().length;
      await facade.invite({ nome: 'New', email: 'new@test.com', papel: 'cuidadora' });
      expect(facade.caregivers()).toHaveLength(before + 1);
    });

    it('returns the new CuidadorVm with the id from the adapter', async () => {
      const vm = await facade.invite({ nome: 'New', email: 'new@test.com', papel: 'cuidadora' });
      expect(vm).not.toBeNull();
      expect(vm!.id).toBe('cuid-new');
      expect(vm!.nome).toBe('New Caregiver');
      expect(vm!.isActive).toBe(true);
    });

    it('saving() is false after successful completion', async () => {
      await facade.invite({ nome: 'New', email: 'new@test.com', papel: 'cuidadora' });
      expect(facade.saving()).toBe(false);
    });

    it('returns null and sets error() on adapter failure', async () => {
      cuidadorPort.create.mockReturnValue(throwError(() => new Error('Invite failed')));
      const vm = await facade.invite({ nome: 'New', email: 'new@test.com', papel: 'cuidadora' });
      expect(vm).toBeNull();
      expect(facade.error()).toBe('Invite failed');
    });

    it('saving() is false even on invite error (finally block)', async () => {
      cuidadorPort.create.mockReturnValue(throwError(() => new Error('fail')));
      await facade.invite({ nome: 'New', email: 'new@test.com', papel: 'cuidadora' });
      expect(facade.saving()).toBe(false);
    });
  });

  // ---------------------------------------------------------------------------
  // deactivate()
  // ---------------------------------------------------------------------------
  describe('deactivate()', () => {
    // Helper: load roster with no links so cuid-test has no coverage → pre-flight passes.
    async function loadRosterWithoutCoverage(): Promise<void> {
      linkPort.listForCaregiver.mockReturnValue(of([]));
      await facade.loadRoster();
    }

    it('marks the caregiver as inactive (isActive=false, status=inactive)', async () => {
      // cuid-test must NOT be last-covering → load roster with empty links.
      await loadRosterWithoutCoverage();
      await facade.deactivate('cuid-test');
      const found = facade.caregivers().find((c) => c.id === 'cuid-test');
      expect(found?.isActive).toBe(false);
      expect(found?.status).toBe('inactive');
    });

    it('removes the deactivated caregiver from activeCaregivers()', async () => {
      await loadRosterWithoutCoverage();
      await facade.deactivate('cuid-test');
      expect(facade.activeCaregivers()).toHaveLength(0);
    });

    it('saving() is false after successful deactivation', async () => {
      await loadRosterWithoutCoverage();
      await facade.deactivate('cuid-test');
      expect(facade.saving()).toBe(false);
    });

    it('sets error() on adapter failure', async () => {
      cuidadorPort.deactivate.mockReturnValue(throwError(() => new Error('Deactivate failed')));
      // Load without links so pre-flight does not block; adapter call will throw.
      await loadRosterWithoutCoverage();
      await facade.deactivate('cuid-test');
      expect(facade.error()).toBe('Deactivate failed');
    });

    // ADR-005 pre-flight: P8 blocking modal
    it('ADR-005: sets coverageModal() when cuid is the sole coverage for a patient', async () => {
      // Default mock: listForCaregiver returns SEED_LINK (cuid-test → pac-marcio, sole coverage).
      await facade.loadRoster();
      // coverageMap: pac-marcio → ['cuid-test'] — last-covering.
      await facade.deactivate('cuid-test');
      expect(facade.coverageModal()).not.toBeNull();
      expect(facade.coverageModal()?.type).toBe('deactivate');
    });

    it('ADR-005: does NOT call adapter when pre-flight blocks deactivation', async () => {
      await facade.loadRoster();
      await facade.deactivate('cuid-test');
      expect(cuidadorPort.deactivate).not.toHaveBeenCalled();
    });

    it('ADR-005: does NOT block when coverage map is empty (no patients assigned)', async () => {
      await loadRosterWithoutCoverage(); // no links → empty coverage map
      await facade.deactivate('cuid-test');
      expect(facade.coverageModal()).toBeNull();
      expect(cuidadorPort.deactivate).toHaveBeenCalled();
    });

    it('dismissCoverageModal() clears the modal signal', async () => {
      await facade.loadRoster();
      await facade.deactivate('cuid-test');
      expect(facade.coverageModal()).not.toBeNull();
      facade.dismissCoverageModal();
      expect(facade.coverageModal()).toBeNull();
    });
  });

  // ---------------------------------------------------------------------------
  // revokeLink() — ADR-005 last-covering-block
  // ---------------------------------------------------------------------------
  describe('revokeLink()', () => {
    it('on success: saving()=false and error()=null', async () => {
      await facade.revokeLink('cuid-test', 'link-test-001', 'pac-marcio');
      expect(facade.saving()).toBe(false);
      expect(facade.error()).toBeNull();
    });

    it('on 409 (last-covering-block): sets error() containing ADR-005 reference', async () => {
      const err409 = Object.assign(new Error('Conflict'), { status: 409 });
      linkPort.revoke.mockReturnValue(throwError(() => err409));
      await facade.revokeLink('cuid-test', 'link-test-001', 'pac-marcio');
      expect(facade.error()).toContain('ADR-005');
    });

    it('on 409: the error message mentions that it is the last active link', async () => {
      const err409 = Object.assign(new Error('Conflict'), { status: 409 });
      linkPort.revoke.mockReturnValue(throwError(() => err409));
      await facade.revokeLink('cuid-test', 'link-test-001', 'pac-marcio');
      expect(facade.error()).toContain('último vínculo');
    });

    it('on 409: saving()=false after error (finally block)', async () => {
      const err409 = Object.assign(new Error('Conflict'), { status: 409 });
      linkPort.revoke.mockReturnValue(throwError(() => err409));
      await facade.revokeLink('cuid-test', 'link-test-001', 'pac-marcio');
      expect(facade.saving()).toBe(false);
    });

    it('on generic error: sets descriptive error message from Error.message', async () => {
      linkPort.revoke.mockReturnValue(throwError(() => new Error('Server error 500')));
      await facade.revokeLink('cuid-test', 'link-test-001', 'pac-marcio');
      expect(facade.error()).toBe('Server error 500');
    });

    it('on generic error: falls back to generic message for non-Error throws', async () => {
      linkPort.revoke.mockReturnValue(throwError(() => 'plain-string-error'));
      await facade.revokeLink('cuid-test', 'link-test-001', 'pac-marcio');
      expect(facade.error()).toBe('Erro ao revogar vínculo');
    });

    // ADR-005 / P8: 409 also surfaces the blocking modal signal.
    it('on 409: sets coverageModal() to a non-null deactivate payload (ADR-005)', async () => {
      const err409 = Object.assign(new Error('Conflict'), { status: 409 });
      linkPort.revoke.mockReturnValue(throwError(() => err409));
      await facade.revokeLink('cuid-test', 'link-test-001', 'pac-marcio');
      expect(facade.coverageModal()).not.toBeNull();
      expect(facade.coverageModal()?.type).toBe('deactivate');
    });
  });
});
