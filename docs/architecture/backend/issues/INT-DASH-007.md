# INT-DASH-007 -- GWY routing for dash-service: expose /panel/** publicly, block /internal/** at edge

## Contexto

O `neuroassist-ai-api-gateway` (GWY) deve expor as rotas publicas do DASH (`/panel/**`) ao browser
e bloquear as rotas service-to-service (`/internal/**`) na borda. Sem esta configuracao, o browser
poderia chamar `POST /internal/notifications` diretamente (bypass de auth). Este issue configura as
rotas no GWY para o `dash-service`.

## Spec de referencia

- SPEC-01 (separacao de superficie: /panel/** publica, /internal/** service-to-service)
- SPEC-06 (invariant: browser nao alcanca /internal/** via GWY)
- `contracts/integration-contracts.md §6` (GWY/REG: rotas e discovery)

## Escopo

- Configuracao de rotas no `neuroassist-ai-api-gateway` (GWY): `lb://dash-service /panel/**` → rota publica; `/internal/**` nao publicada (nenhuma rota GWY ou rota bloqueada por predicado)
- Registro no REG (Eureka): `dash-service` se registra com `spring.application.name=dash-service`; GWY descobre via `lb://dash-service`
- Teste de fumo: `curl http://localhost:8080/panel/summary` com JWT → roteado ao DASH; `curl http://localhost:8080/internal/notifications` → `404` (rota nao exposta) ou `403`

## Resultado esperado

`/panel/**` acessivel ao browser via GWY; `/internal/**` inacessivel via GWY (somente service-to-service direto).

## Criterios de aceite

- [ ] GWY roteia `GET /panel/summary` para `dash-service` (200 com JWT valido)
- [ ] GWY retorna `404` (ou `403`) para qualquer `POST /internal/**` originado externamente
- [ ] `dash-service` aparece registrado no Eureka (`GET http://localhost:8761/eureka/apps/DASH-SERVICE`)
- [ ] DASH sem REG (Eureka down no startup) → log de aviso, nao crash (Eureka = soft-dependency de discovery)

## Dependencias

- DASH-001 (Eureka client configurado no dash-service)
- GWY-001..GWY-003 (rotas GWY base)

## Estimativa

S

## Prioridade

P6
