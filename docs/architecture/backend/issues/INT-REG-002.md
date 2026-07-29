# INT-REG-002 -- Validar integracao de observabilidade: logs de ciclo de vida no pipeline da plataforma

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
SPEC-07 e SPEC-10 exigem que REG emita logs estruturados de ciclo de vida (registro, renovacao, eviction) ao pipeline de observabilidade da plataforma. Este issue valida a integracao de ponta a ponta: eventos de lifecycle de REG sao consultaveis no destino de logs configurado. A integracao deve ser best-effort -- REG nao pode ser bloqueado por indisponibilidade do pipeline.

## Spec de referencia
- SPEC-07, SPEC-10, contracts/integration-contracts.md

## Escopo
- [ ] Verificar que evento de registro de `pac-service` gera entrada de log JSON com campos `logical_id=PAC-SERVICE`, `event_type=registration`
- [ ] Verificar que evento de eviction de instancia stale gera entrada com `event_type=eviction`
- [ ] Simular indisponibilidade do pipeline de observabilidade (parar o coletor local) e verificar que REG continua aceitando registros e respondendo discovery sem erro

## Criterios de aceite
- [ ] Evento de registro encontrado no destino de logs configurado, com `logical_id` e `event_type` corretos
- [ ] REG continua healthcheck UP com o pipeline de observabilidade parado (best-effort confirmado)
- [ ] Nenhum campo de dado pessoal de paciente/cuidador aparece nos registros de log

## Dependencias
- REG-005 (observabilidade implementada), REG-009 (compose com pipeline local, se configurado)

## Estimativa
M

## Prioridade
P3
