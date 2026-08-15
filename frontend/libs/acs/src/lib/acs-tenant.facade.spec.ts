// =============================================================================
// acs-tenant.facade.spec.ts — FE-ACS-020
//
// Tests: AcsTenantFacade initial state, load(), create(), retryProvisioning().
// Port is mocked with synchronous of() Observables.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AcsTenantFacade } from './acs-tenant.facade';
import { ACS_TENANT_PORT } from './api/acs-tenant.port';
import type { TenantDto } from './api/acs-tenant.port';

// ---------------------------------------------------------------------------
// Seed data
// ---------------------------------------------------------------------------

const SEED_TENANT: TenantDto = {
  id: 'tenant-familia-amaral',
  nome: 'Família Amaral',
  tipo: 'familia',
  provisioningStatus: 'active',
};

const PROVISIONING_TENANT: TenantDto = {
  ...SEED_TENANT,
  provisioningStatus: 'provisioning',
};

// ---------------------------------------------------------------------------
// Spec suite
// ---------------------------------------------------------------------------

describe('AcsTenantFacade — FE-ACS-020', () => {
  let facade: AcsTenantFacade;
  let tenantPort: { get: jest.Mock; create: jest.Mock; retryProvisioning: jest.Mock };

  beforeEach(() => {
    tenantPort = {
      get: jest.fn().mockReturnValue(of(SEED_TENANT)),
      create: jest.fn().mockReturnValue(
        of({ ...SEED_TENANT, id: 'tenant-new', nome: 'Nova Família' }),
      ),
      retryProvisioning: jest.fn().mockReturnValue(of(SEED_TENANT)),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: ACS_TENANT_PORT, useValue: tenantPort },
      ],
    });

    facade = TestBed.inject(AcsTenantFacade);
  });

  // ---------------------------------------------------------------------------
  // Initial state
  // ---------------------------------------------------------------------------
  describe('initial state', () => {
    it('tenant() is null', () => {
      expect(facade.tenant()).toBeNull();
    });

    it('loading() is false', () => {
      expect(facade.loading()).toBe(false);
    });

    it('error() is null', () => {
      expect(facade.error()).toBeNull();
    });

    it('provisioning() is false', () => {
      expect(facade.provisioning()).toBe(false);
    });
  });

  // ---------------------------------------------------------------------------
  // load(tenantId)
  // ---------------------------------------------------------------------------
  describe('load(tenantId)', () => {
    it('populates tenant() with mapped TenantVm', async () => {
      await facade.load('tenant-familia-amaral');
      const vm = facade.tenant();
      expect(vm).not.toBeNull();
      expect(vm!.id).toBe('tenant-familia-amaral');
      expect(vm!.nome).toBe('Família Amaral');
      expect(vm!.tipo).toBe('familia');
    });

    it('isActive=true when provisioningStatus=active', async () => {
      await facade.load('tenant-familia-amaral');
      expect(facade.tenant()!.isActive).toBe(true);
    });

    it('loading() is false after successful completion', async () => {
      await facade.load('tenant-familia-amaral');
      expect(facade.loading()).toBe(false);
    });

    it('provisioning()=true when provisioningStatus=provisioning', async () => {
      tenantPort.get.mockReturnValue(of(PROVISIONING_TENANT));
      await facade.load('tenant-familia-amaral');
      expect(facade.provisioning()).toBe(true);
    });

    it('provisioning()=false when provisioningStatus=active', async () => {
      await facade.load('tenant-familia-amaral');
      expect(facade.provisioning()).toBe(false);
    });

    it('sets error() when adapter throws with Error instance', async () => {
      tenantPort.get.mockReturnValue(throwError(() => new Error('Not found')));
      await facade.load('unknown-tenant');
      expect(facade.error()).toBe('Not found');
    });

    it('falls back to generic message when adapter throws non-Error', async () => {
      tenantPort.get.mockReturnValue(throwError(() => 'string-throw'));
      await facade.load('unknown-tenant');
      expect(facade.error()).toBe('Erro ao carregar unidade de cuidado');
    });

    it('loading() is false even on error (finally block)', async () => {
      tenantPort.get.mockReturnValue(throwError(() => new Error('fail')));
      await facade.load('unknown-tenant');
      expect(facade.loading()).toBe(false);
    });

    it('calls port.get() with the given tenantId', async () => {
      await facade.load('tenant-familia-amaral');
      expect(tenantPort.get).toHaveBeenCalledWith('tenant-familia-amaral');
    });
  });

  // ---------------------------------------------------------------------------
  // create(dto)
  // ---------------------------------------------------------------------------
  describe('create(dto)', () => {
    it('returns new TenantVm with id from adapter', async () => {
      const vm = await facade.create({ nome: 'Nova Família', tipo: 'familia' });
      expect(vm).not.toBeNull();
      expect(vm!.id).toBe('tenant-new');
      expect(vm!.nome).toBe('Nova Família');
    });

    it('sets tenant() signal with newly created TenantVm', async () => {
      await facade.create({ nome: 'Nova Família', tipo: 'familia' });
      expect(facade.tenant()?.id).toBe('tenant-new');
    });

    it('isActive=true after create() returns provisioningStatus=active', async () => {
      const vm = await facade.create({ nome: 'Nova Família', tipo: 'familia' });
      expect(vm!.isActive).toBe(true);
    });

    it('loading() is false after successful create', async () => {
      await facade.create({ nome: 'Nova Família', tipo: 'familia' });
      expect(facade.loading()).toBe(false);
    });

    it('provisioning()=true after create() returns provisioningStatus=provisioning', async () => {
      tenantPort.create.mockReturnValue(of({ ...SEED_TENANT, id: 'tenant-new', provisioningStatus: 'provisioning' }));
      await facade.create({ nome: 'Nova Família', tipo: 'familia' });
      expect(facade.provisioning()).toBe(true);
    });

    it('returns null and sets error() on adapter failure', async () => {
      tenantPort.create.mockReturnValue(throwError(() => new Error('Create failed')));
      const vm = await facade.create({ nome: 'Fail', tipo: 'clinica' });
      expect(vm).toBeNull();
      expect(facade.error()).toBe('Create failed');
    });

    it('loading() is false even on create error (finally block)', async () => {
      tenantPort.create.mockReturnValue(throwError(() => new Error('fail')));
      await facade.create({ nome: 'Fail', tipo: 'clinica' });
      expect(facade.loading()).toBe(false);
    });

    it('calls port.create() with the given dto', async () => {
      await facade.create({ nome: 'Família Teste', tipo: 'clinica' });
      expect(tenantPort.create).toHaveBeenCalledWith({ nome: 'Família Teste', tipo: 'clinica' });
    });
  });

  // ---------------------------------------------------------------------------
  // retryProvisioning(tenantId)
  // ---------------------------------------------------------------------------
  describe('retryProvisioning(tenantId)', () => {
    it('updates tenant() signal after retry succeeds', async () => {
      tenantPort.retryProvisioning.mockReturnValue(
        of({ ...SEED_TENANT, provisioningStatus: 'active' }),
      );
      await facade.retryProvisioning('tenant-familia-amaral');
      expect(facade.tenant()?.isActive).toBe(true);
    });

    it('loading() is false after retryProvisioning', async () => {
      await facade.retryProvisioning('tenant-familia-amaral');
      expect(facade.loading()).toBe(false);
    });

    it('sets error() when retryProvisioning adapter fails', async () => {
      tenantPort.retryProvisioning.mockReturnValue(
        throwError(() => new Error('Retry failed')),
      );
      await facade.retryProvisioning('tenant-familia-amaral');
      expect(facade.error()).toBe('Retry failed');
    });
  });
});
