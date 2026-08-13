import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { pacCommandWriteGuard } from './pac-command-write.guard';

// =============================================================================
// pacCommandWriteGuard — unit tests (P6 / PAC-FE-018)
// Guard currently returns true (dev default; BE is the enforcement safety net).
// TODO[PAC-FE-018]: expandir quando AccessDecisionService integrado.
// =============================================================================

const mockRoute = {} as ActivatedRouteSnapshot;
const mockState = {} as RouterStateSnapshot;

describe('pacCommandWriteGuard', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('deve retornar true (dev default — integração com AccessDecisionService pendente)', () => {
    const result = TestBed.runInInjectionContext(() =>
      pacCommandWriteGuard(mockRoute, mockState),
    );
    expect(result).toBe(true);
  });

  it('deve ser uma função (CanActivateFn)', () => {
    expect(typeof pacCommandWriteGuard).toBe('function');
  });
});
