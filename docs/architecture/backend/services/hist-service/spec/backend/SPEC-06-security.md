# SPEC-06 — Security

> Service: `hist-service` · Module: HIST · Refs: ADR-003 (shared), ADR-004 · EARS notation (D-057).

## Objetivo

Especificar autenticação (OAuth2 resource server), autorização por scope, isolamento multi-tenant, e a
postura LGPD do ledger.

## Escopo

**In:** validação de JWT ACS, scopes `hist:write`/`hist:read`/`hist:read:all`, tenant pinning, proibição
de payload biométrico, postura de imutabilidade, questão aberta de erasure. **Out:** emissão de token
(ACS).

## Decisões técnicas

- **Resource server**: valida JWTs emitidos pelo ACS localmente contra o JWK set do ACS (sem callback por
  request). `tenant` claim pina o schema.
- **Scopes**: `hist:write` (produtores), `hist:read` (cuidadora — escopo de pacientes), `hist:read:all`
  (coordenador/DPO — tenant inteiro).
- **Scope de leitura de cuidadora** resolvido via ACS `ActorDirectoryPort` (fail-closed).
- **Tenant isolation**: toda leitura/escrita confinada a `t_<empresa>`; body/query/path tenant divergente
  do claim → `403`.
- **LGPD**: `payload` nunca carrega dado biométrico/imagem (rejeição `422`); dados pessoais no ledger são
  referências + labels snapshot.

## Postura de imutabilidade (defense-in-depth, ADR-004)

1. Domínio não expressa mutação. 2. Aplicação só oferece `append`. 3. Banco: papel do serviço sem
UPDATE/DELETE no ledger.

## Casos de borda

- Token expirado/assinatura inválida → `401`.
- `hist:write` ausente em `POST /audit-facts` → `403`.
- Cuidadora consultando paciente fora do seu escopo → resultado vazio para aquele paciente (ou `503` se ACS
  fora).

## Critérios de aceite (EARS)

- IF a request presents no valid bearer token, THEN THE SYSTEM SHALL respond `401`.
- IF a `POST /audit-facts` caller lacks `hist:write`, THEN THE SYSTEM SHALL respond `403`.
- WHERE a reader holds `hist:read` but not `hist:read:all`, THE SYSTEM SHALL constrain results to the
  caregiver's ACS-resolved patient scope.
- THE SYSTEM SHALL validate the JWT signature and `tenant` claim locally against the ACS JWK set and SHALL
  NOT call ACS on the request path for token validation.
- IF a request's body/query/path tenant differs from the JWT `tenant` claim, THEN THE SYSTEM SHALL respond
  `403` (`/errors/tenant-mismatch`).
- IF `payload` carries any biometric/image key, THEN THE SYSTEM SHALL reject the append with `422`.
- THE SYSTEM SHALL expose no code path or endpoint that updates or deletes a written record (ADR-004).

## §Open — LGPD erasure vs imutabilidade (decisão pendente)

Erasure sub-tenant por paciente preservando a cadeia é gap aberto para Specs/legal (visão doc 06,
warning M1). Candidato: **crypto-shredding** — cifrar `payload` com chave por-paciente e destruir a chave
no erasure, mantendo `record_hash`/esqueleto para a cadeia. Trade-off: a canonicalização passaria a operar
sobre ciphertext/coventant; verificação continua válida, conteúdo fica irrecuperável. **Não implementado no
MVP** (erasure = drop do schema no off-boarding). Registrar decisão antes da fase de execução.
