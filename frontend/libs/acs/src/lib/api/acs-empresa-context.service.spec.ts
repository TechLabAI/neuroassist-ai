import { TestBed } from '@angular/core/testing';
import { AcsEmpresaContextService } from './acs-empresa-context.service';

// =============================================================================
// acs-empresa-context.service.spec.ts — INT-FE-ACS-003
// Verifies AcsEmpresaContextService as the ACS-local empresa-context store.
// =============================================================================

describe('AcsEmpresaContextService', () => {
  let service: AcsEmpresaContextService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [AcsEmpresaContextService] });
    service = TestBed.inject(AcsEmpresaContextService);
  });

  it('should start with null empresa id', () => {
    expect(service.empresaId()).toBeNull();
  });

  it('should set empresa id when setActiveEmpresaId is called', () => {
    service.setActiveEmpresaId('tenant-001');
    expect(service.empresaId()).toBe('tenant-001');
  });

  it('should update empresa id when called again', () => {
    service.setActiveEmpresaId('tenant-001');
    service.setActiveEmpresaId('tenant-002');
    expect(service.empresaId()).toBe('tenant-002');
  });

  it('should allow clearing the id by passing null', () => {
    service.setActiveEmpresaId('tenant-001');
    service.setActiveEmpresaId(null);
    expect(service.empresaId()).toBeNull();
  });
});
