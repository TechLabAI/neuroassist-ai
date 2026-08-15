import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { pacTenantPatientGuard } from './pac-tenant-patient.guard';
import { PacPacienteFacade } from '../pac-paciente.facade';
import { PAC_PACIENTE_PORT } from '../api/paciente.port';

// =============================================================================
// pacTenantPatientGuard — unit tests (P6 / PAC-FE-017)
// Guard verifica presença de Paciente registrado para o tenant.
// Async guard retorna Observable<UrlTree | true> via toObservable().
// Cenários:
//   - hasPatient() true, loading=false → retorna true imediato (navegação permitida)
//   - hasPatient() false, loading=false → retorna Observable(UrlTree('/onboarding'))
//   - loading=true → retorna Observable que espera loading ficar false
// =============================================================================

const mockRoute = {} as ActivatedRouteSnapshot;
const mockState = {} as RouterStateSnapshot;

describe('pacTenantPatientGuard', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  describe('quando hasPatient() é true e loading é false (fast path)', () => {
    beforeEach(() => {
      // Mock facade com hasPatient retornando true (paciente registrado)
      // e loading = false (já carregado)
      const mockFacade = {
        loadCurrent: jest.fn(),
        hasPatient: jest.fn().mockReturnValue(true),
        patient: signal(null),
        saveState: signal('idle' as const),
        loading: signal(false),
        error: signal(null),
      };

      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          { provide: PacPacienteFacade, useValue: mockFacade },
          {
            provide: PAC_PACIENTE_PORT,
            useValue: { list: jest.fn(), get: jest.fn(), create: jest.fn(), update: jest.fn() },
          },
        ],
      });
    });

    it('deve retornar true (navegação permitida)', () => {
      const result = TestBed.runInInjectionContext(() =>
        pacTenantPatientGuard(mockRoute, mockState),
      );
      expect(result).toBe(true);
    });
  });

  describe('quando hasPatient() é false e loading é false', () => {
    beforeEach(() => {
      // Mock facade com hasPatient retornando false (sem paciente registrado)
      // e loading = false (já tentou carregar)
      const mockFacade = {
        loadCurrent: jest.fn(),
        hasPatient: jest.fn().mockReturnValue(false),
        patient: signal(null),
        saveState: signal('idle' as const),
        loading: signal(false),
        error: signal(null),
      };

      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          { provide: PacPacienteFacade, useValue: mockFacade },
          {
            provide: PAC_PACIENTE_PORT,
            useValue: { list: jest.fn(), get: jest.fn(), create: jest.fn(), update: jest.fn() },
          },
        ],
      });
    });

    it('deve retornar Observable<UrlTree> apontando para /onboarding', async () => {
      const result = TestBed.runInInjectionContext(() =>
        pacTenantPatientGuard(mockRoute, mockState),
      );

      // O resultado é um Observable quando loading precisa ser aguardado
      const resolved = await firstValueFrom(result as any);
      expect(resolved).toBeInstanceOf(UrlTree);
      expect((resolved as UrlTree).toString()).toContain('onboarding');
    });
  });

  describe('comportamento de loadCurrent()', () => {
    it('deve chamar facade.loadCurrent() quando loading=false (triggeração)', () => {
      const loadCurrentSpy = jest.fn();
      const mockFacade = {
        loadCurrent: loadCurrentSpy,
        hasPatient: jest.fn().mockReturnValue(false),
        patient: signal(null),
        saveState: signal('idle' as const),
        loading: signal(false), // loading=false → guard chama loadCurrent
        error: signal(null),
      };

      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          { provide: PacPacienteFacade, useValue: mockFacade },
          {
            provide: PAC_PACIENTE_PORT,
            useValue: { list: jest.fn(), get: jest.fn(), create: jest.fn(), update: jest.fn() },
          },
        ],
      });

      TestBed.runInInjectionContext(() =>
        pacTenantPatientGuard(mockRoute, mockState),
      );

      // O guard deve chamar loadCurrent() quando loading=false
      expect(loadCurrentSpy).toHaveBeenCalledTimes(1);
    });
  });
});
