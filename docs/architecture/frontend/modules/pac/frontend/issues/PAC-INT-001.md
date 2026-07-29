## [PAC-INT-001] EDGE device-ack contract stub — syncState honest-pending facade wiring (ADR-005)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/data-access/src/lib/ports/edge-device-ack.port.ts` define a interface `EdgeDeviceAckPort` e `libs/pac/data-access/src/lib/adapters/edge-device-ack-stub.adapter.ts` implementa o stub que sempre retorna `syncState = 'pending'`, garantindo o honest state ADR-005 enquanto o contrato real EDGE não existe.

**Context / Problem statement:**
- ADR-005 (FE) e backend-api-cross-link.md §5: "EDGE device-ack signal (que versão do catálogo o edge já puxou) não está contratado. O FE mantém comandos em 'pendente de sincronização' — estado honesto, não erro."
- O `PacComandoFacade` (PAC-FE-011) deriva `syncState` por `catalog ⋈ EdgeDeviceAckPort.getAckVersion()`. Enquanto o port não tiver implementação real, o stub retorna versão `-1` (menor que qualquer catálogo), garantindo `syncState = 'pending'` para todos os comandos.
- Quando o EDGE service implementar o contrato, apenas o adapter é substituído — nenhuma mudança no facade.
- Flag `pac.deferredEdgeSync=off` colapsa o badge no componente (PAC-FE-001/019); mas internamente o signal permanece `'pending'`.

**Approach (execution path):**
1. Criar `edge-device-ack.port.ts`: `interface EdgeDeviceAckPort { getAckVersion(pacienteId: string): Observable<number> }`.
2. Criar `edge-device-ack-stub.adapter.ts`: `getAckVersion() { return of(-1); }` (versão -1 garante pending para qualquer catálogo).
3. Registrar o stub via `provide: EdgeDeviceAckPort, useClass: EdgeDeviceAckStubAdapter` em `pac.providers.ts` (INT-FE-PAC-004) como padrão até substituição futura.
4. Atualizar `PacComandoFacade` (PAC-FE-011) para injetar `EdgeDeviceAckPort` e derivar `syncState` a partir do `getAckVersion()` vs `comando.calibracaoVersao`.
5. Unit test: com stub → `syncState === 'pending'` para qualquer command row.

**Non-goals:**
- Sem polling, WebSocket ou SSE para device-ack no MVP.
- O adapter real (EDGE service HTTP) é implementado quando o `EdgeDevicePort.confirmSync` contract existir (HIST module ou EDGE module).

**Layer(s):**
frontend / libs/pac/data-access (port + adapter)

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/contracts/backend-api-cross-link.md` (§5 Known gap)
- `docs/architecture/frontend/modules/pac/frontend/adr/ADR-005-command-edit-not-live-until-synced.md`

**Architecture alignment:**
- Port/Adapter pattern (hexagonal FE)
- Tag Nx: `scope:pac`, `type:data-access`

**Implementation checklist (by file):**

**Lib: libs/pac/data-access**
- [ ] `libs/pac/data-access/src/lib/ports/edge-device-ack.port.ts`
- [ ] `libs/pac/data-access/src/lib/adapters/edge-device-ack-stub.adapter.ts`
- [ ] `libs/pac/data-access/src/lib/adapters/edge-device-ack-stub.adapter.spec.ts`

**Tests**
- [ ] `npx nx test pac-data-access` PASS
- [ ] `npx nx lint pac-data-access` PASS

**Milestone:**
Wave 4 / P4 Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:pac
priority:P4

**Acceptance criteria:**
- `npx nx test pac-data-access` PASS: stub `getAckVersion('any-pid')` → `Observable<-1>`.
- `PacComandoFacade` com stub: todo `ComandoRowVM.syncState === 'pending'` (nenhuma row 'synced').
- `npx nx build pac-data-access` PASS.

**Dependencies:**
- PAC-FE-011 (PacComandoFacade — ponto de injeção)
- INT-FE-PAC-004 (pac.providers.ts — registro do adapter)

**Data impact:**
Nenhum.

**API / Contract impact:**
- Gap rastreado: `EdgeDevicePort.confirmSync` não existe no MVP. Este stub é a mitigation explícita.

**Testing / Evidence required:**
- Unit: `getAckVersion` → `-1`; facade com stub → todas rows `pending`.

**Risks / Notes:**
- CRITICAL GAP (ADR-005): quando o adapter real for implementado, certificar que `EdgeDeviceAckPort.getAckVersion` retorna a versão do catálogo que o device sincronizou (não apenas "online/offline"). O sync é por versão de catálogo, não por dispositivo ativo.
