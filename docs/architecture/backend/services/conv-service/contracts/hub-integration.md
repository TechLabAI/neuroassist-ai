# CONV — Hub / Platform Integration (`conv-service`)

> Internal platform (monorepo) service dependencies of CONV. External/infrastructure integrations
> (Elasticsearch, audio filesystem) are in [`integration-contracts.md`](integration-contracts.md).
> **TechLab AI hub: none** — `ai.hasAi = false`; CONV consumes no orchestration/knowledge/
> document_composition/analytics capability (`ADR-002`/ADR-015). All calls synchronous REST over
> Eureka (REG) + Spring Cloud Gateway (GWY); `messaging.enabled = false`.

---

## 1. Routing & discovery — GWY / REG

- **REG (Eureka):** `conv-service` registers like every other service; discovered by id `conv-service`.
- **GWY (Spring Cloud Gateway):** routes the public surface `/api/v1/conv/**` to `conv-service`
  (shared `ADR-008-public-api-surface-normalization.md`). GWY injects `X-Correlation-Id` when absent.

**EARS**

- THE SYSTEM SHALL register with REG and be reachable only through GWY at `/api/v1/conv/**`; it SHALL NOT expose a separate public ingress.

---

## 2. ACS (`acs-service`) — identity (HARD, fail-closed)

- **Port:** `AcsSessionPort` (infrastructure adapter). CONV is a **pure resource server** for the
  *patient-with-session* actor (`ADR-003` → ADR-012). CONV does **not** own, mint, store, or revoke this
  identity.
- **Operations:**
  1. **Validate JWT** — signature via shared Keycloak realm `jwk-set-uri`; required claims `sub`,
     `empresa`, `pacienteId`, `locale`, `scope`.
  2. **Enforce scopes** — `conv.arvore.navegar` / `conv.no.selecionar`; reject any caregiver/admin
     authority.
  3. **Blacklist check** — on **every** request, consult the ACS revocation blacklist so the
     non-expiring session can be revoked immediately.
- **Failure posture:** **hard dependency, fail-closed.** ACS unreachable or token invalid/blacklisted ⇒
  reject (`401`/`403`). Identity never degrades.
- **Blacklist cost (open, → SPEC-05/06):** a per-request blacklist check has latency/cost; specs
  evaluate caching with a short TTL vs. a shared cache, without weakening immediacy of revocation
  (risk R3, assumption A2).

**EARS**

- IF the JWT is missing, malformed, expired-signature, or on the ACS blacklist, THEN THE SYSTEM SHALL reject with `401`.
- IF the JWT lacks the endpoint's scope or carries caregiver/admin authority, THEN THE SYSTEM SHALL reject with `403`.
- WHILE ACS is unreachable, THE SYSTEM SHALL fail closed (reject) rather than serve unauthenticated.

---

## 3. PAC / GEST (`pac-service` / `gest-service`) — vocabulary (SOFT for gesture channel)

- **Port:** `VocabularyPort`. Resolves the shared **`engineFeatureRef`** vocabulary so a gesture
  recognized by GEST binds to a CONV tree node (`ADR-013`). Read-only, synchronous REST **by reference**
  — no schema coupling; CONV never writes PAC/GEST data.
- **Auth (service-to-service):** OAuth2 `client_credentials` (pattern of shared
  `ADR-007-edge-device-auth.md`), scope `pac.vocabulary.read` (gap G7 — exact scope confirmed here;
  provisioning of the client is a scaffold/ACS concern).
- **Contract shape (gap G4 — opaque):** `engineFeatureRef` is treated as an **opaque string id**. The
  exact vocabulary format (real system commit `89f4ee5`) is **not confirmed** to be consumable via a
  public API outside PAC/GEST. CONV models it as an opaque reference and documents the gap; if PAC/GEST
  expose no resolution endpoint, the binding degrades to "unbound" (api-contract §2.4) without failing.
- **Failure posture:** **soft** — only the gesture channel needs it; navigation and touch selection work
  without it. Unresolvable ref ⇒ `bound: false`.

**EARS**

- WHEN CONV needs to bind a gesture, THE SYSTEM SHALL call `VocabularyPort` with `client_credentials` auth and treat the `engineFeatureRef` as opaque.
- IF PAC/GEST cannot resolve the ref (unknown, unbound, or endpoint absent), THEN THE SYSTEM SHALL return `bound: false` and SHALL NOT fail navigation or touch selection.

---

## 4. HIST (`hist-service`) — audit sink (BEST-EFFORT)

- **Port:** `HistAuditPort`. Sends `SpeechNodeSelected` / `PhraseMatched` / `TreeNavigated` /
  `ConvServedDegraded` audit facts **synchronously** (no broker). Details:
  [`event-contracts.md`](event-contracts.md).
- **Failure posture:** **best-effort, non-blocking.** A HIST outage MUST NOT block communication; failed
  sends land in `conv_audit_outbox` (data-model §2.4) for retry (SPEC-09). The actor recorded is always
  the **patient session**.

**EARS**

- WHEN an auditable act occurs, THE SYSTEM SHALL attempt a synchronous send to HIST attributing it to the patient session.
- IF the HIST send fails, THEN THE SYSTEM SHALL enqueue the fact in `conv_audit_outbox` and SHALL still return success to the patient.

---

## 5. TREI (`trei-service`) — sibling writer, NOT a runtime call

- CONV and TREI **share data, not a runtime call path.** TREI writes the tree content
  (`conv_no_conteudo`), the ES `embedding`/documents (via `IndexPort`), and the cached audio files; CONV
  reads them. There is **no synchronous CONV→TREI or TREI→CONV call** at runtime.
- The shared data contract (PostgreSQL tables + ES document schema + audio path convention) is specified
  in [`data-model.md`](data-model.md) and [`../design/index-design-arvore-fala.md`](../design/index-design-arvore-fala.md)
  — this is the **direct input to TREI's own vision/specs** (gap G5).

## 6. Dependency posture summary

| Dependency | Port | Posture | On failure |
|---|---|---|---|
| ACS (identity/blacklist) | `AcsSessionPort` | **Hard / fail-closed** | reject `401`/`403` |
| Elasticsearch (search) | `SearchPort` (lib) | Soft / fail-open | static navigation, `degraded` |
| Audio filesystem | `AudioRefPort` | Soft / fail-open | Web Speech, `degraded` |
| PAC/GEST (vocabulary) | `VocabularyPort` | Soft (gesture channel only) | `bound: false` |
| HIST (audit) | `HistAuditPort` | Best-effort / non-blocking | outbox + retry |
| TREI | — (shared data) | Data dependency, no runtime call | serve what exists; empty tree if unauthored |
| MIG | — (Flyway provisioning) | Build/provision time | tables absent → deploy blocked |
