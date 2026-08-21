## [FE-HIST-007] hist data-access: domain types e DTO→VM mapper

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Tipos de domínio (`Period`, `Verdict`, `Urgency`, `ScreenStatus`, `MarkingState`) e `HistoryItemVm` definidos; função `mapAuditRecordToVm` implementada como único ponto de adaptação DTO→VM.

**Context / Problem statement:**
- SPEC-03 §1/§2: tipos de domínio e `HistoryItemVm`; SPEC-04 §3: tabela de mapeamento DTO→VM.
- `label` = `payload.rotulo` (humano-legível; fallback derivado, nunca o `factType` bruto).
- `urgency` mapeada a enum; `occurredAtLabel` localizado no locale do tenant.
- `confirmationLine` composta de `actor.displayNameSnapshot` + `payload`.
- `correctsRecordId` passthrough (ADR-004: ligação de registros corretivos).
- Bloqueia: FE-HIST-008 (Facade usa o mapper).

**Approach (execution path):**
1. `hist.domain.ts` — exportar todos os tipos de domínio.
2. `hist-item-vm.mapper.ts` — `mapAuditRecordToVm(dto: AuditRecordResponse, locale: string): HistoryItemVm`.
3. `occurredAtLabel`: formatar ISO com `Intl.DateTimeFormat` no locale do tenant (`14h32`).
4. `label` fallback: se `payload.rotulo` ausente, derivar texto amigável (nunca o código técnico).
5. Unit tests cobrindo todos os campos + fallback de `label` + `correctsRecordId`.

**Non-goals:**
- Não implementar joins client-side PAC/ACS aqui (fallback doc; servidos pelo read model).

**Layer(s):**
frontend / data-access

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-03-state-contracts.md` (§1, §2)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-04-api-integration.md` (§3)

**Architecture alignment:**
- Tag Nx: `scope:hist`, `type:data-access`

**Implementation checklist (by file):**
- 🔲 `libs/hist/data-access/src/lib/hist.domain.ts` (Period, Verdict, Urgency, ScreenStatus, MarkingState, HistoryItemVm, ItemMarking, HistoryScreenVm)
- 🔲 `libs/hist/data-access/src/lib/hist-item-vm.mapper.ts`
- 🔲 `libs/hist/data-access/src/lib/hist-item-vm.mapper.spec.ts`
- 🔲 Atualizar `src/index.ts`
- 🔲 `nx test hist-data-access` PASS
- 🔲 `nx lint hist-data-access` PASS

**Milestone:** Wave P2 / Data Access

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P2

**Acceptance criteria:**
- `mapAuditRecordToVm` retorna `label` humano-legível em todos os casos de teste.
- `correctsRecordId` passthrough correto.
- `occurredAtLabel` formata corretamente no locale `pt-BR`.
- Nenhum campo expõe `factType` como `label` primário.

**Dependencies:**
- FE-HIST-005
