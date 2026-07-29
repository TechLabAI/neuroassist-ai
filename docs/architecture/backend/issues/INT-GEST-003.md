# INT-GEST-003 -- Integration with GWY — upstream route /api/v1/gest/** + X-Correlation-Id forwarding

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto
O GWY roteia `/api/v1/gest/**` para `gest-service` via `lb://gest-service` (descoberta Eureka). O GWY pre-valida o JWT e forwarda o token; GEST re-valida independentemente. `X-Correlation-Id` e gerado no GWY se ausente e forwarded — GEST apenas o ecoa na resposta. Nenhuma configuracao especifica de rate-limit para GEST no MVP (politica padrao GWY).

## Spec de referencia
- hub-integration.md §1, api-contract.md §1.1, SPEC-01

## Escopo
- [ ] verificar que rota `lb://gest-service` esta configurada em GWY (GWY-002 ou equivalente) apontando para `/api/v1/gest/**`
- [ ] validar que `X-Correlation-Id` gerado pelo GWY chega em `gest-service` e aparece no log MDC e na resposta
- [ ] validar que token pre-validado pelo GWY e novamente validado pelo SecurityConfig do GEST (defense-in-depth)

## Resultado esperado
Chamada via GWY para `/api/v1/gest/tipos-gesto` com token valido retorna 200 com `X-Correlation-Id` no response header.

## Criterios de aceite
- [ ] `GET /api/v1/gest/tipos-gesto` via GWY retorna `200` com `X-Correlation-Id` no response
- [ ] `X-Correlation-Id` forwarded pelo GWY e o mesmo ecoado pelo GEST na resposta
- [ ] request direto ao GEST (sem GWY) com token valido tambem funciona (GEST nao depende do GWY para autenticar)

## Dependencias
GEST-005, GEST-017, GEST-018.

## Estimativa
S

## Prioridade
P4
