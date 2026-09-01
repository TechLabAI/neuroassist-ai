<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-001 — CONV como serviço novo independente (não extensão de PAC)

## Status
aceita

> Escopo: local (`conv-service`). Deriva de e implementa a decisão de produto já fechada em
> **ADR-013 (accepted)** — "Módulo CONV novo vs. extensão de PAC". Este ADR registra a consequência
> arquitetural para o serviço; não reabre a decisão.

## Contexto
CONV é um módulo **novo, retrofit** num sistema já em produção (GEST/EVNT/PAC/ACS/HIST/DASH/EDGE existem
como serviços reais). A árvore de falas poderia caber como extensão de `pac-service` (cadastro de paciente
+ catálogo de comandos) ou como serviço próprio. O ADR-013 fechou a favor de módulo novo, para não acoplar
um modelo de dados novo e potencialmente volátil (árvore, conteúdo por locale, referência de áudio) ao
schema de PAC já em produção, e para manter o padrão "1 módulo = 1 unidade de trabalho do pipeline". Falta
registrar, no nível do serviço, o boundary concreto: single service vs. decomposição.

## Decisão
`conv-service` é um **único microserviço independente** (alinhado ao ADR compartilhado de microserviços
independentes), deployável por si, registrado no Eureka (REG) e roteado pelo GWY em `/api/v1/conv/**`. Não
o decompomos além disso: navegação, busca assistida e seleção de nó compartilham o mesmo agregado, as
mesmas transações e o mesmo contexto de tenant/paciente — separá-los adicionaria saltos de rede sem uma
costura de bounded context. CONV **não** estende `pac-service` nem toca o schema de PAC; o acoplamento a
PAC/GEST fica limitado ao vocabulário compartilhado `engineFeatureRef` (referência, não FK). TREI é um
módulo **irmão** que escreve o mesmo schema/índice/store que o CONV lê — compartilham dado, não call-path
de runtime.

## Consequências
**Positivas:** blast radius isolado — evoluir a árvore/TTS não exige release do PAC; sem risco de regressão
no catálogo de comandos do piloto real; padrão de módulo consistente; deploy e versionamento próprios.
**Negativas / trade-offs:** mais um serviço para manter, testar e monitorar; a referência a `Paciente`
(owned por PAC) é por id, sem FK cross-service nem transação atômica PAC↔CONV — aceito como consequência
direta do ADR-013 (sinal de erro se transação atômica passar a ser frequente). Reverter para dentro do PAC
é de custo médio e mais barato que o caminho inverso, pois o dado nasce isolado no CONV.
