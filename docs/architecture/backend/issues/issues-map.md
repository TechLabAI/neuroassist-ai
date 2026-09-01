# Issues Map -- issues

| Issue | Title | Kind | Priority | Status |
|---|---|---|---|---|
| PAC-001 | Criar subprojeto Gradle pac-service com dependências Spring Boot 3 + Cloud + Security + JPA | BE | P1 | INTACT |
| PAC-002 | Criar DTOs de request/response (Paciente, Comando, CatalogoAtivo) | BE | P1 | INTACT |
| PAC-003 | Criar interfaces de port da camada application (Paciente, Comando, Gest, Acs, Edge, Hist) | BE | P1 | INTACT |
| PAC-004 | Criar entidades JPA (Paciente, Comando, PacienteCuidador, TipoGestoRef) com constraints e row_version | BE | P1 | INTACT |
| PAC-005 | Implementar PacienteController (POST, GET/{id}, GET list paginado, PUT) com scope enforcement | BE | P2 | INTACT |
| PAC-006 | Implementar PacienteService (create/findById/listAll/update com resolução ACS + EDGE) | BE | P2 | INTACT |
| PAC-007 | Implementar PacienteJpaAdapter (CRUD + schema routing + ETag/If-Match concorrência) | BE | P2 | INTACT |
| PAC-008 | Criar migrations Flyway V0001–V0004 para MIG (paciente, comando, paciente_cuidador, tipo_gesto_ref, trigger) | BE | P3 | INTACT |
| PAC-009 | Implementar TenantSchemaResolver (X-Empresa-Id → set_search_path por-empresa, defense-in-depth) | BE | P3 | INTACT |
| PAC-010 | Implementar ComandoJpaAdapter (índice parcial ativo + row_version + filtro por estado) | BE | P3 | INTACT |
| PAC-011 | Implementar ComandoController (POST draft, GET/{cid}, GET list com filtro estado, PUT edit) | BE | P4 | INTACT |
| PAC-012 | Implementar ComandoAuthoringService.createDraft com todos os guards (timing, primitivos distintos, duplicate-active-mapping) | BE | P4 | INTACT |
| PAC-013 | Implementar ComandoAuthoringService.editDraft com guard estado=drafted e campos identidade congelados | BE | P4 | INTACT |
| PAC-014 | Implementar ComandoLifecycleService.validate (drafted→validated + resolução primitivos via GestDetectabilityPort) | BE | P5 | INTACT |
| PAC-015 | Implementar ComandoLifecycleService.publish (validated→published + emergency gate + bump catalogoVersao + fato HIST) | BE | P5 | INTACT |
| PAC-016 | Implementar ComandoLifecycleService.retire (→retired + bump catalogoVersao + fato HIST + sem hard delete ADR-006) | BE | P5 | INTACT |
| PAC-017 | Implementar EmergencyEligibilityGate (fail-closed emergencia / advisory rotina, Resilience4j CB, timeout ≤2s) | BE | P5 | INTACT |
| PAC-018 | Implementar ComandoLifecycleService.recalibrate (tuning only + calibracaoVersao +1 + gate re-run + fato HIST) | BE | P6 | INTACT |
| PAC-019 | Implementar PacienteVersionBumper (incremento atômico de catalogoVersao na mesma transação) | BE | P6 | INTACT |
| PAC-020 | Implementar CatalogoAtivoController + CatalogoAtivoService (GET published only + If-None-Match ETag / 304) | BE | P6 | INTACT |
| PAC-021 | Configurar OAuth2 resource server JWT (scopes por endpoint, claim tenant validation, SPEC-06) | BE | P7 | INTACT |
| PAC-022 | Implementar GlobalExceptionHandler com RFC 7807 ProblemDetail para todos os tipos de erro (SPEC-01 §6) | BE | P7 | INTACT |
| PAC-023 | Implementar Idempotency-Key dedup (POST creates, cache 24h, idempotency-mismatch 409, SPEC-05) | BE | P8 | INTACT |
| PAC-024 | Configurar Actuator, Micrometer e logging estruturado com MDC (correlationId, empresaId, SPEC-07) | BE | P9 | INTACT |
| PAC-025 | Validar cenários BDD Cucumber (patient-registration, command-lifecycle, emergency-gate, catalog-publication) | BE | P9 | INTACT |
| PAC-INT-001 | Implementar GestDetectabilityClient Feign + Resilience4j CB (resolveTipoGesto + isSupported, timeout 1s/2s) | INT | P5 | INTACT |
| PAC-INT-002 | Implementar AcsDirectoryClient Feign (resolveTenant + resolveCuidador, timeout 2s/3s) | INT | P4 | INTACT |
| PAC-INT-003 | Implementar EdgeDeviceClient Feign (resolveDispositivo, timeout 2s/3s, rejeitar write com 503 se indisponível) | INT | P4 | INTACT |
| PAC-INT-004 | Implementar HistFactClient Feign + outbox local de retry (HistUnavailableException, 2 retries, sem rollback) | INT | P6 | INTACT |
| PAC-INT-005 | Implementar propagação de X-Correlation-Id e X-Empresa-Id em todas as chamadas Feign outbound | INT | P4 | INTACT |
| PAC-INT-006 | Criar WireMock stubs para GEST/ACS/EDGE/HIST nos testes de integração + Cucumber runner | INT | P8 | INTACT |
| EDGE-001 | Create edge-service Gradle module (build.gradle.kts + Spring Boot entrypoint + hexagonal package skeleton) | BE | P1 | INTACT |
| EDGE-002 | Define DispositivoEdge JPA aggregate root with pairing state machine and exclusive-pairing partial UNIQUE index | BE | P1 | INTACT |
| EDGE-003 | Define DispositivoEnrollment, DispositivoCredential, DispositivoEdgeTransition JPA entities + port interfaces | BE | P1 | INTACT |
| EDGE-004 | Implement REST controllers skeleton with DTOs (DeviceController, HeartbeatController, LivenessController, PairingController) | BE | P1 | INTACT |
| EDGE-005 | Implement DeviceRegistrationService.register() — mint one-time enrollment token (hash-only), respond 201 with plain token | BE | P2 | INTACT |
| EDGE-006 | Implement DeviceRegistrationService.enroll() — exchange enrollment token for OAuth2 client_credentials, return credential once | BE | P2 | INTACT |
| EDGE-007 | Implement PairingService.pair() and unpair() with exclusive-pairing enforcement, state machine, and audit transitions | BE | P2 | INTACT |
| EDGE-008 | Implement HeartbeatService.recordHeartbeat() — server-stamp ultimaSincronizacao, call DetectionProfilePort, raise domain events, detect liveness crossing | BE | P2 | INTACT |
| EDGE-009 | Implement LivenessService.evaluate() — read-time liveness derivation, liveness crossing detection, and liveness endpoints | BE | P2 | INTACT |
| EDGE-010 | Implement JPA adapters for all four EDGE repository ports (DispositivoEdge, Enrollment, Credential, Transition) with multi-tenant schema routing | BE | P3 | INTACT |
| EDGE-011 | Generate Flyway migration V0700__edge_initial_schema.sql (4 tables, pgcrypto extension, indexes, constraints) and contribute to MIG | BE | P3 | INTACT |
| EDGE-012 | Configure OAuth2 JWT resource server (jwk-set-uri via ACS/REG, scope enforcement: edge.read / edge.manage / edge.device / edge.internal) | BE | P5 | INTACT |
| EDGE-013 | Implement TenantContextHolder propagation, MDC enrichment (tenantId, correlationId, deviceId) and X-Correlation-Id filter | BE | P5 | INTACT |
| EDGE-014 | Implement DetectionProfileClient Feign adapter (GET /gest/detection-profiles/active) with Resilience4j circuit breaker and degraded fallback | BE | P4 | INTACT |
| EDGE-015 | Implement DeviceRegistrationService.retire() with credential revocation and state-machine enforcement | BE | P4 | INTACT |
| EDGE-016 | Implement Spring TransactionalEventListener wiring for all 7 in-process domain events (post-commit, DispositivoRegistrado .. ProfileDriftDetected) | BE | P6 | INTACT |
| EDGE-017 | Expose Actuator health/readiness/liveness endpoints and Micrometer metrics (heartbeats.received, pairing.transitions, profile.drift) | BE | P7 | INTACT |
| EDGE-018 | Implement structured JSON logging with MDC (correlationId, tenantId, actor, deviceId) — assert zero biometric/PII/credential in logs | BE | P7 | INTACT |
| EDGE-019 | Wire Cucumber integration test infrastructure (Testcontainers PostgreSQL, Spring Security test stubs, device-credential mock, tenant schema setup) | BE | P8 | INTACT |
| EDGE-020 | Implement full BDD feature coverage — wire all 4 Gherkin features (~38 scenarios: device-registration-enrollment, device-pairing, heartbeat-liveness, profile-pointer-drift) | BE | P8 | INTACT |
| EDGE-021 | Implement optimistic-lock row_version enforcement (409 on stale write), client-scope Device detail endpoint (GET /devices/{id}), and DeviceListService for pagination | BE | P9 | INTACT |
| EDGE-022 | GET /api/v1/edge/profile — edge-service serve o perfil de deteccao ao dispositivo (fecha C-19 para a estacao no browser; desbloqueia a onda W10 do GEST) | BE | P1 | DONE |
| EDGE-INT-001 | Integration with GEST — DetectionProfile version pointer (Feign client, circuit breaker, degraded heartbeat fallback) | INT | P4 | INTACT |
| EDGE-INT-002 | Integration with ACS — OAuth2 JWT validation (jwk-set-uri) and per-device client_credentials registration coordination | INT | P5 | INTACT |
| EDGE-INT-003 | Integration with GWY — upstream route /api/v1/edge/** + enrollment route without Bearer + X-Correlation-Id forwarding | INT | P4 | INTACT |
| EDGE-INT-004 | Integration with REG — Eureka service discovery registration and graceful deregistration | INT | P4 | INTACT |
| EDGE-INT-005 | Integration with MIG — contribute EDGE DDL (V0700..V0799) to centralized migration service | INT | P3 | INTACT |
| EDGE-INT-006 | Integration with PAC — provide EdgeDevicePort (GET /patients/{id}/device and GET /devices/{id}) for device validation | INT | P4 | INTACT |
| EDGE-INT-007 | Integration with DASH — provide liveness poll endpoints (ETag/304, serverTime in response body) for caregiver connectivity signal | INT | P4 | INTACT |
| DASH-001 | Create dash-service Gradle subproject (Spring Boot, no-DB, hexagonal skeleton) | BE | P1 | INTACT |
| DASH-002 | Define hexagonal outbound ports for all upstream integrations | BE | P1 | INTACT |
| DASH-003 | Define transient view-model and envelope value objects (no JPA entities) | BE | P1 | INTACT |
| DASH-004 | Implement PanelSession in-memory registry with heartbeat eviction | BE | P2 | INTACT |
| DASH-005 | Implement PanelStreamController: GET /panel/stream SSE channel with heartbeat | BE | P2 | INTACT |
| DASH-006 | Implement InternalNotificationController: POST /internal/notifications dispatch sink with dispatchId idempotency | BE | P2 | INTACT |
| DASH-007 | Implement GET /panel/summary: cross-module home view-model composition (EVNT + EDGE + ACS) | BE | P3 | INTACT |
| DASH-008 | Implement GET /panel/patients/{patientId}/overview: per-patient composition (PAC + EDGE) | BE | P3 | INTACT |
| DASH-009 | Implement GET /panel/patients/{patientId}/history: paged read-through to HIST (size cap 100) | BE | P3 | INTACT |
| DASH-010 | Implement GET /panel/patients/{patientId}/device: EDGE liveness poll with ETag/304 and staleness threshold | BE | P3 | INTACT |
| DASH-011 | Implement on-connect catch-up replay: GET /panel/notifications/unacknowledged + catchup-complete SSE event | BE | P3 | INTACT |
| DASH-012 | Implement long-poll fallback: GET /panel/notifications/poll (waitSeconds<=30, cursor) | BE | P3 | INTACT |
| DASH-013 | Implement PUT /panel/patients/{id}/commands/{cmd}/calibration: write-through to PAC with error relay | BE | P4 | INTACT |
| DASH-014 | Implement POST /panel/events/{eventId}/confirm: write-through to EVNT (feedback verdict relay) | BE | P4 | INTACT |
| DASH-015 | Implement POST /panel/notifications/{dispatchId}/acknowledge: write-through to EVNT callback (idempotent) | BE | P4 | INTACT |
| DASH-016 | Implement AcsRestAdapter (IdentityDirectoryPort): JWK-set cache + scope resolution cache (TTL=30s) | BE | P5 | INTACT |
| DASH-017 | Implement PacRestAdapter (CommandCatalogPort): catalog read + calibration write-through forwarding | BE | P5 | INTACT |
| DASH-018 | Implement EvntEventRestAdapter + EvntDispatchCallbackAdapter (EventStatePort + NotificationDispatchBackPort) | BE | P5 | INTACT |
| DASH-019 | Implement HistRestAdapter (HistoryQueryPort): paged audit query forwarding to HIST | BE | P5 | INTACT |
| DASH-020 | Implement EdgeLivenessRestAdapter (DeviceLivenessPort): conditional GET + ETag + staleness-to-unknown mapping | BE | P5 | INTACT |
| DASH-021 | Configure OAuth2 resource server: panel JWT validation (JWK set) + SVC_EVNT authority for /internal/** | BE | P6 | INTACT |
| DASH-022 | Implement degradation matrix: per-upstream circuit breaker (soft-degrade EDGE/EVNT-read; hard-503 ACS/PAC/HIST) | BE | P6 | INTACT |
| DASH-023 | Configure Actuator, structured logging, MDC X-Correlation-Id propagation and health indicators | BE | P7 | INTACT |
| DASH-024 | Implement Cucumber BDD feature tests for home-status-calm, notification-delivery and calibration write-through | BE | P8 | INTACT |
| TREI-001 | Domain entities + JPA mappings (TREI-private tables: sessao, proposta, conteudo_autoria, outbox) | BE | P1 | INTACT |
| TREI-002 | Flyway migrations V0201-V0203 (shipped to MIG service, ordered after CONV conv_* tables) | BE | P1 | INTACT |
| TREI-003 | Hexagonal port interfaces (LlmAuthoringPort, EmbeddingPort, TtsSynthesisPort, AudioStorePort, IndexPort, AcsSessionPort, PacPatientPort, VocabularyPort, HistAuditPort) | BE | P1 | INTACT |
| TREI-004 | REST DTOs + API controllers (9 endpoints: sessoes, propostas, nos, aprovacoes/cuidadora, aprovacoes/paciente, nos/{id}, DELETE nos/{id}, publicacao, health) | BE | P1 | INTACT |
| TREI-005 | SessaoTreinamentoService — startSession (patient resolution via PAC, session creation, AI disclosure) + requestProposal (Anthropic call via LlmAuthoringPort, persist proposta before return, SEC-002 PHI guard) | BE | P2 | INTACT |
| TREI-006 | Dual authorization gate — approveCaregiver + approvePatient + DualAuthorizationGateCleared event (ADR-003, SPEC-03: different scopes, different actors, idempotent no-op on re-approval) | BE | P2 | INTACT |
| TREI-007 | NoAutoriaService — materializeNode (conv_no_arvore_fala + conv_no_conteudo pendente + trei_conteudo_autoria rascunho) + discardNode (soft delete, 409 on ativo, ADR-018 locale-invariant structure) | BE | P2 | INTACT |
| TREI-008 | JPA repositories + adapters for TREI-private tables (SessaoRepository, PropostaRepository, ConteudoAutoriaRepository, OutboxRepository) | BE | P3 | INTACT |
| TREI-009 | JPA write adapters for shared CONV tables (conv_no_arvore_fala insert, conv_no_conteudo insert/update approval flags/status flip, data-model §0 non-duplication rule) | BE | P3 | INTACT |
| TREI-010 | LlmAuthoringPort adapter — AnthropicAdapter (session-scoped, ANTHROPIC_MODEL_ID recorded, SEC-002 PHI guard, 503 on unreachable, no partial proposta row) | BE | P4 | INTACT |
| TREI-011 | EmbeddingPort adapter — EmbeddingSidecarAdapter + G5ModelIdGuard (SPEC-11: TREI_EMBED_MODEL_ID vs sidecar runtime id, fail-closed, 768-d cosine-normalized vector) | BE | P4 | INTACT |
| TREI-012 | TtsSynthesisPort + ElevenLabsAdapter + AudioStorePort + FilesystemAudioStoreAdapter (idempotent once per (no_id, locale), TREI_AUDIO_ROOT == CONV_AUDIO_ROOT, FIN-002, SEC-002) | BE | P4 | INTACT |
| TREI-013 | IndexPort adapter — ElasticsearchIndexAdapter from elastic-stack-client-lib (8-field traceability block, _id=no_id_locale, dims=768 cosine, DLQ on incomplete write, integration-contracts §4) | BE | P4 | INTACT |
| TREI-014 | OAuth2 Resource Server config + schema-per-empresa tenant routing (SPEC-06, Learning D-492: jwk-set-uri NOT issuer-uri in application-test.yml, TenantContextHolder) | BE | P5 | INTACT |
| TREI-015 | ACS revocation blacklist + dual-actor scope enforcement (AcsSessionAdapter, RevocationFilter, 403 wrong-actor on approval endpoints, fail-closed, ADR-003/ADR-012) | BE | P5 | INTACT |
| TREI-016 | PublicacaoService + OutboxDrainer (scheduled retryable embed->tts->index, G5 gate at embed step, locale ativo only after all 3 ok, SpeechNodePublished event, ADR-004, SPEC-12) | BE | P6 | INTACT |
| TREI-017 | Idempotency guards (SPEC-05: duplicate approval no-op, TTS file re-use check before ElevenLabs call, outbox UNIQUE(no_id,locale,passo) replay-safe, gate fires exactly once) | BE | P6 | INTACT |
| TREI-018 | Health endpoint GET /health (all-dependency status: Anthropic, ElevenLabs, embeddingSidecar, ES, audioStore, ACS; embeddingModelId surface, degradedCapabilities list, SPEC-07, api-contract §2.9) | BE | P7 | INTACT |
| TREI-019 | Structured logging + X-Correlation-Id propagation (SPEC-07: inject if absent, echo on response, propagate to Anthropic/ElevenLabs/sidecar/ES/ACS/HIST, MDC) | BE | P7 | INTACT |
| TREI-020 | Cucumber BDD: dual-authorization-gate.feature + llm-authoring-proposal.feature step definitions (SPEC-08, D-492 pitfall: jwk-set-uri in application-test.yml) | BE | P8 | INTACT |
| TREI-021 | Cucumber BDD: publication-pipeline.feature + bilingual-locale-independence.feature + embedding-model-parity-g5.feature step definitions (SPEC-08, SPEC-11) | BE | P8 | INTACT |
| TREI-022 | Outbox retry backoff + circuit-breaker for LLM/TTS + outbox-falha alert surface via GET /health and GET /nos/{id}/publicacao (SPEC-07, SPEC-09) | BE | P9 | INTACT |
| INT-TREI-001 | INT: TREI -> ACS (per-request revocation blacklist via AcsSessionPort, jwt.jwk-set-uri signature validation, 401 session-revoked, 403 wrong-actor on dual-approval endpoints, fail-closed, ADR-003/ADR-012) | BE | P5 | INTACT |
| INT-TREI-002 | INT: TREI -> PAC (patient resolution at session start via PacPatientPort, opaque engine_feature_ref passthrough via VocabularyPort, client_credentials, hub-integration §2) | BE | P4 | INTACT |
| INT-TREI-003 | INT: TREI -> HIST audit (all 5 facts: proposal.created, approval.caregiver, approval.patient, node.published, node.discarded — no clinical text, HIST outage buffered by outbox, event-contracts §4) | BE | P4 | INTACT |
| INT-TREI-004 | INT: TREI -> ES IndexPort (embed step: 768-d vector + embedding_model_id G5 traceability; index step: upsert _id=no_id_locale with 8-field block, integration-contracts §4, dims=768 cosine strict mapping) | BE | P4 | INTACT |
| INT-TREI-005 | INT: TREI <-> CONV shared state (conv_no_arvore_fala insert, conv_no_conteudo approval flags + status flip pendente->ativo, audio_ref write, TREI_AUDIO_ROOT==CONV_AUDIO_ROOT, data-model §0 non-duplication, G6) | BE | P6 | INTACT |
