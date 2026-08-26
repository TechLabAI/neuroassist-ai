import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { DeviceAuthHttpAdapter, DeviceAuthErro } from './device-auth-http.adapter';
import { SKIP_AUTH_HEADER } from '@neuroassist/shared/contracts';

// =============================================================================
// DeviceAuthHttpAdapter — unit tests (P2 — GEST-FE-027)
//
// Proveniência do contrato: lido de AutenticacaoController.deviceToken() +
// DeviceTokenRequest.java + AutenticacaoResponse.java do acs-service.
// Fixture escrita a partir do produtor, não deduzida pelo nome dos campos.
//
// O que estes testes protegem:
//   1. Corpo da requisição tem SOMENTE clientId e clientSecret (sem campos extras)
//   2. 401 → DeviceAuthErro('credencial-invalida')
//   3. Rede (status 0) → DeviceAuthErro('rede')
//   4. 5xx → DeviceAuthErro('servidor')
//   5. Sucesso → deviceId = sub, tenantId = tenant
//   6. clientSecret nunca aparece na exceção lançada
// =============================================================================

// Fixture copiada da forma do produtor (AutenticacaoResponse.java).
// Para token de dispositivo: refreshToken=null, papel=null, nome=null.
const FIXTURE_TOKEN_DISPOSITIVO = {
  accessToken: 'eyJhbGciOiJSUzI1NiJ9.fixture-device-access.assinatura',
  refreshToken: null,
  tokenType: 'Bearer',
  expiresIn: 3600,
  tenant: 'd0000000-0000-0000-0000-000000000001',
  sub: 'a0000000-0000-0000-0000-000000000042',
  papel: null,
  nome: null,
  authorities: ['edge.device'],
};

describe('DeviceAuthHttpAdapter', () => {
  let adapter: DeviceAuthHttpAdapter;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        DeviceAuthHttpAdapter,
      ],
    });
    adapter = TestBed.inject(DeviceAuthHttpAdapter);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('contrato de requisição', () => {
    it('deve usar POST para /oauth2/token/device', async () => {
      const promise = adapter.autenticar({ clientId: 'edge-abc', clientSecret: 'secret-123' });

      const req = httpMock.expectOne('/oauth2/token/device');
      expect(req.request.method).toBe('POST');
      req.flush(FIXTURE_TOKEN_DISPOSITIVO);
      await promise;
    });

    it('deve enviar somente clientId e clientSecret no corpo', async () => {
      const promise = adapter.autenticar({ clientId: 'edge-abc', clientSecret: 'secret-123' });

      const req = httpMock.expectOne('/oauth2/token/device');
      expect(req.request.body).toEqual({ clientId: 'edge-abc', clientSecret: 'secret-123' });
      // Controle negativo: tenantId não deve aparecer (DeviceTokenRequest.java exclui)
      expect(Object.keys(req.request.body as object)).not.toContain('tenantId');
      req.flush(FIXTURE_TOKEN_DISPOSITIVO);
      await promise;
    });

});

  describe('mapeamento da resposta', () => {
    it('deve extrair deviceId do campo sub da resposta', async () => {
      const promise = adapter.autenticar({ clientId: 'edge-abc', clientSecret: 'secret-123' });
      httpMock.expectOne('/oauth2/token/device').flush(FIXTURE_TOKEN_DISPOSITIVO);

      const resultado = await promise;
      expect(resultado.deviceId).toBe('a0000000-0000-0000-0000-000000000042');
    });

    it('deve extrair tenantId do campo tenant da resposta', async () => {
      const promise = adapter.autenticar({ clientId: 'edge-abc', clientSecret: 'secret-123' });
      httpMock.expectOne('/oauth2/token/device').flush(FIXTURE_TOKEN_DISPOSITIVO);

      const resultado = await promise;
      expect(resultado.tenantId).toBe('d0000000-0000-0000-0000-000000000001');
    });

    it('deve incluir a authority edge.device na resposta', async () => {
      const promise = adapter.autenticar({ clientId: 'edge-abc', clientSecret: 'secret-123' });
      httpMock.expectOne('/oauth2/token/device').flush(FIXTURE_TOKEN_DISPOSITIVO);

      const resultado = await promise;
      expect(resultado.authorities).toContain('edge.device');
    });

    it('deve ter refreshToken sempre null', async () => {
      const promise = adapter.autenticar({ clientId: 'edge-abc', clientSecret: 'secret-123' });
      httpMock.expectOne('/oauth2/token/device').flush(FIXTURE_TOKEN_DISPOSITIVO);

      const resultado = await promise;
      expect(resultado.refreshToken).toBeNull();
    });
  });

  describe('tratamento de erros', () => {
    it('401 deve lançar DeviceAuthErro com motivo credencial-invalida', async () => {
      const promise = adapter.autenticar({ clientId: 'edge-abc', clientSecret: 'errado' });
      httpMock.expectOne('/oauth2/token/device').flush(
        { error: 'unauthorized' },
        { status: 401, statusText: 'Unauthorized' },
      );

      await expect(promise).rejects.toMatchObject({ motivo: 'credencial-invalida' });
    });

    it('401 não deve vazar clientSecret na mensagem de erro', async () => {
      const promise = adapter.autenticar({ clientId: 'edge-abc', clientSecret: 'super-secret-123' });
      httpMock.expectOne('/oauth2/token/device').flush(
        { error: 'unauthorized' },
        { status: 401, statusText: 'Unauthorized' },
      );

      try {
        await promise;
        fail('Deveria ter lançado erro');
      } catch (err) {
        const errorStr = JSON.stringify(err);
        expect(errorStr).not.toContain('super-secret-123');
        expect(err).toBeInstanceOf(DeviceAuthErro);
      }
    });

    it('falha de rede (status 0) deve lançar DeviceAuthErro com motivo rede', async () => {
      const promise = adapter.autenticar({ clientId: 'edge-abc', clientSecret: 'secret-123' });
      httpMock.expectOne('/oauth2/token/device').flush(null, { status: 0, statusText: 'Unknown Error' });

      await expect(promise).rejects.toMatchObject({ motivo: 'rede' });
    });

    it('500 deve lançar DeviceAuthErro com motivo servidor', async () => {
      const promise = adapter.autenticar({ clientId: 'edge-abc', clientSecret: 'secret-123' });
      httpMock.expectOne('/oauth2/token/device').flush(
        { error: 'internal server error' },
        { status: 500, statusText: 'Internal Server Error' },
      );

      await expect(promise).rejects.toMatchObject({ motivo: 'servidor' });
    });
  });
  // ---------------------------------------------------------------------------
  // O token de usuário esquecido no navegador
  // ---------------------------------------------------------------------------

  describe('SKIP_AUTH_HEADER — não carregar credencial de usuário nesta chamada', () => {
    it('marca a requisição para o interceptor de Authorization NÃO anexar o token de usuário', () => {
      // POR QUE ISTO IMPORTA, e não é zelo excessivo:
      //
      // `AUTH_BEARER_URL_PREFIXES` inclui `/oauth2`, e `/oauth2/token/device` casa
      // com o prefixo. Basta alguém ter entrado no sistema NAQUELA máquina para
      // parear o dispositivo — o token dessa pessoa fica no sessionStorage e depois
      // expira. A partir daí o interceptor anexa o token velho AQUI.
      //
      // O endpoint é `permitAll`, mas fica atrás do filtro do resource server, que
      // rejeita o token inválido antes de o controller ser alcançado. Medido contra
      // o acs-service em execução:
      //
      //   sem Authorization ............ 401 acs.auth.invalid-credentials
      //   com Bearer de usuário velho .. 401 acs.auth-unauthenticated (não chegou)
      //
      // Os dois são 401 — e o store trata 401 como estado TERMINAL. A estação
      // recusaria iniciar acusando a credencial do dispositivo, sendo que a causa é
      // um resto de sessão de usuário.
      void adapter.autenticar({ clientId: 'edge-abc', clientSecret: 'secret-123' });

      const req = httpMock.expectOne('/oauth2/token/device');
      expect(req.request.context.get(SKIP_AUTH_HEADER)).toBe(true);
      req.flush(FIXTURE_TOKEN_DISPOSITIVO);
    });
  });
});
