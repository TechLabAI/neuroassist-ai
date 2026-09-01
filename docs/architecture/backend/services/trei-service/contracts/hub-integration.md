# TREI — Hub / Intra-mesh Integration (`trei-service`)

> Module **TREI** · Internal platform services consumed **by reference** over synchronous REST via
> Eureka (REG) + Spring Cloud Gateway (GWY). **TechLab AI hub: none** (`ai.hasAi = false`) — TREI's LLM
> dependency is the direct Anthropic API (see [`integration-contracts.md`](integration-contracts.md) §1),
> not the orchestration hub. External/third-party integrations are in
> [`integration-contracts.md`](integration-contracts.md).
> EARS acceptance criteria per [`../../../standards/ears-notation.md`](../../../standards/ears-notation.md) (D-057).

---

## 0. TechLab AI hub — not consumed

`ai.hasAi = false`. TREI consumes **no** TechLab AI capability (orchestration, knowledge,
document_composition, analytics). All assisted generation is the direct Anthropic API, quarantined to the
authoring phase (ADR-016). This section exists to make the boundary explicit for the backlog/scaffold.

---

## 1. ACS (`acs-service`) — identity (hard / fail-closed)

- **Role:** OAuth2 issuer/validator for **both** actors — the caregiver (DASH shell) and the
  patient-with-session (`ADR-012`). Shared realm; TREI is a resource server, not the identity owner.
- **Access:** JWT signature validation (`jwk-set-uri`) + per-request revocation check via `AcsSessionPort`.
- **Posture:** **hard dependency, fail-closed.** If ACS is unreachable, identity cannot be validated →
  requests are rejected / health `DEGRADED` (api §2.9).

**EARS**
- IF a JWT is on the ACS revocation blacklist, THEN THE SYSTEM SHALL reject the request with `401`
  (`session-revoked`) on **every** call, regardless of non-expiry (ADR-003/ADR-012).
- THE SYSTEM SHALL require the caregiver identity for `treinamento.no.aprovar.cuidadora` and the
  patient-with-session identity for `treinamento.no.aprovar.paciente`, and SHALL reject the wrong actor
  with `403` (dual authorization, ADR-003).

## 2. PAC (`pac-service`) — patient scope & vocabulary (read-only)

- **Role:** resolves that `pacienteId` belongs to the caller's `empresa`; supplies the opaque
  `engine_feature_ref` vocabulary when a proposed phrase binds to a gesture (shared with CONV/GEST).
- **Access:** synchronous REST (service-to-service OAuth2 `client_credentials`), read-only via
  `PacPatientPort` / `VocabularyPort`.
- **Posture:** required for session start (patient resolution); `engine_feature_ref` is treated as an
  **opaque** token TREI never parses (gap G4, mirrors CONV).

**EARS**
- WHEN a session is started, THE SYSTEM SHALL resolve `pacienteId` within the caller's `empresa` via PAC and
  reject with `404` (`patient-not-found`) if it does not resolve.
- THE SYSTEM SHALL treat `engine_feature_ref` as an opaque identifier and SHALL NOT parse its structure.

## 3. HIST (`hist-service`) — audit (synchronous, by reference)

- **Role:** immutable audit trail of authoring facts (proposal, each approval, publication, discard).
- **Access:** synchronous REST via `HistAuditPort`; a HIST outage is buffered by the local outbox posture
  and never blocks authoring (event-contracts §4).
- **Posture:** soft for request success (buffered), but every fact MUST eventually reach HIST.

**EARS**
- THE SYSTEM SHALL audit each approval and each publication to HIST with actor id, `noId`, `locale`, and
  `correlationId`, carrying no clinical text (SEC-002).
- IF HIST is unreachable, THEN THE SYSTEM SHALL buffer the fact and retry, and SHALL NOT fail the caller.

## 4. GWY / REG — edge & discovery

- **GWY (Spring Cloud Gateway):** routes `/api/v1/trei/**`, injects/propagates `X-Correlation-Id`.
- **REG (Eureka):** service discovery for intra-mesh calls (ACS/PAC/HIST). TREI registers as
  `trei-service`; the Eureka plane is platform-internal (not edge-exposed).

**EARS**
- THE SYSTEM SHALL register with REG as `trei-service` and SHALL be reachable at the edge only through GWY
  under `/api/v1/trei`.

## 5. CONV↔TREI shared contract (no direct call)

TREI and CONV **do not call each other**. They are coupled only through shared **state**: TREI writes the
`conv_*` tree tables (same schema), the ES `arvore-fala` documents, and the audio files; CONV reads them.
The consistency + G5 model-id parity contract lives in
[`data-model.md`](data-model.md) §3 and
[`../../conv-service/design/index-design-arvore-fala.md`](../../conv-service/design/index-design-arvore-fala.md).

**EARS**
- THE SYSTEM SHALL couple with CONV only through shared persistent state (PostgreSQL tree tables + ES index
  + audio store) and SHALL NOT expose or consume a synchronous CONV endpoint.
