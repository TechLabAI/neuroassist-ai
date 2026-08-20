# INT-HIST-001 -- Inbound contract tests: producer stubs (EVNT/PAC/ACS/EDGE → POST /audit-facts)

## Contexto

Os produtores EVNT, PAC, ACS e EDGE submetem audit facts ao HIST via `POST /api/v1/hist/audit-facts`.
Este issue cria testes de contrato inbound que simulam cada produtor com os `factType` que
lhe pertencem, verificando que o HIST aceita o contrato publicado. Ferramentas: MockMvc +
WireMock (stubs de resposta do HIST para os produtores) ou Spring Contract Verifier (opcional).

## Spec de referencia

- SPEC-01 (superfície de ingestão)
- SPEC-10 (integração inbound: produtores + scopes)
- `contracts/integration-contracts.md §1`
- `contracts/api-contract.md §2, §5`

## Escopo

**`src/test/kotlin/com/neuroassist/hist/integration/inbound/`:**
- `EvntProducerContractTest`: POST com factTypes `evento.confirmado`, `evento.descartado`, `evento.feedback.registrado` + scope `hist:write` → 201 ou 200.
- `PacProducerContractTest`: POST com factTypes `comando.sensibilidade.editada`, `comando.recalibrado` → 201.
- `AcsProducerContractTest`: POST com factTypes `acesso.concedido`, `acesso.revogado` → 201.
- `EdgeProducerContractTest`: POST com factTypes `dispositivo.pareado`, `dispositivo.despareado` → 201.
- Cenário negativo compartilhado: POST sem `hist:write` → 403.
- Cenário negativo: POST com `factType` não pertencente ao produtor (e.g. EVNT tentando `acesso.concedido`) → 422 (vocabulário é validado, mas não restringe por produtor no MVP — documentar como nota aberta).

## Resultado esperado

Todos os testes de contrato inbound passam sem acesso ao código dos produtores (isolado via
JWT mock com scope `hist:write` e factType fixo por cenário).

## Criterios de aceite

- [ ] Um teste por produtor (4 testes), cada um usando o JWT correto com `hist:write`.
- [ ] `factType` de cada produtor é aceito com 201.
- [ ] POST sem `hist:write` → 403 em todos os testes.
- [ ] Nota documentada: restrição de factType por produtor não é enforcement no MVP (open gap).
- [ ] Testes rodam isolados do código dos produtores (sem dependência de EVNT/PAC/ACS/EDGE).

## Dependencias

- HIST-005 (POST /audit-facts endpoint funcional)
- HIST-014 (auth configurada para JWT mock)
- HIST-023 (vocabulário de factType configurado)

## Estimativa

M

## Prioridade

P4
