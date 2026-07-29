## [FE-EVNT-006] EVNT_NAV_CONFIG — menu e breadcrumbs do módulo

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/navigation/src/lib/evnt.nav-config.ts` define `EVNT_NAV_CONFIG` registrando a entrada "Eventos" no menu (order 20) e os breadcrumbs para `/eventos` → `[Eventos]` e `/eventos/:eventoId` → `[Eventos > {rótulo} ({hora})]`. O AlarmOutlet não contribui breadcrumb.

**Context / Problem statement:**
- SPEC-06 §4 especifica que `EVNT_NAV_CONFIG` é registrado pelo bootstrap e define menu + breadcrumbs.
- NAV-01 gap (M3.5): navegação para `/eventos` → renderizar feed de rotina (breadcrumb `[Eventos]`).
- NAV-02 gap (M3.5): navegação para `/eventos/:eventoId` → renderizar detalhe (breadcrumb `[Eventos > {rótulo} ({hora})]`).
- O `AlarmOutlet` NÃO contribui breadcrumb (é interrupt, não localização).

**Approach (execution path):**
1) Criar `libs/evnt/navigation/src/lib/evnt.nav-config.ts` com `EVNT_NAV_CONFIG`.
2) Configurar entrada de menu: label `'Eventos'`, icon `notifications_active`, route `/eventos`, order 20.
3) Configurar breadcrumb para `/eventos`: `[{ label: 'Eventos', route: '/eventos' }]`.
4) Configurar breadcrumb dinâmico para `/eventos/:eventoId`: `[{ label: 'Eventos', route: '/eventos' }, { label: '{rótulo} ({hora})' }]` (resolvido pelo componente via `EventDetailPageComponent`).
5) Exportar via `libs/evnt/navigation/src/index.ts` e incluir em `@neuroassist/evnt` barrel.

**Non-goals:**
- Breadcrumb do AlarmOutlet (não existe — interrupt).
- Implementação do serviço de navegação global (shared).

**Layer(s):**
frontend / libs/evnt/navigation

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-06-shell-integration.md` (§4)
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-01-route-map.md` (§2 — Breadcrumb column)

**Architecture alignment:**
- Tag Nx: `scope:evnt`, `type:domain`
- Nenhuma dependência de feature libs (direção de dependência: shell-entry → navigation → nada de feature)

**Implementation checklist (by file):**

**Lib navigation**
- [ ] `libs/evnt/navigation/src/lib/evnt.nav-config.ts` — `EVNT_NAV_CONFIG` exportado
- [ ] Menu entry: `label: 'Eventos'`, `icon: 'notifications_active'`, `route: '/eventos'`, `order: 20`
- [ ] Breadcrumb `/eventos` → `[Eventos]` (NAV-01)
- [ ] Breadcrumb `/eventos/:eventoId` → `[Eventos > {rótulo} ({hora})]` dinâmico (NAV-02)
- [ ] AlarmOutlet sem entrada de breadcrumb / menu
- [ ] `libs/evnt/navigation/src/index.ts` re-exporta `EVNT_NAV_CONFIG`

**Tests**
- [ ] `nx test evnt-navigation` PASS
- [ ] `nx lint evnt-navigation` PASS

**Milestone:**
Wave P1 / Foundation

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P1

**Acceptance criteria:**
- `npx nx test evnt-navigation` PASS
- `npx nx lint evnt-navigation` PASS
- `npx nx build evnt-navigation` PASS
- Menu Shell exibe "Eventos" na posição order 20
- Breadcrumb de detalhe exibe rótulo + hora do evento

**Dependencies:**
- FE-EVNT-001 (EVNT_MENU_METADATA — valores de referência)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Unit: `EVNT_NAV_CONFIG.menu.order === 20`
- Unit: breadcrumb detail tem dois segmentos (Eventos + rótulo)

**Risks / Notes:**
- NAV-01/NAV-02 gaps (M3.5): estes gaps eram de cobertura de cenário; a configuração de nav é simples, mas o rótulo dinâmico do breadcrumb de detalhe precisa de integração com o `EventDetailPageComponent` (FE-EVNT-016).
