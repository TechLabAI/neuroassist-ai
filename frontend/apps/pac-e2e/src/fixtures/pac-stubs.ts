/**
 * pac-stubs.ts — stubs de rota compartilhados da suíte PAC.
 *
 * Réplica do padrão já em uso no EDGE (`apps/edge-e2e/src/fixtures/edge-stubs.ts`):
 * o aplicativo roda com `providePac({ adapter: 'mock' })` fixo em `app.config.ts`
 * e, em modo mock, NENHUMA chamada HTTP sai — logo `page.route()` não intercepta
 * coisa alguma. Era esse o BLOCKER-4, e ele não exigia o pac-service no ar:
 * exigia que a app EMITISSE a chamada.
 *
 * `scriptAdaptadorHttp()` põe `window.__PAC_E2E_ADAPTER = 'http'` antes do
 * bootstrap; `providePac()` lê a global uma única vez e liga os adaptadores HTTP.
 * Semear depois do `goto` não tem efeito nenhum — o sintoma seria a app seguir em
 * mock enquanto os `page.route` daqui nunca são chamados, falha que se parece com
 * "o intercept não pegou".
 *
 * ── As URLs ──────────────────────────────────────────────────────────────────
 * Os padrões que a suíte usava (`**\/api/v1/pacientes**`) NÃO correspondiam a
 * endereço nenhum: os adaptadores HTTP chamam `/api/pac/v1/pacientes` e
 * `/api/gest/v1/tipos-gesto` (prefixo do módulo, roteado pelo GWY). Um stub
 * endereçado a uma URL que o produto nunca chama não testa nada — só falha de um
 * jeito que parece defeito de tela. Conferido em `paciente-http.adapter.ts`,
 * `comando-http.adapter.ts` e `tipos-gesto-read-http.adapter.ts`.
 */
import { Page, Route } from '@playwright/test';

export const PAC_BASE = '/api/pac/v1/pacientes';
export const GEST_TIPOS_BASE = '/api/gest/v1/tipos-gesto';

/** Paciente ativo do mock — o mesmo id que o resto do produto usa. */
export const PACIENTE_ID = 'pac-marcio';

// ---------------------------------------------------------------------------
// Cargas úteis — cópias das sementes dos adaptadores mock, para que o modo HTTP
// e o modo mock mostrem a MESMA tela quando o cenário não quer nada diferente.
// ---------------------------------------------------------------------------

export const PAGINA_PACIENTE = {
  content: [
    {
      id: PACIENTE_ID,
      tenantId: 'tenant-familia-amaral',
      nome: 'Márcio Amaral',
      dispositivo: 'edge-marcio-01',
      catalogoVersao: 'v1',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

export const PAGINA_SEM_PACIENTE = {
  content: [],
  totalElements: 0,
  totalPages: 1,
  number: 0,
  size: 20,
};

export const TIPOS_GESTO = [
  { id: 'tg-sobrancelha', nome: 'Levantar sobrancelha' },
  { id: 'tg-piscar-duplo', nome: 'Piscar duas vezes' },
  { id: 'tg-piscar-rapido', nome: 'Piscar rápido' },
  { id: 'tg-piscar-devagar', nome: 'Piscar devagar' },
  { id: 'tg-fechar-olhos', nome: 'Fechar os olhos' },
  { id: 'tg-mover-nariz', nome: 'Mover o nariz' },
  { id: 'tg-leitura-labial', nome: 'Leitura labial' },
];

export const COMANDOS_3 = [
  {
    id: 'cmd-ajuda',
    pacienteId: PACIENTE_ID,
    rotulo: 'Ajuda',
    urgencia: 'emergencia',
    primitivoPrincipal: 'tg-sobrancelha',
    primitivoSecundario: null,
    sensibilidade: 'media',
    tempoMinimoGestoMs: 400,
    tempoMaximoGestoMs: 1500,
    estado: 'published',
    etag: 'etag-cmd-ajuda-v1',
  },
  {
    id: 'cmd-agua',
    pacienteId: PACIENTE_ID,
    rotulo: 'água',
    urgencia: 'rotina',
    primitivoPrincipal: 'tg-sobrancelha',
    primitivoSecundario: 'tg-piscar-duplo',
    sensibilidade: 'alta',
    tempoMinimoGestoMs: 400,
    tempoMaximoGestoMs: 1800,
    estado: 'published',
    etag: 'etag-cmd-agua-v1',
  },
  {
    id: 'cmd-sapo',
    pacienteId: PACIENTE_ID,
    rotulo: 'Sapo',
    urgencia: 'rotina',
    primitivoPrincipal: 'tg-piscar-rapido',
    primitivoSecundario: 'tg-piscar-devagar',
    sensibilidade: 'media',
    tempoMinimoGestoMs: 400,
    tempoMaximoGestoMs: 1800,
    estado: 'published',
    etag: 'etag-cmd-sapo-v1',
  },
];

/** ProblemDetail RFC 7807 genérico. */
export function problemDetail(
  errorCode: string,
  status: number,
  detail: string,
  extra: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    type: `https://neuroassist.ai/problems/${errorCode}`,
    title: errorCode,
    status,
    detail,
    instance: PAC_BASE,
    errorCode,
    ...extra,
  };
}

// ---------------------------------------------------------------------------
// Modo HTTP
// ---------------------------------------------------------------------------

/**
 * Script para `page.addInitScript()` que põe a app em modo HTTP.
 *
 * <p>Tem de rodar ANTES de qualquer código da app: `providePac()` lê a global uma
 * única vez, no bootstrap.
 */
export function scriptAdaptadorHttp(): string {
  return `(() => { window.__PAC_E2E_ADAPTER = 'http'; })()`;
}

async function cumprir(route: Route, corpo: unknown, status = 200): Promise<void> {
  await route.fulfill({
    status,
    contentType: status >= 400 ? 'application/problem+json' : 'application/json',
    body: JSON.stringify(corpo),
  });
}

/**
 * Põe a página em modo HTTP e instala os stubs de base (paciente + tipos de gesto).
 *
 * <p>Chamar ANTES do `goto`. O Playwright casa rotas na ordem INVERSA de registro,
 * então qualquer `page.route` do próprio cenário, registrado depois, continua tendo
 * precedência sobre estes.
 *
 * <p>Os comandos NÃO entram aqui de propósito: é justamente a resposta que cada
 * cenário quer controlar (demora, 500, lista vazia). Quem não registrar nada para
 * `/comandos` recebe 404 do dev-server e vê a tela de erro — o que é informação,
 * não ruído.
 */
export async function aplicarModoHttp(page: Page): Promise<void> {
  await page.addInitScript(scriptAdaptadorHttp());

  await page.route(`**${GEST_TIPOS_BASE}**`, (route) => cumprir(route, TIPOS_GESTO));

  // `/pacientes/**/comandos` também casa `**/pacientes**`; o teste de `/comandos`
  // é o que impede este stub de sequestrar a lista de comandos quando ele acaba
  // registrado depois do stub específico do cenário.
  await page.route(`**${PAC_BASE}**`, async (route) => {
    if (route.request().url().includes('/comandos')) return route.fallback();
    return cumprir(route, PAGINA_PACIENTE);
  });
}

/** Stub da LISTA de comandos do paciente ativo. `atraso` simula chamada em voo. */
export async function rotaComandos(
  page: Page,
  corpo: unknown,
  status = 200,
  atrasoMs = 0,
): Promise<void> {
  await page.route(`**${PAC_BASE}/*/comandos*`, async (route) => {
    if (atrasoMs > 0) await new Promise((r) => setTimeout(r, atrasoMs));
    await cumprir(route, corpo, status);
  });
}

/** Stub da PÁGINA de pacientes (para sobrepor o de base num cenário de erro). */
export async function rotaPacientes(
  page: Page,
  corpo: unknown,
  status = 200,
): Promise<void> {
  await page.route(`**${PAC_BASE}*`, async (route) => {
    if (route.request().url().includes('/comandos')) return route.fallback();
    await cumprir(route, corpo, status);
  });
}
