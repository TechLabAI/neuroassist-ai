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
import { provideGest } from '@neuroassist/gest';
import { provideEvnt, EventDeliveryFacade } from '@neuroassist/evnt';
import { providePac } from '@neuroassist/pac';
import { provideAcs, acsHttpInterceptorFn, ACS_AUTH_SESSION } from '@neuroassist/acs';
import { provideAuth, authBearerInterceptorFn } from '@neuroassist/shared/auth-login';
import { AuthSessionStore } from '@neuroassist/shared/auth-session';
import { provideEdge, EDGE_ACCESS_DECISION } from '@neuroassist/edge';
import { provideHist } from '@neuroassist/hist';
import {
  provideDash,
  DASH_ACTIVE_EVENT_TOKEN,
  DASH_DEVICE_BADGE_TOKEN,
} from '@neuroassist/dash';
import type { ActiveEventVm } from '@neuroassist/dash';
import { appRoutes } from './app.routes';

// ---------------------------------------------------------------------------
// app.config.ts — root ApplicationConfig for neuroassist-ai-web.
//
// adapter: 'mock' for every module — no backend microservice is required to
// serve/e2e this app locally (see docs/architecture/backend/standards/
// api-mock-convention.md). Flip individual modules to 'http' once their
// backend + GWY route is deployed and reachable (per-module TODO in each
// provide<Module>() factory).
//
// acsHttpInterceptorFn: ACS-specific HTTP interceptor that adds X-Empresa-Id
//   header to ACS requests once a tenant has been provisioned (INT-FE-ACS-001).
//   Tenant provisioning routes are excluded via SKIP_EMPRESA_ID_HEADER token.
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
    provideHttpClient(withInterceptors([authBearerInterceptorFn, acsHttpInterceptorFn])),
    provideAnimations(),

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
    provideAuth({ apiBase: '/acs-api' }),

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
    // ACS_ACCESS_DECISION segue NÃO providenciado, deliberadamente: os guards de
    // escrita do ACS pedem `acs.cuidador.write`/`acs.vinculo.write`, e o
    // acs-service não emite nenhuma das duas (emite `acs.cuidador.criar`,
    // `.read`, `.desativar`). Ligar o token hoje trancaria a coordenadora para
    // fora das telas que ela pode usar. Divergência a resolver no ACS.
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
    // ADR-007: o motor de detecção é o browser (MediaPipe), não mais a ponte com
    // o agente Python. Elegibilidade segue em mock até a P2 do plan-FE-E2E.
    provideGest({ detection: 'browser', eligibility: 'mock' }),
    provideEvnt({ adapter: 'mock' }),
    providePac({ adapter: 'mock' }),
    provideAcs({ adapter: 'mock' }),
    provideEdge({ adapter: 'mock' }),
    provideHist({ adapter: 'mock' }),
    provideDash({ adapter: 'mock' }),
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
