import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { pacCommandWriteGuard } from './pac-command-write.guard';
import { PAC_ACCESS_DECISION } from './pac-access-tokens';
import { PAC_PERMISSION_MAP } from '../pac.descriptor';

// =============================================================================
// pacCommandWriteGuard — o guard agora GUARDA.
//
// Ver a nota do par em pac-auth.guard.spec.ts: um caso que passa e um que é
// barrado, porque o teste anterior afirmava só o `true` incondicional.
//
// O terceiro teste é o que teria evitado o defeito do ACS: prova que a chave de
// tela consultada pelo guard EXISTE no PAC_PERMISSION_MAP. Quando o guard
// pergunta por uma chave que o mapa não conhece, o provider de app.config.ts
// nega — e a tela some para todo mundo, inclusive para quem tem direito, sem
// nenhum erro visível. Foi assim que quatro entradas do mapa passaram meses
// citando authorities inexistentes sem nada acusar.
// =============================================================================

const rotaFalsa = {} as ActivatedRouteSnapshot;
const estadoFalso = {} as RouterStateSnapshot;

/** Decisão que traduz pelo mapa, como o provider real de app.config.ts faz. */
function decisaoPara(authorities: readonly string[]) {
  return {
    can: (chaveDeTela: string) => {
      const authority = (PAC_PERMISSION_MAP as Record<string, string>)[chaveDeTela];
      return authority === undefined ? false : authorities.includes(authority);
    },
  };
}

// Recopiadas do RoleAuthorityPolicy do acs-service (só as do PAC importam aqui).
const AUTHORITIES_COORDENADORA = [
  'pac.paciente.criar',
  'pac.paciente.read',
  'pac.catalogo-ativo.read',
  'pac.comando-authoring.criar',
  'pac.comando-lifecycle.read',
  'pac.comando-lifecycle.publicar',
];
// O apoio ao paciente não tem NENHUMA authority de escrita: confirma alarme e
// mais nada. É o papel que separa "pode ler o catálogo" de "pode editá-lo" —
// a cuidadora NÃO serve para este cenário, porque ela também escreve comando.
const AUTHORITIES_APOIO = ['dash.panel-composition.read', 'evnt.event-confirmation.confirmar'];

function rodarGuard() {
  return TestBed.runInInjectionContext(() =>
    pacCommandWriteGuard(rotaFalsa, estadoFalso),
  );
}

describe('pacCommandWriteGuard', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('deixa a coordenadora entrar no editor de comando', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: PAC_ACCESS_DECISION, useValue: decisaoPara(AUTHORITIES_COORDENADORA) },
      ],
    });
    expect(rodarGuard()).toBe(true);
  });

  it('devolve quem não escreve comando ao catálogo, que ele ainda pode ler', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: PAC_ACCESS_DECISION, useValue: decisaoPara(AUTHORITIES_APOIO) },
      ],
    });
    const resultado = rodarGuard();
    expect(resultado).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(resultado as UrlTree)).toBe('/pac');
  });

  it('a chave de tela que o guard consulta existe no PAC_PERMISSION_MAP', () => {
    expect(PAC_PERMISSION_MAP).toHaveProperty('commandWrite');
    expect(PAC_PERMISSION_MAP.commandWrite).toBe('pac.comando-authoring.criar');
  });

  it('chave desconhecida NEGA — erro de digitação não vira permissão concedida', () => {
    const decisao = decisaoPara(AUTHORITIES_COORDENADORA);
    expect(decisao.can('chaveQueNinguemDefiniu')).toBe(false);
  });

  it('sem o token providenciado deixa passar — a lib monta isolada em teste', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    expect(rodarGuard()).toBe(true);
  });
});
