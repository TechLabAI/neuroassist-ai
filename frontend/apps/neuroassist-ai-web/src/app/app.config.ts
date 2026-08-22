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
import { provideAcs, acsHttpInterceptorFn } from '@neuroassist/acs';
import { provideEdge } from '@neuroassist/edge';
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
    provideHttpClient(withInterceptors([acsHttpInterceptorFn])),
    provideAnimations(),
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
