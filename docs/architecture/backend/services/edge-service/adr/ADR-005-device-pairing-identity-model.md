<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-005 — Device Pairing and Identity Model

## Status
aceita

## Contexto
EDGE é a autoridade de dispositivo do NeuroAssist AI: precisa saber qual dispositivo físico observa
qual paciente e garantir que cada heartbeat/pull veio *daquele* dispositivo e não de um impostor. Como
é um produto de segurança, duas falhas são inaceitáveis: (a) um dispositivo se passar por outro
(spoofing) e forjar liveness/telemetria; (b) um dispositivo ser pareado ao paciente errado (crítico
numa clínica com vários leitos). O BA modela `Paciente.dispositivo` como 1:1 e o registry declara EDGE
como "registro, pareamento e monitoramento de heartbeat". Falta decidir o *modelo de identidade e
pareamento*: como o dispositivo se autentica e como ele é vinculado a um paciente. Esta é a decisão
crítica local deste serviço (a decisão single-service vs decomposição já está resolvida por ADR-002
shared — microsserviços independentes; o corte nuvem↔edge é do GEST ADR-005).

## Decisão
Vamos adotar **identidade por dispositivo com pareamento exclusivo e explícito**:
1. **Registro credencia o dispositivo.** No registro, cada `DispositivoEdge` recebe uma identidade de
   backend e material de credencial próprio (referência hasheada, nunca segredo em claro). Toda chamada
   subsequente (heartbeat, pull de perfil) é **autenticada como aquele dispositivo**; o id nunca é lido
   do corpo da requisição. O mecanismo concreto (mTLS vs token de dispositivo vs client-credentials por
   dispositivo) é fixado em `/be-architect-specs` — este ADR fixa o *princípio*, não o transporte.
2. **Pareamento é exclusivo e operado.** Vincular um dispositivo a um paciente é uma ação explícita de
   um operador autenticado (Danilo), não implícita por telemetria. No MVP vale a invariante **um
   dispositivo ativo por paciente** e **um paciente por dispositivo pareado**. Substituição de
   dispositivo é despareamento + novo pareamento, com o antigo `retired`.
3. **Toda transição de pareamento é auditada** (`dispositivo_edge_transition`), incluindo ator e razão —
   evidência de segurança, não log operacional.
PAC valida `paciente.dispositivo_edge_id` contra EDGE via porta (referência cross-service, não FK).

## Consequências
**Positivas:** spoofing mitigado (identidade provada por chamada, não inferida do payload); pareamento
errado mitigado (ação explícita + auditoria + exclusividade); histórico de eventos continua resolvendo
porque dispositivos são `retired`, nunca apagados; PAC obtém uma referência confiável sem FK
cross-service.
**Negativas / trade-offs:** provisionamento e rotação de credencial por dispositivo adicionam
cerimônia de onboarding (claim/aprovação) que não existiria com um segredo compartilhado; a invariante
1:1 exclui redundância de câmera no MVP (2 dispositivos por paciente exigiria revisar a invariante); o
mecanismo de credencial fica como gap explícito a resolver em Specs (doc 06 G1).
