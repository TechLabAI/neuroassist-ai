## [FE-EVNT-009] DTOs e mapper EventoDto → EventoVm

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/data-access/src/lib/evnt.dtos.ts` define os wire DTOs (`EventoDto`, `ActiveEventsDto`, `EventoSummaryDto`, `ConfirmationRequestDto`, `ConfirmationResponseDto`) e `libs/evnt/data-access/src/lib/evnt.mapper.ts` provê funções puras de mapeamento para `EventoVm` — sem fabricação de dados.

**Context / Problem statement:**
- SPEC-04 §2 lista os DTOs exatos espelhando o BE api-contract §4.
- `patientName` não é fabricado — se não existir no payload, cai back para o `pacienteId`.
- `displayTime` é derivado de `recognizedAt` em formato `"às 14h32"` pt-BR.
- O mapper é testável isoladamente (funções puras, sem DI).
- Bloqueia: FE-EVNT-008 (Facade usa o mapper internamente).

**Approach (execution path):**
1) Criar `libs/evnt/data-access/src/lib/evnt.dtos.ts` com todas as interfaces de SPEC-04 §2.
2) Criar `libs/evnt/data-access/src/lib/evnt.mapper.ts` com:
   - `toEventoVm(dto: EventoDto | EventoSummaryDto): EventoVm`
   - `formatDisplayTime(iso: string): string` — `"às 14h32"` usando `Intl.DateTimeFormat` com locale `pt-BR`
   - `toTier(urgencia: Urgencia): Tier` — mapeamento determinístico
3) `patientName`: se não presente no DTO, usar `dto.pacienteId`.
4) Exportar tudo via `libs/evnt/data-access/src/index.ts`.

**Non-goals:**
- Lógica de negócio (quem pode confirmar — computed no Facade/SPEC-03 §4).
- Tradução de mensagens de erro (INT-FE-EVNT-006).

**Layer(s):**
frontend / libs/evnt/data-access

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-04-data-access.md` (§2)
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-03-view-models.md` (§1)

**Architecture alignment:**
- Funções puras — sem side-effects, sem DI
- Tag Nx: `scope:evnt`, `type:data-access`

**Implementation checklist (by file):**

**Lib data-access**
- [ ] `libs/evnt/data-access/src/lib/evnt.dtos.ts` — `EventoDto`, `ActiveEventsDto`, `EventoSummaryDto`, `ConfirmationRequestDto`, `ConfirmationResponseDto`
- [ ] `libs/evnt/data-access/src/lib/evnt.mapper.ts` — `toEventoVm`, `formatDisplayTime`, `toTier`
- [ ] `formatDisplayTime`: locale `pt-BR`, formato `"às HH:mm"` (ex: "às 14h32")
- [ ] `patientName`: fallback para `pacienteId` se `patientName` ausente no DTO
- [ ] `libs/evnt/data-access/src/index.ts` re-exporta DTOs e mapper

**Tests**
- [ ] `nx test evnt-data-access` PASS
- [ ] Unit: `formatDisplayTime('2026-07-28T14:32:00Z')` → `"às 14h32"` (ajustar timezone conforme UTC/local)
- [ ] Unit: `toEventoVm` com `patientName` ausente → usa `pacienteId`
- [ ] Unit: `toTier('emergencia')` → `'full_screen_alarm'`
- [ ] `nx lint evnt-data-access` PASS

**Milestone:**
Wave P2 / Data access

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P2

**Acceptance criteria:**
- `npx nx test evnt-data-access` PASS
- `npx nx lint evnt-data-access` PASS
- Todos os campos de `EventoVm` preenchidos sem `undefined` após mapeamento de DTO válido

**Dependencies:**
- (nenhuma de outras issues FE — apenas tipos shared do projeto)

**Data impact:**
- Nenhum

**API / Contract impact:**
- DTOs espelham BE api-contract §4 — qualquer mudança no BE quebra este arquivo

**Testing / Evidence required:**
- Unit: fixture de cada DTO → `toEventoVm` → snapshot do `EventoVm` resultante

**Risks / Notes:**
- `displayTime` depende de timezone: BE envia UTC ISO-8601 → exibir em horário local do browser (Intl.DateTimeFormat com `timeZone: undefined`).
