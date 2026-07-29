# EDGE-003 -- Define DispositivoEnrollment, DispositivoCredential, DispositivoEdgeTransition JPA entities + port interfaces

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto
As tres entidades filhas do agregado dao suporte a: bootstrap de confianca (token de enrollment, hash-only), material de autenticacao do device (client_credentials, hash-only), e trilha de auditoria append-only de transicoes de pareamento e liveness (ADR-005 invariante #3). Nenhuma das tres armazena dado em plaintext.

## Spec de referencia
- SPEC-04, SPEC-05, SPEC-06

## Escopo
- [ ] criar `domain/model/DispositivoEnrollment.java` (campos data-model §2.2: id, tenantId, dispositivoEdgeId, tokenHash, expiresAt, consumedAt, createdAt)
- [ ] criar `domain/model/DispositivoCredential.java` (campos data-model §2.3: id, tenantId, dispositivoEdgeId, clientId, secretHash, status, issuedAt, revokedAt) com enum `CredentialStatus {active, revoked}`
- [ ] criar `domain/model/DispositivoEdgeTransition.java` (campos data-model §2.4: id, tenantId, dispositivoEdgeId, kind, fromState, toState, actor, reason, at) com enum `TransitionKind {pairing, liveness}`
- [ ] criar `domain/port/DispositivoEnrollmentRepositoryPort.java` (findByTokenHash, save, markConsumed)
- [ ] criar `domain/port/DispositivoCredentialRepositoryPort.java` (save, findActiveByDeviceId, revokeByDeviceId)
- [ ] criar `domain/port/DispositivoEdgeTransitionRepositoryPort.java` (append, findByDeviceId — SEM metodos de update/delete)

## Resultado esperado
Tres entidades JPA e seis interfaces de porta compilaveis, prontas para os adapters de infraestrutura.

## Criterios de aceite
- [ ] `compileJava` exit 0 com todas as entidades
- [ ] nenhum metodo de mutation (update/delete) em `DispositivoEdgeTransitionRepositoryPort` — apenas `append`
- [ ] `DispositivoCredential.secretHash` nao tem setter publico (imutavel apos criacao)
- [ ] todos os campos de timestamp sao `OffsetDateTime` (UTC)
- [ ] enums `CredentialStatus` e `TransitionKind` presentes nos respectivos modelos

## Dependencias
- EDGE-002 (precede)

## Estimativa
M

## Prioridade
P1
