import {
  ApplicationConfig,
  computed,
  inject,
  provideZoneChangeDetection,
  signal,
} from '@angular/core';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideGest, GEST_SERVICE_BASE_URL } from '@neuroassist/gest';
import { provideEvnt, EventDeliveryFacade } from '@neuroassist/evnt';
import {
  providePac,
  PAC_ACCESS_DECISION,
  PAC_AUTH_SESSION,
  PAC_PERMISSION_MAP,
} from '@neuroassist/pac';
import {
  provideAcs,
  acsHttpInterceptorFn,
  AcsEmpresaContextService,
  ACS_AUTH_SESSION,
  ACS_ACCESS_DECISION,
  MODULE_PERMISSION_MAP,
} from '@neuroassist/acs';
import { provideAuth, authBearerInterceptorFn } from '@neuroassist/shared/auth-login';
import { AuthSessionStore } from '@neuroassist/shared/auth-session';
import { accessHttpInterceptor, EMPRESA_ID_TOKEN } from '@neuroassist/shared/access-http-base';
import { provideEdge, EDGE_ACCESS_DECISION, EDGE_TOAST_TOKEN } from '@neuroassist/edge';
import { ToastService } from '@neuroassist/shared/ui-feedback';
import { provideHist } from '@neuroassist/hist';
import {
  provideDash,
  DASH_ACTIVE_EVENT_TOKEN,
  DASH_DEVICE_BADGE_TOKEN,
} from '@neuroassist/dash';
import type { ActiveEventVm } from '@neuroassist/dash';
import { provideConv } from '@neuroassist/conv';
import { provideTrei } from '@neuroassist/trei';
import { appRoutes } from './app.routes';
import { APP_ADAPTER_MODE } from './config/adapter-mode';
import { CURRENT_ADAPTER_MODE } from './config/adapter-mode-env';

// ---------------------------------------------------------------------------
// app.config.ts — root ApplicationConfig for neuroassist-ai-web.
//
// adapter: 'mock' for every module below — no backend microservice is required to
// serve/e2e this app locally (see docs/architecture/backend/standards/
// api-mock-convention.md). Flip individual modules to 'http' once their backend + GWY
// route is deployed and reachable (per-module TODO in each provideX call below).
//
// APP_ADAPTER_MODE (P2 FE-E2E-006): CURRENT_ADAPTER_MODE is resolved at build time via
// fileReplacements ('mock' | 'http') and exposed as an injectable token so e2e specs can
// read which build they are running against. It does NOT drive the per-module adapter
// choice above — that is deliberately manual (see comment above) — it is read-only
// observability for tests.
//
// GEST_SERVICE_BASE_URL override (P2 FE-E2E-004): unconditional, not gated by adapter
// mode. Harmless while eligibility stays 'mock' below (EligibilityHttpAdapter is never
// instantiated), and already correct — GWY-relative '/api/v1/gest' — for the day
// eligibility flips to 'http'. Must appear before provideGest() so the adapter picks up
// the overridden value. EDGE_BRIDGE_WS_URL is NOT overridden — it is localhost-only,
// never routed through GWY.
//
// Tokens cross-module do DASH:
//   DASH_ACTIVE_EVENT_TOKEN — LIGADO ao EventDeliveryFacade do EVNT (ver abaixo).
//   DASH_DEVICE_BADGE_TOKEN — ainda signal(null); ligar quando o EDGE expuser o badge.
// ---------------------------------------------------------------------------

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(appRoutes),

    // ORDEM dos interceptors = ordem de execução na ida.
    //
    // `authBearerInterceptorFn` vem primeiro porque identidade é anterior a
    // contexto de empresa: o `X-Empresa-Id` só faz sentido depois de existir
    // alguém autenticado. A corretude, porém, não depende disso — `HttpRequest`
    // é imutável e cada interceptor clona o que recebeu, então o segundo clone
    // preserva o header do primeiro. (No Feign do backend é o contrário: o
    // `RequestTemplate` é mutável e compartilhado, e ali a ordem morde.)
    //
    // `accessHttpInterceptor` (P1 FE-E2E-002) não decide Authorization — isso é
    // `authBearerInterceptorFn`, acima. Ele só acrescenta X-Empresa-Id (rastreabilidade,
    // nunca um portão — ver multi-empresa.md), X-Correlation-Id e Idempotency-Key, e
    // mapeia erro ProblemDetail (RFC 7807) para AppError.
    provideHttpClient(
      withInterceptors([authBearerInterceptorFn, acsHttpInterceptorFn, accessHttpInterceptor]),
    ),
    provideAnimations(),

    // P2 FE-E2E-006: expose build-time adapter mode as injectable token (read-only,
    // consumed by e2e specs — does not drive the manual per-module flips below).
    { provide: APP_ADAPTER_MODE, useValue: CURRENT_ADAPTER_MODE },

    // P1 FE-E2E-002: wire EMPRESA_ID_TOKEN to AcsEmpresaContextService.empresaId signal.
    // ACS is the sole writer of the active tenant id (ACS-003 / INT-FE-ACS-003).
    {
      provide: EMPRESA_ID_TOKEN,
      useFactory: () => inject(AcsEmpresaContextService).empresaId,
    },

    // P2 FE-E2E-004: GWY-relative base for GEST — see file header. Inert until
    // eligibility below flips to 'http'.
    { provide: GEST_SERVICE_BASE_URL, useValue: '/api/v1/gest' },

    // ------------------------------------------------------------------
    // Autenticação.
    //
    // Repare que NÃO há `adapter: 'mock' | 'http'` aqui, ao contrário de todos
    // os módulos abaixo: um login em mock aceitaria qualquer senha, que é
    // exatamente a única coisa que a tela existe para não fazer. E o login
    // também não passa pelo `provideAcs()`: autenticar é anterior a qualquer
    // módulo — não há empresa nem paciente antes de alguém entrar —, e flipar o
    // `provideAcs` para 'http' por causa do login arrastaria junto tenant,
    // cuidador e vínculo, que o onboarding consome em mock.
    //
    // `/acs-api` é caminho relativo de propósito: o acs-service recusa CORS
    // (`403 Invalid CORS request` para Origin http://localhost:4200), então a
    // chamada tem de ser de mesma origem. O dev-server faz o proxy
    // (apps/neuroassist-ai-web/proxy.conf.json); em produção é o reverse proxy.
    // ------------------------------------------------------------------
    provideAuth({ apiBase: '' }),

    // FIM DO "Wave-0 bypass" do ACS (libs/acs/.../acs.guard.ts):
    // enquanto ACS_AUTH_SESSION resolvia para null — o padrão —, o
    // `acsCoordinatorGuard` devolvia `true` e /acs inteiro ficava aberto.
    // Providenciar o store real faz o guard passar a guardar.
    //
    // O getter existe porque o contrato do token é `readonly token: string|null`
    // (um valor), e a sessão é um signal (uma função). O getter liga os dois sem
    // congelar o valor no momento da injeção — o que faria o guard decidir para
    // sempre com o estado do bootstrap.
    //
    // FIM DO "Wave-0 bypass" do ACS também na parte de PERMISSÃO.
    //
    // Ficou desligado enquanto os guards pediam `acs.cuidador.write` e
    // `acs.vinculo.write` — nomes que o acs-service nunca emitiu. Ligar assim
    // teria trancado a coordenadora fora das telas que só ela pode usar.
    //
    // O que faltava não era o provider: era APLICAR o `MODULE_PERMISSION_MAP`,
    // que existe justamente para traduzir chave de tela → authority do backend e
    // que nenhum guard consultava. Com a tradução no lugar (e duas entradas do
    // mapa corrigidas contra o RoleAuthorityPolicy), ligar passou a ser seguro.
    //
    // Uma chave desconhecida NEGA. O contrário — deixar passar o que não se sabe
    // traduzir — transforma erro de digitação em permissão concedida.
    {
      provide: ACS_ACCESS_DECISION,
      useFactory: () => {
        const sessao = inject(AuthSessionStore);
        return {
          can: (chaveDeTela: string) => {
            const authority = (MODULE_PERMISSION_MAP as Record<string, string>)[chaveDeTela];
            return authority === undefined ? false : sessao.pode(authority);
          },
        };
      },
    },
    {
      provide: ACS_AUTH_SESSION,
      useFactory: () => {
        const sessao = inject(AuthSessionStore);
        return {
          get token(): string | null {
            return sessao.token();
          },
        };
      },
    },
    // FIM DO "Wave-0 bypass" do PAC. Mesma história do ACS, um módulo depois:
    // `pacAuthGuard` e `pacCommandWriteGuard` devolviam `true` incondicionalmente,
    // com a implementação de verdade escrita em comentário logo acima do return.
    // As duas rotas do editor de comando (/pac/comandos/novo e
    // /pac/comandos/:id/editar, em app.routes.ts) estavam abertas a qualquer
    // sessão — inclusive ao apoio ao paciente, que não tem nenhuma authority de
    // escrita e cuja única capacidade é confirmar alarme.
    //
    // A tradução mora aqui e não no guard PORQUE o nome da authority é decisão
    // do acs-service e já mudou duas vezes. O guard pergunta pela CAPACIDADE
    // ('commandWrite'); o PAC_PERMISSION_MAP diz qual authority a representa
    // hoje — e quatro das cinco entradas dele citavam nomes que não existem em
    // lugar nenhum até serem corrigidas contra o RoleAuthorityPolicy.
    //
    // Chave desconhecida NEGA, como no ACS.
    {
      provide: PAC_ACCESS_DECISION,
      useFactory: () => {
        const sessao = inject(AuthSessionStore);
        return {
          can: (chaveDeTela: string) => {
            const authority = (PAC_PERMISSION_MAP as Record<string, string>)[chaveDeTela];
            return authority === undefined ? false : sessao.pode(authority);
          },
        };
      },
    },
    {
      provide: PAC_AUTH_SESSION,
      useFactory: () => {
        const sessao = inject(AuthSessionStore);
        return {
          get token(): string | null {
            return sessao.token();
          },
        };
      },
    },
    // FIM DO "Wave-0 bypass" do EDGE. O `edgeConductorGuard` e o `_canManage` da
    // DeviceFacade tratavam `EDGE_ACCESS_DECISION === null` como "pode tudo", e
    // null era o padrão: TODA sessão via os botões de reparear, desparear e
    // aposentar dispositivo — inclusive a cuidadora, que não responde por isso.
    //
    // Ao contrário do ACS, aqui a ligação é segura de fazer AGORA, porque o nome
    // confere com o produtor: o acs-service emite `edge.manage` para
    // COORDENADOR_CLINICA e só `edge.read` para CUIDADORA (verificado no
    // RoleAuthorityPolicy e numa resposta real do serviço). O frontend comparava
    // com `edge.device.manage`, que o backend nunca emitiu — o literal foi
    // acertado junto com esta ligação. Sem isso, ligar o token teria trancado
    // também a coordenadora, que é exatamente por que ACS_ACCESS_DECISION segue
    // desligado acima.
    {
      provide: EDGE_ACCESS_DECISION,
      useFactory: () => {
        const sessao = inject(AuthSessionStore);
        return { can: (permissao: string) => sessao.pode(permissao) };
      },
    },
    // A superfície de aviso do SPEC-07 passa a EXISTIR.
    //
    // As facades do EDGE fazem `this.toast?.show(...)` com `inject(..., {optional:true})`,
    // e ninguém providenciava o token: `undefined?.show()` — silêncio. Uma
    // cuidadora sem permissão de gerenciar dispositivo clicava, tomava 403 e a
    // tela não dizia nada. O `?.` fazia isso passar por tratamento de erro.
    //
    // A adaptação de assinatura fica aqui, e não no ToastService: o contrato do
    // EDGE é `show(mensagem, acao?)` com ação `{label, callback}`, e é ele quem
    // deve se acomodar ao serviço compartilhado — não o contrário.
    {
      provide: EDGE_TOAST_TOKEN,
      useFactory: () => {
        const toasts = inject(ToastService);
        return {
          show: (mensagem: string, acao?: { label: string; callback: () => void }) => {
            toasts.show(mensagem, acao);
          },
        };
      },
    },
    // ADR-007: o motor de detecção é o browser (MediaPipe), não mais a ponte com
    // o agente Python. Elegibilidade segue em mock até a P2 do plan-FE-E2E.
    provideGest({ detection: 'browser', eligibility: 'mock' }),
    provideEvnt({ adapter: 'mock' }),
    providePac({ adapter: 'mock' }),
    provideAcs({ adapter: 'mock' }),
    provideEdge({ adapter: 'mock' }),
    provideHist({ adapter: 'mock' }),
    provideDash({ adapter: 'mock' }),
    // CONV — Conversação por Árvore de Falas (patient-only shell, /paciente route).
    // ProposalPort is always mock (G-2/CONV-INT-006 — real endpoint: future TREI module).
    // TODO[CONV-INT-001]: flip adapter:'http' once conv-service BE is deployed via GWY.
    provideConv({ adapter: 'mock' }),
    // TREI — Treinamento de Árvore de Fala (caregiver authoring surface — Tela 08).
    // TODO[INT-FE-TREI-001..004]: flip adapter:'http' once trei-service is deployed via GWY.
    provideTrei({ adapter: 'mock' }),
    // INT-FE-DASH-001 — activeEvent do EVNT ligado ao token do DASH.
    // Esta é a ligação entre módulos que sustenta o invariante `calm` do
    // DashHomeFacade: calm === true  ⟺  não há alarme ativo no EVNT. Enquanto o
    // token era signal(null), a home dizia "Márcio está bem" com um alarme em
    // curso — o oposto exato do que o produto promete (SPEC-01 §3).
    //
    // A projeção é feita aqui, em scope:web, e não dentro do DASH ou do EVNT:
    // nenhum dos dois pode importar o outro (ADR-005 / nx-boundary).
    //
    // patientId vai vazio de propósito: EventoVm não carrega o id do paciente
    // (só patientName) e o DASH usa apenas o null-check de activeEvent para
    // derivar `calm`. Preencher com um id inventado seria pior que deixar vazio.
    // Divergência de contrato registrada no ES — resolver acrescentando
    // patientId a EventoVm ou removendo-o de ActiveEventVm.
    {
      provide: DASH_ACTIVE_EVENT_TOKEN,
      useFactory: () => {
        const evnt = inject(EventDeliveryFacade);
        return computed<ActiveEventVm | null>(() => {
          const evento = evnt.activeEvent();
          if (!evento) return null;
          return {
            eventId: evento.eventoId,
            urgencia: evento.urgencia,
            patientId: '',
          };
        });
      },
    },
    // INT-FE-DASH-002 — badge de dispositivo do EDGE: ainda pendente.
    { provide: DASH_DEVICE_BADGE_TOKEN, useValue: signal(null) },
  ],
};
