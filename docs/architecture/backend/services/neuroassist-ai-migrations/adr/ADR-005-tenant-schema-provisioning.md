<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-005 — Tenant schema provisioning strategy

## Status
aceita

## Contexto
Every new empresa needs an isolated schema `t_<tenantId>` brought to the current head, from one
canonical tenant template. Onboarding (ACS) must be able to request this. Open questions: is
provisioning idempotent? synchronous or event-driven? what happens on drift or partial failure?
what ordering vs the global schema? The broker is disabled (`messaging.enabled = false`) and the
platform's posture is REST/sync with local outbox retry, so an event-driven provisioning bus is
not available in the MVP.

## Decisão
Vamos tratar o provisionamento como uma operação **síncrona, idempotente e determinística**,
invocada por ACS no onboarding da empresa:
1. Criar `t_<tenantId>` se ausente.
2. Aplicar o **template canônico de tenant** até o head (mesmo template para `familia` e
   `clinica`).
3. Garantir que o schema global esteja em head antes (dependência de ordem global→tenant).
4. Serializar execuções por schema via **advisory lock**; re-invocar para um tenant já
   provisionado é no-op após o head.
5. **Fail-fast** em drift de checksum ou erro de DDL — abortar e reportar o schema que falhou;
   **sem auto-repair** no pipeline.
Deprovisioning (drop de schema) NÃO é automatizado — é operação destrutiva revisada manualmente.

## Consequências
**Positivas**
- Onboarding com um único passo determinístico; retries seguros (idempotência).
- Isolamento estrutural garantido (todo tenant nasce do mesmo template).
- Falhas visíveis e localizáveis (schema nomeado), sem corrupção silenciosa.

**Negativas / trade-offs aceitos**
- Chamada síncrona acopla a latência do onboarding ao tempo de migração do template; aceitável no
  volume do MVP (reavaliar broker se nº de pacientes/edge crescer — nota do stack).
- "Migrate all tenants" em nova versão do template percorre todos os schemas sequencialmente.
- Offboarding manual adiciona um passo operacional fora do pipeline (guardrail contra DDL
  destrutiva automática).
