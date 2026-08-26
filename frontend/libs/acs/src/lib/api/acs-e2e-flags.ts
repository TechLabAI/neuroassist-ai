// =============================================================================
// Bandeiras de e2e do ACS — o nome de cada uma, num lugar só.
//
// POR QUE ISTO É UM MÓDULO E NÃO UMA STRING SOLTA
//
// Quem ESCREVE a bandeira é a suíte de teste; quem a LÊ é o adaptador mock. São
// dois lados que precisam concordar num literal, e é exatamente a forma do
// defeito nº 1 desta casa: o consumidor escreve o literal pelo que acha que ele
// é, o produtor lê outro, e nada acusa — a bandeira simplesmente não faz efeito,
// e o teste passa ou falha por um motivo que ninguém investiga.
//
// Até aqui os quatro literais do ACS viviam copiados nos dois lados: três no
// `acs-cuidador-mock.adapter.ts` e as mesmas três digitadas de novo dentro de
// `page.addInitScript(...)` no `caregiver-roster.spec.ts`. Com o literal aqui,
// escrever errado não compila.
//
// Espelha `libs/evnt/src/lib/api/evnt-e2e-flags.ts`.
// =============================================================================

export const ACS_E2E_FLAGS = {
  /** `list()` dos cuidadores estoura — habilita o estado de erro do roster. */
  falhaListar: '__ACS_E2E_LIST_FAIL__',
  /** `create()` do cuidador estoura — habilita o cenário de preservação de rascunho. */
  falhaCriar: '__ACS_E2E_CREATE_FAIL__',
  /** `list()` demora 3s em vez de 400ms — torna a janela de carregamento observável. */
  listaLenta: '__ACS_E2E_SLOW_LIST__',
  /**
   * `list()` acrescenta uma cuidadora de OUTRA empresa ao elenco.
   *
   * <p>Fica atrás de bandeira porque o elenco base é afirmado por contagem em
   * vários cenários já verdes (`roster-row` = 2, `unlinked-caregiver-row` = 2).
   * Semear a forasteira sempre reprovaria seis cenários que não têm nada a ver
   * com o assunto.
   */
  crossEmpresa: '__ACS_E2E_CROSS_TENANT__',
  /**
   * `link()` estoura com erro de rede (500) — habilita o cenário em que a
   * cobertura é RE-DERIVADA depois de um erro de vínculo.
   */
  falhaVincular: '__ACS_E2E_LINK_FAIL__',
  /**
   * A empresa nasce EM PROVISIONAMENTO em vez de já ativa.
   *
   * <p>Vale para os dois lados do `AcsTenantMockAdapter`: o registro semeado
   * (`tenant-familia-amaral`) passa a responder `provisioning` no `get()`, e o
   * `create()` devolve 202 em vez de 201. Sem ela o mock devolvia `active`
   * incondicionalmente e o estado intermediário — que o backend produz de
   * verdade, porque provisionar schema é uma saga — não tinha como aparecer na
   * tela.
   *
   * <p>Fica atrás de bandeira porque o cenário 1 desta suíte afirma o caminho
   * feliz (201 ativo na hora); semear provisionamento sempre o reprovaria.
   */
  empresaProvisionando: '__ACS_E2E_TENANT_PROVISIONING__',
  /**
   * `retryProvisioning()` NÃO resolve — devolve `provisioning` de novo.
   *
   * <p>É o cenário da faixa "ainda não confirmada": a pessoa tenta de novo e a
   * saga continua pendente. Separada de `empresaProvisionando` porque as duas
   * metades são estados distintos do produto (em curso × retentativa que não
   * resolveu) e o cenário do retry BEM-SUCEDIDO precisa da primeira sem a
   * segunda.
   */
  retryNaoResolve: '__ACS_E2E_TENANT_RETRY_STUCK__',
} as const;

/**
 * Identidade da cuidadora forasteira semeada por `crossEmpresa`.
 *
 * <p>Exportada porque a suíte precisa dela para endereçar a linha certa, e um
 * segundo literal digitado no teste seria o mesmo defeito que este módulo existe
 * para impedir.
 */
export const ACS_E2E_CUIDADORA_OUTRA_EMPRESA = {
  id: 'cuid-outra-empresa',
  nome: 'Fora da Empresa',
  /** Diferente de 'tenant-familia-amaral' — é o que torna o vínculo proibido. */
  tenantId: 'tenant-outra-empresa',
} as const;
