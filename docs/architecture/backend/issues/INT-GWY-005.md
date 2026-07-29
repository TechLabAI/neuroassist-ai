# INT-GWY-005 -- Integration with external LB/DNS health probe (public liveness)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto
O LB externo/DNS faz probe de liveness via `/actuator/health`; esse endpoint deve ser publico e nao exigir autenticacao, sob risco do LB remover o gateway de servico por falso-negativo.

**Spec de referencia:** SPEC-10 (integracao #5), SPEC-01 §4

## Escopo
- [ ] implementar `backend/services/neuroassist-ai-api-gateway/src/main/resources/application.yml`

## Resultado esperado
`/actuator/health` acessivel sem autenticacao e responde `200 {status:UP}` sempre que o processo esta de pe (liveness, independente de readiness).

## Criterios de aceite
- [ ] `GET /actuator/health` responde `200` sem qualquer credencial
- [ ] `/actuator/health` reflete apenas liveness do processo, distinto de `/actuator/health/readiness` (GWY-017)

## Dependencias
GWY-017 (precede).

## Estimativa
S

## Prioridade
P7
