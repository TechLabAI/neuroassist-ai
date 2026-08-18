# SPEC-09 — Messaging Architecture

## Objetivo
Documentar a arquitetura de mensageria do DASH no MVP: **broker desabilitado**; comunicação síncrona REST +
canal push SSE ao browser; caminho de broker diferido (ADR-005).

## Escopo
- **In:** justificativa do sync-only; canal SSE vs broker; mapeamento para tópicos se o broker for introduzido.
- **Out:** implementação de broker (não existe no MVP, `stack.messaging.enabled = false`).

## Decisões técnicas
- **Todo tráfego serviço-a-serviço é REST síncrono** (EVNT→DASH dispatch, leituras upstream, write-throughs).
- **Canal do browser é server-push SSE**, não broker e não poll; long-poll como fallback.
- **Sem fila durável / read-model** no DASH — EVNT e upstreams já persistem e fazem retry.
- **Broker-deferred:** se a mensageria for habilitada no projeto, `NotificationDispatch` (EVNT→DASH) e os
  callbacks `delivered`/`acknowledged` (DASH→EVNT) viram tópicos naturais **sem mudar significado nem a chave
  de idempotência** (`dispatchId`). O canal do browser permanece SSE.

## Casos de borda
- Pico de dispatches → SSE empurra por sessão; sem sessão → `no_open_session` (EVNT retry).
- Introdução futura de broker → contratos REST viram tópicos 1:1; nenhum re-desenho semântico.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL use synchronous REST for all service-to-service traffic in the MVP.
- THE SYSTEM SHALL push notifications to the browser over SSE, with long-poll as fallback.
- THE SYSTEM SHALL hold no durable queue or read-model.
- WHERE a broker is later enabled, THE SYSTEM SHALL preserve `dispatchId` as the idempotency key across the
  topic mapping.
