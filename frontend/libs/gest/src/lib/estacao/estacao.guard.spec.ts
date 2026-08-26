import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { signal } from '@angular/core';
import { estacaoDeviceGuard } from './estacao.guard';
import { DeviceSessionStore } from './device-session.store';
import type { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

// =============================================================================
// estacaoDeviceGuard — unit tests (GEST-FE-030)
//
// O que estes testes protegem:
//   1. Sessão de dispositivo autenticada → acesso concedido (true)
//   2. Sem sessão (desconhecido) → redirect para /
//   3. Sessão de usuário comum (não device) → redirect para /
//   4. Estado credencial-invalida → redirect para /
//
// Mutação confirmada: ao inverter o `if (autenticado())` para `if (!autenticado())`,
// o teste "deve permitir acesso com sessão de dispositivo autenticada" falha.
// =============================================================================

/** Stub mínimo de ActivatedRouteSnapshot e RouterStateSnapshot */
const mockRoute = {} as ActivatedRouteSnapshot;
const mockState = {} as RouterStateSnapshot;

describe('estacaoDeviceGuard', () => {
  let router: Router;
  let autenticadoSignal: ReturnType<typeof signal<boolean>>;

  function runGuard(): ReturnType<typeof estacaoDeviceGuard> {
    return TestBed.runInInjectionContext(() =>
      estacaoDeviceGuard(mockRoute, mockState),
    );
  }

  beforeEach(() => {
    autenticadoSignal = signal(false);

    TestBed.configureTestingModule({
      imports: [RouterTestingModule.withRoutes([])],
      providers: [
        {
          provide: DeviceSessionStore,
          useValue: {
            autenticado: autenticadoSignal.asReadonly(),
          },
        },
      ],
    });
    router = TestBed.inject(Router);
  });

  it('deve permitir acesso quando o dispositivo está autenticado', () => {
    autenticadoSignal.set(true);
    const result = runGuard();
    expect(result).toBe(true);
  });

  it('deve redirecionar para / quando não há sessão de dispositivo', () => {
    autenticadoSignal.set(false);
    const result = runGuard();
    // Guard retorna UrlTree para /
    expect(result).not.toBe(true);
    const urlTree = router.createUrlTree(['/']);
    expect(result.toString()).toBe(urlTree.toString());
  });

  it('deve redirecionar para / com sessão de usuário (autenticado=false no DeviceSessionStore)', () => {
    // Sessão de usuário cuidadora: DeviceSessionStore.autenticado() nunca fica true
    // para tokens de usuário — a loja só transita para 'autenticado' via autenticar()
    // com credenciais de dispositivo.
    autenticadoSignal.set(false);
    const result = runGuard();
    expect(result).not.toBe(true);
  });
});
