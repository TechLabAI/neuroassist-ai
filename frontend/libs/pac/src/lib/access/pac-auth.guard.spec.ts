import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { provideRouter } from '@angular/router';
import { pacAuthGuard } from './pac-auth.guard';
import { PAC_AUTH_SESSION } from './pac-access-tokens';

// =============================================================================
// pacAuthGuard — o guard agora GUARDA.
//
// O teste anterior afirmava `expect(result).toBe(true)` e nada mais. Ele passava
// porque o guard devolvia `true` incondicionalmente — isto é, o teste
// documentava a ausência da guarda como se fosse a especificação dela. Um guard
// que voltasse a devolver `true` passaria despercebido, que é exatamente como
// este chegou até aqui.
//
// Daí o PAR: um caso que passa e um que é barrado. Sem os dois, nenhuma
// afirmação sobre "guardar" é verificável.
// =============================================================================

const rotaFalsa = {} as ActivatedRouteSnapshot;
const estadoFalso = {} as RouterStateSnapshot;

function rodarGuard() {
  return TestBed.runInInjectionContext(() => pacAuthGuard(rotaFalsa, estadoFalso));
}

describe('pacAuthGuard', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('deixa passar quem tem token', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: PAC_AUTH_SESSION, useValue: { token: 'jwt-qualquer' } },
      ],
    });
    expect(rodarGuard()).toBe(true);
  });

  it('manda para /login quem não tem token', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: PAC_AUTH_SESSION, useValue: { token: null } },
      ],
    });
    const resultado = rodarGuard();
    expect(resultado).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(resultado as UrlTree)).toBe('/login');
  });

  it('sem o token providenciado deixa passar — a lib monta isolada em teste', () => {
    // Não é a porta do aplicativo: em app.routes.ts o `sessaoRequeridaGuard` do
    // shell barra um nível acima. Documentado aqui para que a próxima pessoa não
    // leia este `true` como "o guard não guarda".
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    expect(rodarGuard()).toBe(true);
  });
});
