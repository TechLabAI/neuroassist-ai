# INT-GWY-001 -- Integration with Service Registry (Eureka) discovery + readiness DOWN fallback

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto
Toda resolucao de rota do gateway depende do Service Registry (Eureka); esta issue cobre especificamente o contrato de integracao e o comportamento de falha (cache local, readiness DOWN, `503` sem instancia saudavel).

**Spec de referencia:** SPEC-10 (integracao #1)

## Escopo
- [ ] implementar `backend/services/neuroassist-ai-api-gateway/src/main/java/com/neuroassist/gateway/health/EurekaRegistrationHealthIndicator.java`

## Resultado esperado
Integracao com Eureka validada fim-a-fim: registro, descoberta de instancias saudaveis, e degradacao correta quando o registry fica vazio/indisponivel.

## Criterios de aceite
- [ ] nenhuma instancia saudavel registrada para um alvo resulta em `503 /errors/upstream-unavailable`
- [ ] readiness fica `DOWN` enquanto o gateway nao estiver registrado no Eureka

## Dependencias
GWY-011 (precede).

## Estimativa
S

## Prioridade
P4
