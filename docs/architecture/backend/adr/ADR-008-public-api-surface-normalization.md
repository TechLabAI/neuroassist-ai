<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-008 — Superfície pública normalizada em `/api/v1/<svc>/**` no GWY

## Status
aceita

## Contexto

Cada um dos 7 serviços de domínio escolheu o seu próprio prefixo de `@RequestMapping` durante o
scaffold, sem uma convenção transversal. O resultado, verificado no código:

| Serviço | `@RequestMapping` |
|---|---|
| ACS | `/api/v1/acs/{tenants,caregivers,directory}`, `/oauth2` |
| HIST | `/api/v1/hist/{history,audit-facts,audit-records,fact-types,patients}` |
| EDGE | `/api/v1/edge/{devices,heartbeat}`, `/edge-service/profile-pointer` |
| PAC | `/api/pac/v1/pacientes` |
| EVNT | `/api/v1/events`, `/api/v1/reports`, `/evnt-service/notification-orchestration` |
| DASH | `/panel`, `/internal/notifications` |
| GEST | `/gest-service/{eligibility,tipo-gesto,detection-profile,detection-contract}` |

São **quatro formatos distintos** (`/api/v1/<svc>/`, `/api/<svc>/v1/`, `/api/v1/<recurso>`,
`/<svc>-service/`), mais `/panel` e `/internal` no DASH. Nenhum serviço declara
`server.servlet.context-path`.

Emergiu, porém, uma convenção implícita e consistente de **visibilidade**:

| Prefixo | Significado de fato |
|---|---|
| `/api/...` | superfície pública, consumida pelo browser |
| `/<svc>-service/...`, `/internal/...` | serviço-a-serviço, nunca destinada a cliente externo |

Ao habilitar o roteamento dos 7 serviços no GWY (E2E-001), as rotas de API foram escritas como
`/api/v1/<svc>/**` — normalizadas — mas **sem filtro de rewrite**. Como o gateway encaminha o
path inalterado, ele precisa coincidir com o `@RequestMapping` do serviço; em 4 dos 7 não
coincide, e a request morre em `404` no serviço. (As rotas de *health* funcionam porque têm
`StripPrefix=3` — foi o que o check `INF-02` validou, o que não prova alcançabilidade das rotas
de API.)

Isso força uma decisão que estava implícita: **qual é o contrato público do backend** — o path
que cada controller escolheu, ou um path uniforme imposto na borda?

Forças em jogo:
- O frontend consome tudo através do GWY; hoje o `proxy.conf.json` precisaria de 9 prefixos.
- Publicar os paths reais exporia superfície de serviço-a-serviço (todo o `/gest-service/**`;
  a vizinhança de `/internal/notifications` no DASH).
- A comunicação interna usa Feign via `lb://` no Eureka e **não** passa pelo gateway — portanto
  não é afetada por nenhuma das opções.
- ADR-002 (local do GWY) já estabelece que diferenças por rota são expressas como
  **configuração declarativa de filtros** dentro de um único gateway.

## Decisão

A superfície pública do backend é **`/api/v1/<svc>/**`** para os 7 serviços de domínio, mais
`/oauth2/**` (ACS como Authorization Server). O GWY reconcilia essa superfície com o path real
de cada controller através de **`RewritePath` por rota** — os `@RequestMapping` dos serviços
**não** mudam.

```
/api/v1/acs/**    →  (sem rewrite)          ACS
/api/v1/hist/**   →  (sem rewrite)          HIST
/api/v1/edge/**   →  (sem rewrite)          EDGE
/api/v1/pac/**    →  /api/pac/v1/**         PAC
/api/v1/evnt/**   →  /api/v1/**             EVNT  (events, reports)
/api/v1/dash/**   →  /panel/**              DASH
/api/v1/gest/...  →  /gest-service/...      GEST  (allow-list, ver abaixo)
```

Duas regras decorrentes, que fazem parte da decisão:

**1. Endpoints de serviço-a-serviço não são publicados.** Um path só ganha rota no GWY se for
destinado a cliente externo. `/internal/**`, `/edge-service/**`, `/evnt-service/**` e os
endpoints internos do GEST permanecem inalcançáveis pela borda. O `RewritePath` funciona como
allow-list: `/api/v1/dash/**` → `/panel/**` torna `/internal/notifications` inatingível de fora
por construção.

**2. Onde a superfície interna é ampla, o predicado é por endpoint, não wildcard.** O
`gest-service` não tem superfície pública nenhuma — os 4 mappings estão todos na convenção
interna. O frontend consome dele exatamente dois (`eligibility` e `tipo-gesto`), então só esses
dois ganham rota. Um `/api/v1/gest/**` genérico publicaria também `detection-profile` (consumido
pelo EDGE) e `detection-contract` (interno), o que anularia a regra 1.

O contrato público passa a ser propriedade do **gateway**, não do controller.

Implementação e critérios de verificação: `E2E-014`.

## Consequências

**Positivas**

- Superfície pública uniforme e previsível: um cliente aprende `/api/v1/<svc>/...` uma vez.
- O `proxy.conf.json` do frontend cai de 9 prefixos para 2 (`/api/v1/**`, `/oauth2/**`),
  eliminando uma fonte recorrente de erro de configuração.
- **Desacoplamento:** refatorar o path de um controller deixa de ser uma quebra de cliente — o
  gateway absorve a mudança no rewrite. Sem isso, o path interno de cada serviço é contrato
  público permanente.
- Superfície interna deixa de ser publicável por descuido; a exposição passa a ser um ato
  explícito (uma rota nova), não um efeito colateral de um wildcard.
- Consistente com ADR-002 (GWY local): comportamento por rota como configuração declarativa.
- Reversível: rewrites são configuração, não código.

**Negativas / trade-offs aceitos**

- **Indireção.** O path que o cliente chama não é o path que o controller declara. Ler o
  `@RequestMapping` deixa de ser suficiente para saber a URL pública — é preciso ler também as
  rotas do GWY. Mitigação: a tabela da seção Decisão é a referência única, e os paths públicos
  são documentados em `standards/services-catalog.md`.
- **Ponto único de erro de configuração.** Um rewrite errado quebra um serviço inteiro na borda
  sem que nada no serviço mude. Mitigação: `E2E-014` exige verificação em endpoint de negócio
  (`≠ 404`) por serviço — `/actuator/health` não conta, por usar outro predicado.
- **O frontend muda 6 constantes de base path** (PAC ×2, EVNT, DASH ×2, GEST). Sem mudança de
  lógica; rastreado em `FE-E2E-001`, `FE-E2E-004` e `FE-E2E-006`.
- **Divergência de perfil não resolvida aqui.** Hoje as rotas vivem em `application-docker.yml`
  e o `application.yml` base declara só `edge-service-route`, então o GWY fora do compose roteia
  diferente do GWY dentro dele. Consolidar é escopo de `E2E-014`.
- Não normalizamos os `@RequestMapping` dos serviços. A inconsistência interna permanece — foi
  aceita para manter o raio de mudança na borda e não tocar em 7 serviços já `PRODUCTION-READY`.
  Uma normalização interna, se desejada depois, fica livre para acontecer justamente por causa do
  desacoplamento que este ADR cria.

## Relacionados

- `ADR-002` (compartilhado) — microsserviços independentes roteados pelo GWY.
- `ADR-002` (local do GWY) — gateway único, comportamento por rota como filtro declarativo.
- `E2E-001` — habilitou o roteamento base e a descoberta no Eureka.
- `E2E-014` — implementa esta decisão e define os critérios de verificação.
- `plan-FE-E2E.md` — registra o impacto no frontend como G8 / pré-condição da P2.
