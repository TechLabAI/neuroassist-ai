# INT-REG-003 -- Validar registro da frota completa: todos os 9 servicos em membership

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
hub-integration.md §3 lista os 9 clientes Eureka da plataforma: gest-service, evnt-service, pac-service, acs-service, hist-service, dash-service, edge-service, neuroassist-ai-api-gateway e neuroassist-ai-migrations. Este issue valida que todos os 9 registram-se corretamente no REG e que o GWY resolve todos os `lb://` targets necessarios para suas rotas.

## Spec de referencia
- SPEC-02, SPEC-10, contracts/hub-integration.md

## Escopo
- [ ] Subir todos os 9 servicos (ou stubs/mocks) com `eureka.client.serviceUrl.defaultZone` apontando para REG
- [ ] Verificar que `GET /eureka/apps` retorna os 9 logical ids com pelo menos 1 instancia UP cada
- [ ] Verificar que o dashboard REG em `/` exibe os 9 servicos registrados

## Criterios de aceite
- [ ] `GET /eureka/apps` JSON contem `application` entries para todos os 9 logical ids
- [ ] Dashboard em `http://localhost:8761/` exibe "9 instances" ou equivalente
- [ ] Nenhum servico reporta erro de connection refused ao REG no log de startup

## Dependencias
- INT-REG-001 (validacao MVP primario), todos os scaffolds dos modulos GEST/EVNT/PAC/ACS/HIST/DASH/EDGE/GWY/MIG

## Estimativa
L

## Prioridade
P4
