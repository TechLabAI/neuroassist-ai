# TREI-003 -- Hexagonal port interfaces (todos os outbound ports do serviço)

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/9 itens

## Contexto

Declara os contratos de saída do TREI seguindo a arquitetura hexagonal
(`ADR-*-hexagonal-architecture.md`). Cada port é uma interface Java no pacote `domain.port`;
as implementações (adapters) ficam em `infrastructure`. Nenhum adapter é criado aqui —
apenas as interfaces para compilar o `application` layer.

## Spec de referencia

- SPEC-01 (api-surface — portas de saída implícitas nos endpoints)
- SPEC-10 (integrations — external ports: LlmAuthoringPort, EmbeddingPort, TtsSynthesisPort, AudioStorePort, IndexPort)
- hub-integration.md §1-3 (AcsSessionPort, PacPatientPort, VocabularyPort, HistAuditPort)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/domain/port/LlmAuthoringPort.java` — `propose(SessaoTreinamento, String instrucao) : List<PropostaLlm>` (integration-contracts §1)
- [ ] criar `src/main/java/com/neuroassist/trei/domain/port/EmbeddingPort.java` — `embed(String texto) : EmbeddingResult` onde `EmbeddingResult` carrega o vetor 768-d e o `modelId` reportado (SPEC-11, G5)
- [ ] criar `src/main/java/com/neuroassist/trei/domain/port/TtsSynthesisPort.java` — `synthesize(String texto, String locale, UUID pacienteId) : AudioSynthesisResult` (integration-contracts §3)
- [ ] criar `src/main/java/com/neuroassist/trei/domain/port/AudioStorePort.java` — `store(UUID empresa, UUID pacienteId, String locale, UUID noId, byte[] audio, AudioFormato formato) : String` retorna `audioRef` (integration-contracts §5)
- [ ] criar `src/main/java/com/neuroassist/trei/domain/port/IndexPort.java` — `index(IndexDocument doc) : void` onde `IndexDocument` carrega os 8 campos de traceability + texto + embedding + audioRef (integration-contracts §4)
- [ ] criar `src/main/java/com/neuroassist/trei/domain/port/AcsSessionPort.java` — `isRevoked(String jti) : boolean` (hub-integration §1, fail-closed)
- [ ] criar `src/main/java/com/neuroassist/trei/domain/port/PacPatientPort.java` — `resolvePatient(UUID pacienteId, UUID empresa) : Optional<PacPatientInfo>` (hub-integration §2)
- [ ] criar `src/main/java/com/neuroassist/trei/domain/port/VocabularyPort.java` — `resolveEngineFeatureRef(UUID pacienteId, String opaquRef) : Optional<String>` (hub-integration §2, opaque passthrough)
- [ ] criar `src/main/java/com/neuroassist/trei/domain/port/HistAuditPort.java` — `audit(AuditFact fact) : void` (hub-integration §3, best-effort — exceptions capturadas e buffered)

## Resultado esperado

9 interfaces compilando; nenhum adapter criado nesta issue. O application layer pode
declarar dependências nos ports sem depender de infrastructure.

## Criterios de aceite

- [ ] `./gradlew :trei-service:compileJava` sai 0 com todos os 9 ports declarados
- [ ] Nenhum port importa classes de `infrastructure.*` (camadas hexagonais: domain não conhece infrastructure)
- [ ] `EmbeddingPort` retorna `EmbeddingResult` com campo `modelId : String` (necessário para a verificação G5 no adapter TREI-011)

## Dependencias

- TREI-001 (precede — enums e types do domínio devem existir)

## Estimativa

S

## Prioridade

P1
