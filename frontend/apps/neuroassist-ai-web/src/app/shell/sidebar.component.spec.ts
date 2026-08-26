import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthSessionStore, type Papel } from '@neuroassist/shared/auth-session';
import { SidebarComponent } from './sidebar.component';

// =============================================================================
// O menu não é escondido — ele não é renderizado.
//
// A distinção é o ponto destes testes. Um item com `display:none` continua no
// DOM: navegável por teclado, lido por leitor de tela, visível a quem inspeciona
// a página. As asserções abaixo contam ANCORAS NO DOM, não elementos visíveis —
// então esconder por CSS reprovaria aqui.
// =============================================================================

const AUTHORITIES_CUIDADORA = [
  'pac.catalogo-ativo.read',
  'evnt.event-query.read',
  'evnt.event-confirmation.confirmar',
  'dash.panel-composition.read',
  'edge.read',
  'acs.cuidador.read',
  'hist:read',
];

const AUTHORITIES_COORDENADORA = [
  ...AUTHORITIES_CUIDADORA,
  'acs.cuidador.criar',
  'pac.paciente.vincular',
  'edge.manage',
  'gest.detection-profile.publicar',
  'hist:read:all',
];

describe('SidebarComponent', () => {
  let fixture: ComponentFixture<SidebarComponent>;
  let store: AuthSessionStore;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [provideRouter([])],
    }).compileComponents();
    store = TestBed.inject(AuthSessionStore);
    fixture = TestBed.createComponent(SidebarComponent);
  });

  /** Os `data-menu-item` presentes no DOM, na ordem em que aparecem. */
  function itensNoDom(): string[] {
    return Array.from(
      fixture.nativeElement.querySelectorAll('[data-menu-item]') as NodeListOf<HTMLElement>,
    ).map((el) => el.getAttribute('data-menu-item') ?? '');
  }

  function entrarComo(papel: Papel, authorities: readonly string[]): void {
    // O store não expõe setter de usuário; `entrar` é o caminho real, e é o que
    // o login vai usar. Espelha o corpo de POST /oauth2/token.
    store.entrar({
      accessToken: 'jwt-de-teste',
      refreshToken: null,
      tokenType: 'Bearer',
      expiresIn: 900,
      tenant: 't-1',
      sub: 'u-1',
      papel,
      nome: 'Jamilly Amaral',
      authorities,
    });
    fixture.detectChanges();
  }

  it('sem sessão, o DOM não contém nenhum item de menu', () => {
    fixture.detectChanges();
    expect(itensNoDom()).toEqual([]);
    // Nem o rodapé de identidade — não há identidade.
    expect(fixture.nativeElement.querySelector('[data-testid="sidebar-papel"]')).toBeNull();
  });

  it('cuidadora: só os quatro itens do dia a dia estão no DOM', () => {
    entrarComo('CUIDADORA', AUTHORITIES_CUIDADORA);
    expect(itensNoDom()).toEqual(['painel', 'alarmes', 'comandos', 'historico']);
    // "Equipe" não está escondida: não existe.
    expect(fixture.nativeElement.querySelector('[data-menu-item="equipe"]')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Equipe');
  });

  it('coordenadora: os itens da clínica aparecem, sob o rótulo "Clínica"', () => {
    entrarComo('COORDENADOR_CLINICA', AUTHORITIES_COORDENADORA);
    expect(itensNoDom()).toEqual([
      'painel',
      'alarmes',
      'comandos',
      'historico',
      'equipe',
      'pacientes-sem-cuidadora',
      'dispositivos',
      'deteccao',
      'onboarding',
    ]);
    expect(fixture.nativeElement.textContent).toContain('Clínica');
  });

  it('apoio: painel, alarme e histórico — e nenhuma ação de configuração', () => {
    entrarComo('PACIENTE_APOIO', [
      'dash.panel-composition.read',
      'hist:read',
      'evnt.event-confirmation.confirmar',
    ]);
    expect(itensNoDom()).toEqual(['painel', 'alarmes', 'historico']);
  });

  it('o rodapé mostra o papel de quem entrou, e não um rótulo fixo', () => {
    entrarComo('COORDENADOR_CLINICA', AUTHORITIES_COORDENADORA);
    const rodape: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="sidebar-papel"]',
    );
    expect(rodape.textContent?.trim()).toBe('Coordenadora');
  });

  it('sair esvazia o menu sem recarregar a página', () => {
    entrarComo('COORDENADOR_CLINICA', AUTHORITIES_COORDENADORA);
    expect(itensNoDom().length).toBeGreaterThan(0);
    store.sair();
    fixture.detectChanges();
    expect(itensNoDom()).toEqual([]);
  });
});
