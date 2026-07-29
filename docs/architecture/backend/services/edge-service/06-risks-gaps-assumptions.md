<!-- OUTPUT-SCHEMA
- Riscos
- Gaps Conhecidos
- Suposicoes a Validar
- Dependencias Criticas
-->

# 06 — Risks, Gaps, Assumptions

## Riscos
- **R1 — Detecção de queda depende de polling (safety-critical).** Sem broker
  (`messaging.enabled = false`), EDGE não empurra "dispositivo offline"; DASH descobre por polling. A
  latência do polling limita quão rápido a cuidadora vê a queda. Para um produto de segurança isso é o
  risco central. *Mitigação MVP:* janela de staleness curta + intervalo de polling do DASH curto;
  reavaliar broker (RabbitMQ) assim que o número de pacientes crescer (nota em `stack.messaging`).
- **R2 — Falso "online".** Se a liveness fosse um flag persistido e atualizado por job, uma falha do job
  deixaria um dispositivo morto marcado como vivo. *Mitigação:* `status` é derivado em tempo de leitura
  de `ultima_sincronizacao` (ADR-006), não um flag persistido — não existe job para falhar.
- **R3 — Spoofing de dispositivo.** Um atacante poderia forjar heartbeats/pareamento de outro
  dispositivo. *Mitigação:* toda chamada é autenticada como o próprio dispositivo (ADR-005); o id nunca
  vem do corpo.
- **R4 — Deriva de versão de perfil.** O dispositivo pode rodar um perfil de detecção desatualizado.
  *Mitigação:* o dispositivo reporta a versão em execução no heartbeat; EDGE compara com o ponteiro do
  GEST e sinaliza drift na resposta (nunca silencioso).
- **R5 — Clock skew do edge.** Liveness usa tempo; relógio do dispositivo divergente pode distorcer
  `ultima_sincronizacao`. *Mitigação:* carimbar `ultima_sincronizacao` com o relógio do servidor no
  aceite do heartbeat, não com o timestamp do cliente.
- **R6 — Pareamento trocado.** Parear o dispositivo ao paciente errado numa clínica com vários leitos é
  um erro de segurança. *Mitigação:* pareamento explícito e auditado (transition log), invariante de
  exclusividade; confirmação de identidade do paciente no fluxo de onboarding (a validar em Specs).

## Gaps Conhecidos
- **G1 — Mecanismo de credencial do dispositivo indefinido.** ADR-005 fixa o *princípio* (autenticar
  como o dispositivo); o mecanismo concreto (mTLS vs token de dispositivo vs client-credentials por
  dispositivo) é decidido em `/be-architect-specs` junto ao contrato de segurança.
- **G2 — Threshold de staleness não numerado.** O valor da janela (ex.: 3× o intervalo de heartbeat) é
  um parâmetro operacional a definir em Specs/config, não fixado na visão.
- **G3 — Intervalo de heartbeat.** Cadência do heartbeat (e do polling do DASH) a definir; equilibra
  frescor da liveness × custo/energia no edge.
- **G4 — Provisionamento inicial (bootstrap trust).** Como o operador aprova o registro de um novo
  dispositivo (claim code, pré-provisionamento) é um detalhe de fluxo a especificar.
- **G5 — Multi-dispositivo por paciente.** MVP assume 1 dispositivo ativo/paciente; redundância (2
  câmeras) fica fora de escopo e exigiria revisar a invariante de exclusividade.

## Suposicoes a Validar
- **A1** — Um paciente é monitorado por exatamente um dispositivo edge ativo no MVP (invariante de
  exclusividade). Fonte: BA data-model (`Paciente.dispositivo` 1:1) + registry EDGE.
- **A2** — `DispositivoEdge` é tenant-scoped (pertence a uma empresa) e nunca compartilhado entre
  empresas. Fonte: `multiTenancy.isolationStrategy = schema-per-tenant`.
- **A3** — O heartbeat é pull/HTTP síncrono e é o veículo natural para entregar o ponteiro de perfil do
  GEST e coletar a versão em execução. Fonte: nota `stack.messaging` (REST + outbox de retry local).
- **A4** — EDGE não recebe nenhum dado biométrico/vídeo; apenas metadados de liveness e versão. Fonte:
  invariante de privacidade herdada do GEST doc 02.
- **A5** — DASH consome liveness por polling de endpoint EDGE (não por push). Fonte: ausência de broker.

## Dependencias Criticas
- **GEST (gest-service)** — fonte do ponteiro de versão do perfil de detecção entregue ao dispositivo;
  sem GEST, EDGE ainda pareia e monitora liveness, mas não entrega perfil.
- **PAC (pac-service)** — consumidor de `EdgeDevicePort` para validar `dispositivo_edge_id`; contrato de
  validação precisa estar alinhado (cross-service ref, não FK).
- **DASH (dash-service)** — consumidor da liveness; o valor de segurança do módulo só se realiza quando
  DASH exibe e alerta sobre o estado `offline`.
- **MIG** — DDL centralizada; sem os scripts EDGE registrados no MIG não há schema.
- **REG / GWY** — descoberta e roteamento; o edge agent só alcança EDGE via GWY.
- **OAuth2 / auth boundary** — necessário para autenticar tanto o operador (ações de registro/pareamento)
  quanto o dispositivo (ADR-005).
