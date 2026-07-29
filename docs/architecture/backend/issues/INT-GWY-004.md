# INT-GWY-004 -- Integration routing to downstream services via lb:// with 502/503/504 mapping

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
Roteamento para os servicos de dominio (EVNT/PAC/ACS/HIST/DASH/EDGE) e sempre via `lb://` (INV-4); esta issue cobre o contrato de falha (timeout, 5xx) mapeado para o error model RFC 7807.

**Spec de referencia:** SPEC-10 (integracao #4)

## Escopo
- [ ] implementar `backend/services/neuroassist-ai-api-gateway/src/main/java/com/neuroassist/gateway/filter/ProblemDetailErrorFilter.java`
- [ ] implementar `backend/services/neuroassist-ai-api-gateway/src/main/java/com/neuroassist/gateway/config/RouteConfig.java`

## Resultado esperado
Integracao de roteamento validada fim-a-fim para os 6 servicos downstream, com mapeamento correto de timeout/5xx para 502/503/504.

## Criterios de aceite
- [ ] timeout de upstream resulta em `504` sem retry automatico de metodo nao-idempotente
- [ ] `5xx` nao tratado do upstream e mapeado para `502` sem vazar o corpo original

## Dependencias
GWY-002, GWY-003, GWY-016 (precedem).

## Estimativa
S

## Prioridade
P4
