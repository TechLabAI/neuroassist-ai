# ADR-005 — Server-driven calm home summary + shell-level cross-module composition

- **Status:** proposed (2026-08-17)
- **Escopo:** module-local (DASH)
- **Relaciona-se com:** ADR-006 (module entry as library), `nx-boundaries.md`,
  EVNT ADR-005 (global shell outlet), INT-FE-EVNT-001 (`ShellOutletRegistry`)

## Contexto

DASH é a **raiz de composição** do painel: a Home (Tela 01) precisa responder "O Márcio
está bem?" combinando dados de vários módulos — o `activeEvent` (EVNT), o status do
dispositivo (EDGE), o resumo do dia (HIST/`dash-service`) — e o shell (sidebar/topbar)
precisa hospedar o alarme/toast que o EVNT renderiza. Isso cria duas tensões:

1. **Boundary.** Se `scope:dash` importasse `scope:evnt`/`scope:edge`/`scope:hist` para
   montar a Home, violaria `@nx/enforce-module-boundaries` (módulo não importa módulo).
2. **Latência/estabilidade.** Fazer *fan-out* client-side para N APIs de módulo na abertura
   do painel ameaça o alvo de ≤ 2 s e acopla a Home a N contratos instáveis.

## Decisão

**(a) Home calma via agregado do `dash-service`.** A Home lê **um** endpoint
`GET /dashboard/summary` (paciente ativo, última verificação, sincronização, dica de
estado calmo) servido pelo `dash-service`, que faz a agregação **server-side**. DASH-FE
depende de **um** contrato estável, não dos contratos de EVNT/EDGE/HIST.

**(b) Verdade "ao vivo" fica com o EVNT.** O estado calmo/alarme em tempo real **não** vem
do agregado: deriva do sinal `activeEvent` (EVNT, SSE/poll). A Home computa
`calm = !(activeEvent é emergencia não confirmada)`.

**(c) Composição cross-module no `scope:web`.** O wiring dos sinais de outros módulos
(`activeEvent` do EVNT, badge do EDGE) na topbar/Home acontece **no app shell**
(`neuroassist-ai-web`, `scope:web`, que pode importar tudo) e é **injetado** em DASH via
tokens/inputs. `scope:dash` nunca importa outro `scope:<módulo>`.

**(d) Host de outlets globais.** DASH declara a **região de outlet global** no
`DASH_MODULE_DESCRIPTOR`; o EVNT registra o alarme/toast nela via `ShellOutletRegistry`
(`libs/shared/ui-components`). DASH provê o host; EVNT provê o conteúdo.

## Alternativas consideradas

1. **Fan-out client-side (DASH chama EVNT/EDGE/HIST direto).** Rejeitada: viola boundaries,
   acopla a N contratos, pior latência, e força `scope:dash` a conhecer outros módulos.
2. **DASH importa as libs dos outros módulos para compor a Home.** Rejeitada: violação dura
   de boundary; transforma DASH num "módulo guarda-chuva".
3. **Home derivar o estado calmo apenas do agregado (sem `activeEvent`).** Rejeitada por
   segurança: o agregado pode estar defasado; o alarme precisa da verdade em tempo real do
   EVNT — falso "está bem" é o pior modo de falha do produto.

## Consequências

- **Positivas:** boundaries respeitadas; Home rápida e acoplada a um só contrato; verdade de
  alarme em tempo real preservada (fail-safe); shell e conteúdo de outlet desacoplados;
  composição testável no `scope:web`.
- **Negativas:** exige o `dash-service` (BE) prover o agregado (hoje inexistente — ver Gaps
  §2); a lógica de composição vive no `scope:web`, exigindo disciplina de teste ali; dois
  caminhos de verdade (agregado calmo + `activeEvent` ao vivo) precisam de contrato claro de
  precedência (o vivo sempre vence).

## Referências

- `docs/architecture/frontend/modules/dash/frontend/02-architecture-blueprint.md` §4
- `docs/architecture/frontend/modules/dash/frontend/05-state-and-data-flow.md` §1
- `docs/architecture/frontend/standards/nx-boundaries.md`
- `docs/architecture/frontend/adr/ADR-006-module-entry-as-library.md`
- EVNT: `libs/evnt` global shell outlet (INT-FE-EVNT-001)
