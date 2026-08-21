# E2E Sessão 2 — Onboarding + Directory + Audit Baseline

**Sessão:** 2 — Onboarding + Directory (estável) + Audit Baseline
**Issues alvo:** E2E-009 + E2E-003 (DIR-01, DIR-02, DIR-04) + E2E-004 (AUD-01..05)
**Status:** ✅ CONCLUÍDA — 2026-08-21 — Flow-01 17/17
**Concluída em:** 2026-08-21

---

## 1. Resultado

| Issue | Resultado |
|---|---|
| **E2E-009** (Flow-01) | ✅ **DONE** — 17/17 checks |
| **E2E-003** (Directory) | 🟡 DIR-01 ✅, DIR-04 ✅, **DIR-02 = feature ausente**; DIR-03/DIR-05 ficam na Sessão 5 |
| **E2E-004** (Audit) | 🟡 AUD-03/04/05 ✅, **AUD-01/02 = feature ausente**; AUD-06..10 ficam na Sessão 4 |

Cadeia completa validada de ponta a ponta:

```
login ACS → PAC cria paciente (→ACS directory) → PAC cria comando
  → :validate  (→GEST guarda de primitiva)
  → :publish   (→GEST gate de emergência ADR-005)  → HIST comando.publicado
  → :recalibrate                                    → HIST comando.recalibrado
  → :retire                                         → HIST comando.aposentado
  → GET /history (coordenador hist:read:all e cuidadora hist:read)
```

Os três fatos chegaram ao HIST com cadeia de hash íntegra (`sequenceNo`, `prevHash`,
`recordHash`) e o outbox do PAC ficou `sent` nos três.

---

## 2. Duas features ausentes (não são falhas de integração)

Ambas continuam **OPEN** — não há o que validar porque o código não existe:

- **AUD-01 / AUD-02** — o ACS não tem produtor de audit fact. Não existe
  `AutenticacaoRealizada` nem `AutenticacaoFalhou` em lugar nenhum do serviço.
- **DIR-02** — o `CaregiverRoutingClient` do EVNT é um stub vazio
  (`// TODO: add methods from techlab-ai-integration.md`) com `url` default apontando para a
  porta 8084 (GEST, não ACS). O endpoint do ACS foi validado diretamente; o consumo pelo
  EVNT fica para a Sessão 4 (Flow-03).

---

## 3. 16 defeitos de contrato

Catálogo completo (sintoma → causa raiz → correção → por que o gate unitário não pegou) em
**[plan-e2e-integration.md §11](../plans/plan-e2e-integration.md)**. C-1..C-15 corrigidos,
C-16 registrado como dívida.

Resumo por natureza:

| Natureza | Defeitos |
|---|---|
| Auth backbone (claim, nomes de authority, tenant) | C-1, C-2, C-3, C-14 |
| Contratos Feign (path/body/vocabulário) | C-4, C-5, C-6, C-8, C-9, C-11 |
| Multi-tenancy (nome de schema) | C-10, C-15 |
| Schema vs mapeamento JPA | C-12 |
| Capacidade perdida / suíte quebrada | C-7, C-13 |
| Escopo de leitura (dívida) | C-16 |

**Todos os 16 passaram por sete gates unitários verdes sem serem detectados.**

---

## 4. Arquivos alterados

Código de produção: `RoleAuthorityPolicy`, `TokenIssuer` (ACS) · `SecurityConfig`,
`MultiTenantConnectionProvider` (PAC/EDGE/HIST/GEST) · `TenantContextFilter`,
`TenantIdentifierResolver` (GEST) · clientes Feign e DTOs de PAC→{ACS,GEST,HIST} e HIST→ACS ·
`HistFact`, `HistFactAdapter` (PAC) · `application.yml` (HIST, migrations) ·
`docker-compose.dev.yml` · nova migration `V0207__pac_hist_outbox_status_varchar.sql`.

Testes ajustados junto (nenhum desabilitado): `RoleAuthorityPolicyTest`, `DirectoryServiceTest`,
`PacWireMockStubs`, `ExternalServicesWireMock`, `CommandLifecycleSteps`,
`EmergencyEligibilityGateSteps`, `HistoryQuerySteps`, `AcsIntegrationTest`,
`EdgePairingCircuitBreakerIT`.

**Gates:** suítes completas + `architectureCheck` verdes em ACS, PAC, HIST, EDGE e GEST.

---

## 5. Ambiente

Seed de desenvolvimento (`V0102__acs_dev_seed.sql`) — cuidadores vivem em
`neuroassist_global.cuidador`, não no schema do tenant:

```
TENANT   = e2e00000-0000-0000-0000-000000000001   (schema t_emp_dev, ACTIVE)
COORD    = coord@neuroassist.dev      / Test@1234!   COORDENADOR_CLINICA
CUIDADORA= cuidadora@neuroassist.dev  / Test@1234!   CUIDADORA
APOIO    = apoio@neuroassist.dev      / Test@1234!   PACIENTE_APOIO
JAMILLY  = jamilly@neuroassist.dev    / Test@1234!   CUIDADORA (004e95eb-7e48-4b01-afd1-f39dbdbe1974)
```

**Pré-condição do Flow-01:** é preciso uma primitiva de gesto registrada no GEST em estado
`SUPPORTED` (`POST /gest-service/tipo-gesto` → transições `PENDING_TRAINING` → `SUPPORTED`).
Sem ela o `:validate` responde 422 e o gate de emergência bloqueia o `:publish`.

Rebuild + restart após alterar código (os JARs são bind-mounts, não imagens):

```bash
cd backend && ./gradlew :services:<svc>:bootJar -x test
docker restart neuroassist-<svc>
# migrations: docker compose -f docker/docker-compose.dev.yml up migrations-empresa
```

---

## 6. Pré-requisitos para a Sessão 3 (Device + Heartbeat)

1. **C-10 pendente em EVNT e DASH** — mesmo patch de resolução `tenant_id → schema_nome`.
2. **GEST não é resource server de verdade** — `anyRequest().permitAll()` e sem
   `@EnableMethodSecurity`; os `@PreAuthorize` dos controllers são inertes. O JWT já é
   decodificado (C-14), falta enforcar.
3. **EDGE mistura prefixos** — `SCOPE_edge.manage` na SecurityConfig vs `edge.manage` no
   `@PreAuthorize`, com converter de prefixo vazio.
4. **`edge.pairing.url` do GEST aponta para 8090** (DASH) em vez de 8087 (EDGE).

---

## 7. Recomendação

13 dos 16 defeitos são o mesmo padrão: **cada consumidor foi testado contra um mock que ele
mesmo escreveu do produtor**. O mock codifica a suposição do consumidor, então o teste passa
exatamente quando a suposição está errada.

Antes de seguir para a Sessão 3, avaliar promover **testes de contrato consumer-driven**
(Pact / Spring Cloud Contract) — já previstos em §5.1 do plano como "CI a cada PR", nunca
implementados. É o único controle que pegaria C-4..C-9 e C-11 em tempo de PR; sem ele, as
sessões restantes vão continuar descobrindo esses defeitos um a um, em runtime.

Sinais de learning desta sessão emitidos no Elasticsearch
(`techlab-pipeline-events-v1`, categoria `LEARNING`): 16 defeitos + 6 aprendizados
sistêmicos, consultáveis pelo `trace_id` da sessão.
