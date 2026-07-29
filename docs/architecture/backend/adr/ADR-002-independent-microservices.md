<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-002 — Independent Microservices

## Status
aceita

## Contexto
Uma vision anterior deste ADR (auto-gerada durante a fase de vision de um módulo de
infraestrutura, sem revisão prévia) propôs monólito modular como composição do backend,
justificado por time pequeno e piloto de baixo volume (uma família, um paciente). Essa proposta
nunca foi de fato aprovada pelo arquiteto do projeto e foi revertida antes de qualquer scaffold
ser gerado (D-526, registrado em `techlab-architecture`). O padrão do framework
techlab-architecture (D-007 e o pipeline `/be-architect-scaffold` por módulo) assume
microsserviços independentes — um `backend/services/<service>/` deployável por módulo — com
GWY (gateway) e REG (service registry/Eureka) existindo precisamente para rotear e descobrir
serviços que são processos distintos. Um monólito modular tornaria GWY/REG estruturalmente
supérfluos.

## Decisão
Vamos manter o padrão do framework: **microsserviços independentes**, um por módulo do
`modules.registry` (GEST, EVNT, PAC, ACS, HIST, DASH, EDGE) mais os três serviços de
infraestrutura (GWY, REG, MIG). Cada serviço é um deployável Spring Boot próprio, registrado no
Eureka (REG) e roteado pelo Gateway (GWY). Comunicação síncrona (REST) no MVP — sem barramento de
eventos, consistente com `stack.messaging.enabled = false` — mas entre processos separados, não
dentro de um único processo monolítico.

## Consequências
**Positivas:** GWY/REG têm papel real (roteamento e descoberta de serviços de fato distintos);
consistência com o padrão do framework e com o pipeline `/be-architect-scaffold` (cada módulo já
scaffolda como serviço Gradle próprio); nenhum retrabalho de composição se o piloto crescer para
múltiplas famílias/clínicas.
**Negativas / trade-offs:** mais overhead operacional que um monólito para o volume atual (1
paciente) — N processos deployáveis em vez de 1; latência de rede entre serviços onde antes seria
chamada in-process; exige service discovery (REG) funcionando mesmo em ambiente de piloto pequeno.
Aceito porque é o padrão já suportado pelo framework e evita uma migração de topologia depois.
