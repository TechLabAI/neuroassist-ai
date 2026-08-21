## [INT-FE-HIST-004] Integração: hist-service GET /fact-types (vocabulário controlado)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`HistClient.listFactTypes` integrado; vocabulário de fact-types disponível para filtro opcional por facet; cache session-static implementado.

**Context / Problem statement:**
- Backend: `GET /api/v1/hist/fact-types` → `FactType[]` (api-contract §5, scope `hist:read`).
- Cache: session-static — buscar uma vez por sessão (SPEC-04 §1).
- Usado como facet opcional de filtro (`factType` param na query de history).
- MVP: filtro por factType não exposto na UI (A1 "sem custom date-range ou factType picker no MVP") — mas o vocabulary precisa estar disponível para a Facade.

**Approach (execution path):**
1. `HistClient.listFactTypes()` → `Observable<FactType[]>`.
2. Facade armazena vocabulary em signal `#factTypes`; carrega na inicialização (lazy).
3. Cache: `shareReplay(1)` ou signal setado uma vez por sessão.
4. Unit test: verifica que segundo call usa cache (sem segundo HTTP request).

**Non-goals:**
- Não expor picker de factType na UI do MVP.

**Layer(s):**
frontend / data-access (integration)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-04-api-integration.md` (§1)
- `docs/architecture/frontend/modules/hist/contracts/backend-api-cross-link.md` (§1)

**Implementation checklist (by file):**
- 🔲 `HistClient.listFactTypes` implementado
- 🔲 Facade: `#factTypes` signal com cache session-static
- 🔲 Unit test: cache hit (sem segundo request)
- 🔲 `nx test hist-data-access` PASS

**Milestone:** Wave P4 / Hub Integrations

**Labels:**
type:integration · layer:frontend · scope:hist · priority:P4

**Acceptance criteria:**
- `listFactTypes()` busca apenas uma vez por sessão (cache verificado em test).
- Vocabulary disponível na Facade para uso futuro.

**Dependencies:**
- FE-HIST-005
