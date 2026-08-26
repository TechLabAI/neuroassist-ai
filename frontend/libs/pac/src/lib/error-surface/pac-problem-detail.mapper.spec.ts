import { mapProblemDetailToAppError, extractErrorCode } from './pac-problem-detail.mapper';

// =============================================================================
// O mapeador não tinha teste nenhum — descobri isso ao mudar a precedência da
// mensagem e nada ficar vermelho. Ele é o ponto por onde TODO erro do backend
// entra na tela; sem cobertura, a regra que decide o que a pessoa lê vivia de
// palavra.
// =============================================================================

describe('mapProblemDetailToAppError', () => {
  it('extrai o código do sufixo do type', () => {
    expect(extractErrorCode('https://neuroassist.ai/problems/missing-tenant')).toBe('missing-tenant');
    expect(extractErrorCode('/errors/validation')).toBe('validation');
    expect(extractErrorCode(undefined)).toBe('unknown');
  });

  // ── precedência da mensagem ────────────────────────────────────────────────

  it('para código CONHECIDO, a mensagem canônica em pt-BR ganha do detail', () => {
    // O `detail` é escrito para quem depura, em inglês e técnico, e ia direto
    // para a tela de uma coordenadora.
    const erro = mapProblemDetailToAppError({
      type: 'https://neuroassist.ai/problems/missing-tenant',
      status: 403,
      detail: 'The request does not carry a valid X-Empresa-Id header.',
    });

    expect(erro.code).toBe('missing-tenant');
    expect(erro.message).not.toContain('X-Empresa-Id');
    // A frase canônica diz o que houve e o que fazer. Foi reescrita junto com
    // esta mudança: "tenant" é palavra de quem escreve o sistema, e "contate o
    // administrador" não dá à pessoa nada que ela possa fazer.
    expect(erro.message).toMatch(/sessão|entre de novo/i);
  });

  it('para código DESCONHECIDO, o detail é melhor que nada', () => {
    // Dizer algo específico, ainda que em inglês, é melhor que "Erro
    // desconhecido" — é o único fio que quem for investigar terá.
    const erro = mapProblemDetailToAppError({
      type: 'https://neuroassist.ai/problems/coisa-nova',
      status: 500,
      detail: 'Downstream sensor calibration bus timed out.',
    });

    expect(erro.code).toBe('coisa-nova');
    expect(erro.message).toBe('Downstream sensor calibration bus timed out.');
  });

  it('sem detail e sem código conhecido, cai no title e depois na frase genérica', () => {
    expect(mapProblemDetailToAppError({ type: '/errors/x', status: 500, title: 'Boom' }).message).toBe('Boom');
    expect(mapProblemDetailToAppError({ status: 500 }).message).toBe('Erro desconhecido.');
  });

  // ── erros por campo ────────────────────────────────────────────────────────

  it('carrega fieldErrors quando o backend os manda', () => {
    // É o que permite dizer a frase ao LADO do campo, em vez de uma genérica no
    // rodapé. Antes a lista chegava e era jogada fora.
    const erro = mapProblemDetailToAppError({
      type: '/errors/validation',
      status: 400,
      detail: 'dispositivo: device reference unknown to EDGE.',
      fieldErrors: [{ field: 'dispositivo', message: 'Dispositivo desconhecido no catálogo EDGE.' }],
    });

    expect(erro.fieldErrors).toHaveLength(1);
    expect(erro.fieldErrors?.[0]).toEqual({
      field: 'dispositivo',
      message: 'Dispositivo desconhecido no catálogo EDGE.',
    });
  });

  it('sem fieldErrors, o campo fica ausente e não uma lista vazia', () => {
    // Lista vazia e ausência querem dizer coisas diferentes para quem consome:
    // `fieldErrors?.length` decide se há erro por campo a desenhar.
    const erro = mapProblemDetailToAppError({ type: '/errors/validation', status: 400 });
    expect(erro.fieldErrors).toBeUndefined();
  });

  it('preserva o type — é o discriminante de quem trata erro tipado', () => {
    const erro = mapProblemDetailToAppError({ type: '/errors/tenant-mismatch', status: 403 });
    expect(erro.type).toBe('/errors/tenant-mismatch');
  });
});
