import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { pacAuthGuard } from './pac-auth.guard';

// =============================================================================
// pacAuthGuard — unit tests (P6 / PAC-FE-013 scope)
// Guard currently returns true (dev default).
// TODO[PAC-FE-013]: expandir quando AuthSessionService integrado.
// =============================================================================

const mockRoute = {} as ActivatedRouteSnapshot;
const mockState = {} as RouterStateSnapshot;

describe('pacAuthGuard', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('deve retornar true (dev default — integração com AuthSessionService pendente)', () => {
    const result = TestBed.runInInjectionContext(() =>
      pacAuthGuard(mockRoute, mockState),
    );
    expect(result).toBe(true);
  });
});
