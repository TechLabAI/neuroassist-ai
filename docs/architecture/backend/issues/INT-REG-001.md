# INT-REG-001 -- Validar integracao de registro com clientes in-fleet: GWY, PAC, EVNT

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto
REG e o alvo de dependencia da plataforma (hub-integration.md §2-3). Os clientes Eureka de todos os servicos registram-se em REG; o GWY e o mais critico pois resolve todos os targets `lb://` a partir do registry. Este issue valida a integracao de registro/discovery com o subconjunto MVP primario (GWY, pac-service, evnt-service) antes do rollout completo da frota.

## Spec de referencia
- SPEC-01, SPEC-02, SPEC-10, contracts/integration-contracts.md

## Escopo
- [ ] Configurar `eureka.client.serviceUrl.defaultZone=http://localhost:8761/eureka/` nos profiles locais de `neuroassist-ai-api-gateway`, `pac-service` e `evnt-service`
- [ ] Verificar que apos startup de cada servico o logical id correspondente aparece no snapshot `GET /eureka/apps` de REG
- [ ] Verificar que GWY resolve `lb://PAC-SERVICE` e `lb://EVNT-SERVICE` via REG (discovery funcional para rotas PAC e EVNT)
- [ ] Documentar o startup order padrao local: REG -> outros servicos (com `depends_on` no compose ou instrucao no README)

## Criterios de aceite
- [ ] `GET /eureka/apps` apos startup dos 3 servicos retorna 3 instancias em status UP
- [ ] GWY encaminha rotas de PAC/EVNT usando discovery (nao URL estatica)
- [ ] Restart de REG seguido de re-registro dos clientes restaura o registry sem intervencao manual

## Dependencias
- REG-009 (REG healthcheck no compose), INT-GWY-001 (GWY registra em REG)

## Estimativa
M

## Prioridade
P2
