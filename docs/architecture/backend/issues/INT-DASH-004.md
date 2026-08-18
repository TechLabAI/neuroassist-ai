# INT-DASH-004 -- DASH -> HIST history read: pagination forwarding, size cap=100, hard-503 on HIST unavailable

## Contexto

O DASH encaminha a query de historico ao HIST com filtros e paginacao inalterados, apos validar
escopo e aplicar cap de `size=100`. Este issue valida o contrato de chamada DASH→HIST do ponto
de vista de integracao.

## Spec de referencia

- SPEC-10 (`HistoryQueryPort`, upstream: `hist-service`)
- `contracts/integration-contracts.md §4` (HIST: filtros repassados inalterados, size cap 100, hard-503)

## Escopo

- WireMock HIST retornando pagina de historico → verificar que DASH repassa `type`, `actor`, `from`, `to`, `page`, `size` (apos cap) na query string
- Cenario: request com `size=200` → DASH chama HIST com `size=100` (cap silencioso)
- WireMock HIST down → DASH retorna `503 upstream-unavailable` (sem degrade util)
- Verificar que `patientId` fora do escopo nao chega ao HIST (bloqueado pelo DASH antes da chamada)

## Resultado esperado

HIST recebe todos os filtros corretos; indisponibilidade do HIST sempre resulta em 503 para o cuidador.

## Criterios de aceite

- [ ] Filtros repassados ao HIST sem transformacao logica
- [ ] `size` capado a 100 antes da chamada (HIST recebe no maximo 100)
- [ ] HIST down → `503`
- [ ] `patientId` fora do escopo: DASH nao chama HIST (bloqueia antes)

## Dependencias

- DASH-009 (history endpoint)
- DASH-019 (HistRestAdapter)

## Estimativa

S

## Prioridade

P3
