<!-- OUTPUT-SCHEMA
- Status
- §0 Leitura executiva
- §1 Achados
- §2 Estratégia
- §3 Prioridades
- §4 Riscos
- §5 Critérios de aceite
-->

# Plano — EDGE (edge-service)

## Status
**Tipo:** Incremental especifico
**Resumo:** P1-P13 concluidos, score 85/100 Maduro. Este plano adiciona a P14 (issue EDGE-022): `GET /api/v1/edge/profile` — o endpoint que serve o perfil de detecção completo ao dispositivo (browser-estação), fechando o defeito C-19 e desbloqueando a onda W10 do GEST.

---

## §0 Leitura executiva

O edge-service esta em producao (P1-P13, 160/160 testes, score 85). Ha uma lacuna cirurgica: a estacao do paciente (browser, ADR-007/ADR-008) autentica-se como dispositivo (`edge.device`) mas nao consegue buscar o proprio perfil de deteccao — o GEST exige `gest.detection-profile.read`, que um token de dispositivo nunca tem (defeito C-19, documentado no Javadoc do `DetectionProfileClient`). Sem o perfil a estacao nao sabe o que detectar nem qual `pacienteId` usar no `POST /api/v1/reports`, quebrando a cadeia gesto-alarme. A solucao e um novo endpoint no edge-service que busca o perfil completo via identidade de servico (ja configurada em `GestServiceIdentityFeignConfig`) e o entrega ao dispositivo. Nenhum alargamento de authorities do token de dispositivo.

---

## §1 Achados

Estado: score 85/100 Maduro (P1-P13 concluidos). EDGE-022: 0/7 criterios (sem plano). Gap cirurgico: nenhum endpoint serve o perfil completo ao dispositivo.

### Estado atual (ES + plan-state)
- Score: 85/100 Maduro. Last implement: P11/P12/P13 (dry-run removido, 37 cenarios BDD reais, 0 stubs). Last validate: 160/160 unit PASS, 37/37 Cucumber PASS.
- EDGE-022: 0/7 criterios atendidos (status: ABERTA, maturidade 0 — Inicial). Sem plano vigente para esta issue.
- `plan-state.json` sugere: INCREMENTAL (sem inconsistencias estruturais).

### Pontos fortes existentes
- `DetectionProfileClient` + `GestServiceIdentityFeignConfig` + `ServiceIdentityRequestInterceptor` ja existem e funcionam (EDGE-014).
- `DetectionProfileFeignAdapter` demonstra o padrao correto: `@CircuitBreaker` com `ignoreExceptions` configurado, fallback explicito, `ServiceTokenUnavailableException` relancada (nao engolida).
- `DispositivoEdge.pacienteId` + `PairingState` disponivel no dominio.
- `HeartbeatController` e o padrao de identidade a seguir: `deviceId` do claim `sub`, sem parametro de path/query/corpo.
- `CodedException` + `GlobalExceptionHandler` ja mapeiam codigos para ProblemDetail (RFC 7807).

### Gaps
- Nenhum endpoint `GET /api/v1/edge/profile` existe.
- `GestDetectionProfileView` contem apenas `long version` — precisa crescer para incluir todos os campos que a estacao usa.
- Nenhum outbound port para busca de perfil completo (so existe `DetectionProfilePort` para o ponteiro de versao).
- Nenhum use case / application service para entrega de perfil ao dispositivo.
- Nenhum cenario BDD cobrindo este fluxo.

### Aderencia as regras (RULE-01..09)
- RULE-01/02: dominio sem Spring/JPA — nenhuma alteracao de dominio nesta issue.
- RULE-03: `@Transactional` apenas em `application.service` — `GetDeviceProfileService` nao precisa de transacao (leitura de dispositivo + chamada Feign); o `@Transactional` nao deve ser aplicado.
- RULE-04: `api.*` nao depende de `infrastructure.*` — o controller recebe `DeviceProfileResponse` do use case, nunca `GestDetectionProfileView`.
- RULE-05: `infrastructure.*` nao depende de `api.*` — respeitado.
- RULE-06: `@FeignClient` apenas em `infrastructure.clients` — o `DetectionProfileClient` ja esta la; o novo metodo vai no mesmo lugar.
- RULE-07: `TenantContextHolder.require()` e a primeira chamada em `GetDeviceProfileService`.
- RULE-08: `spring.jpa.open-in-view=false` — ja configurado, nenhuma alteracao.
- RULE-09: pacote base `com.neuroassist.edge` — todos os novos arquivos seguem este pacote.

### TODO-LIVE encontrados (fora do escopo — reportar apenas)
- `DetectionProfileClient` Javadoc cita C-19 como defeito. Esta issue fecha o C-19, mas o Javadoc deve ser atualizado para refletir isso.

---

## §2 Estratégia

Incremental especifico: uma prioridade (P14/EDGE-022) que fecha C-19 sem tocar no dominio nem nas prioridades anteriores.

### Objetivo macro
Implementar `GET /api/v1/edge/profile` no edge-service: guarda-corpo `edge.device`, identidade extraida do `sub`, verificacao de pareamento, busca de perfil completo via `DetectionProfileClient` (identidade de servico), repasse de GET condicional (If-None-Match / 304), degradacao explicita se GEST indisponivel.

### Ordem das prioridades

| P  | Titulo                              | Motivo                                                        | Issues      |
|----|-------------------------------------|---------------------------------------------------------------|-------------|
| P1 | GET /api/v1/edge/profile — estacao  | Fecha C-19, desbloqueia W10 GEST (FE-028, FE-030, FE-031, FE-032) | EDGE-022 |

---

## §3 Prioridades

Uma prioridade (P1 neste plano incremental): `GET /api/v1/edge/profile`. Cobre EDGE-022 integralmente.

### Prioridade 1 — GET /api/v1/edge/profile (perfil de detecção para a estação)

**Status:** pendente
**Issues cobertas:** EDGE-022

#### Objetivo
Criar o endpoint `GET /api/v1/edge/profile` no edge-service. Um dispositivo pareado obtém em uma chamada: o que detectar (`primitives[].engineFeatureRef`), com qual sensibilidade (`thresholds` + `sensibilidade` por primitivo) e de quem (`pacienteId`). Token de usuario recebe 403. Dispositivo nao pareado recebe 403 `edge.device-not-paired`. GEST indisponivel gera degradacao explicita — jamais perfil vazio.

#### Problema identificado
Token `edge.device` alcanca apenas `POST /api/v1/edge/heartbeat` e `POST /api/v1/reports` em todo o sistema. O acesso ao perfil completo no GEST exige `gest.detection-profile.read`, que um dispositivo nao tem (C-19). O `DetectionProfileClient` ja usa identidade de servico precisamente por isso — o intermediario existe, mas o endpoint que o expoe ao dispositivo nunca foi criado.

#### Correcoes mandatorias
1. Expandir `GestDetectionProfileView` para espelhar `DetectionProfileResponse` do gest-service nos campos que a estacao precisa. Manter `@JsonIgnoreProperties(ignoreUnknown = true)`. Garantir que o record e `public` e todos os records aninhados tambem sao `public` (RULE: records package-private bloqueiam proxies JDK).
2. Adicionar metodo `getProfileForDeviceConditional` em `DetectionProfileClient` aceitando `@RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch`. Nao remover o metodo existente `getActiveProfileForDevice` (usado pelo `DetectionProfileFeignAdapter`).
3. Criar `GetFullDetectionProfilePort` em `application/port/out` — outbound port para busca de perfil completo, distinto de `DetectionProfilePort` (que retorna apenas ponteiro de versao).
4. Criar `DeviceProfileFeignAdapter` em `infrastructure/clients` implementando `GetFullDetectionProfilePort`. Reusar `DetectionProfileClient`. Circuit breaker com `ignoreExceptions` configurado. `ServiceTokenUnavailableException` relancada (nao engolida). Retorno diferenciado: `ProfileResult.ok(view)`, `ProfileResult.notModified()`, `ProfileResult.degraded()`. Nao usar fallback que retorne perfil vazio.
5. Criar `GetDeviceProfileUseCase` em `application/port/in`.
6. Criar `GetDeviceProfileService` em `application/service`. Primeira linha: `TenantContextHolder.require()`. Carregar dispositivo via `DispositivoEdgeRepositoryPort.findByIdAndTenantId`. Verificar `pairingState == PairingState.paired`; se nao: lancar `CodedException("edge.device-not-paired")`. Chamar `GetFullDetectionProfilePort`. Se degradado: lancar `CodedException("edge.profile-unavailable")` com mensagem que distinga de "perfil nao configurado". Nao e `@Transactional` (nenhuma escrita em DB neste fluxo).
7. Criar `DeviceProfileResponse` em `api/dto` como `public record`. Campos: espelhar o que a estacao precisa de `GestDetectionProfileView`. Nao importar `GestDetectionProfileView` no record — o mapeamento ocorre no service.
8. Criar `DeviceProfileController` em `api/controller`. `@PreAuthorize("hasAuthority('edge.device')")`. `GET /api/v1/edge/profile`. `deviceId` do `auth.getName()` (mesmo padrao do `HeartbeatController`). Propagar `If-None-Match` do request para o use case; se resultado for `notModified`, retornar `ResponseEntity.status(304).build()`. Sem parametro `deviceId` em path, query ou corpo — identidade fechada por construcao. Nao importar nada de `infrastructure.*`.
9. Atualizar o Javadoc do `DetectionProfileClient` para refletir que C-19 foi fechado por este endpoint.

#### Alvos de alteracao (caminhos completos)

**Novos arquivos:**
- `backend/services/edge-service/src/main/java/com/neuroassist/edge/application/port/out/GetFullDetectionProfilePort.java`
- `backend/services/edge-service/src/main/java/com/neuroassist/edge/application/port/in/GetDeviceProfileUseCase.java`
- `backend/services/edge-service/src/main/java/com/neuroassist/edge/application/service/GetDeviceProfileService.java`
- `backend/services/edge-service/src/main/java/com/neuroassist/edge/infrastructure/clients/DeviceProfileFeignAdapter.java`
- `backend/services/edge-service/src/main/java/com/neuroassist/edge/api/dto/DeviceProfileResponse.java`
- `backend/services/edge-service/src/main/java/com/neuroassist/edge/api/controller/DeviceProfileController.java`
- `backend/services/edge-service/src/test/java/com/neuroassist/edge/application/service/GetDeviceProfileServiceTest.java`
- `backend/services/edge-service/src/test/resources/features/device-profile.feature`
- `backend/services/edge-service/src/test/java/com/neuroassist/edge/bdd/steps/DeviceProfileSteps.java`

**Arquivos alterados:**
- `backend/services/edge-service/src/main/java/com/neuroassist/edge/infrastructure/clients/GestDetectionProfileView.java` (expansao de campos)
- `backend/services/edge-service/src/main/java/com/neuroassist/edge/infrastructure/clients/DetectionProfileClient.java` (adicao de metodo condicional + atualizacao de Javadoc)
- `backend/services/edge-service/src/main/resources/application.yml` (se necessario: configurar circuit breaker `edge-device-profile` separado do `gest-detection-profile`)

#### Contrato operacional

**Deve existir:**
- `GET /api/v1/edge/profile` respondendo `200 OK` com `DeviceProfileResponse` para token `edge.device` + dispositivo pareado.
- `ETag` e `Cache-Control: max-age=60` propagados do GEST na resposta 200.
- `304 Not Modified` sem corpo quando `If-None-Match` do cliente bate com o ETag corrente do GEST.
- `403` com codigo `edge.device-not-paired` para dispositivo registrado mas nao pareado.
- `403` com codigo `edge.identity-mismatch` ou `access-denied` para token de usuario (nao `edge.device`).
- `503` (ou `502`) com codigo `edge.profile-unavailable` quando GEST esta indisponivel — nunca `200` com corpo vazio.

**Nao deve existir:**
- Parametro `deviceId` em path, query string ou corpo da requisicao.
- Importacao de `GestDetectionProfileView` em qualquer classe fora de `infrastructure.clients`.
- `@Transactional` em `GetDeviceProfileService`.
- `@Primary` duplicado: `DeviceProfileFeignAdapter` nao usa `@Primary`; `DetectionProfileFeignAdapter` ja tem `@Primary` para `DetectionProfilePort`. Sao portas diferentes.

**Verificacao objetiva:**
- `./gradlew :services:edge-service:architectureCheck` — exit 0.
- `./gradlew :services:edge-service:test` — 160 + novos testes unit PASS.
- `./gradlew :services:edge-service:integrationTest` — 37 + novos cenarios BDD PASS.
- `curl -H "Authorization: Bearer <user_token>" /api/v1/edge/profile` retorna 403.
- `curl -H "Authorization: Bearer <device_token>" /api/v1/edge/profile` retorna 200 com `pacienteId` nao nulo.

**Condicao de bloqueio:**
- `architectureCheck` falha — nao commitar.
- GEST indisponivel faz o endpoint retornar `200` com corpo vazio — parar, corrigir fallback.

#### Sequencia de execucao

**Fase 1 — Domain (sem alteracoes)**
Nenhuma mudanca no dominio: `DispositivoEdge`, `PairingState`, `CodedException` ja existem e cobrem todos os cenarios desta issue.

**Fase 2 — Outbound port**
Criar `GetFullDetectionProfilePort` em `application/port/out`. Definir o `ProfileResult` selado (sealed interface ou record com variantes `ok`, `notModified`, `degraded`). Zero Spring, zero JPA.

**Fase 3 — Inbound port**
Criar `GetDeviceProfileUseCase` em `application/port/in`. Metodo `getDeviceProfile(UUID deviceId, String tenantId, String ifNoneMatch)` retornando `GetDeviceProfileUseCase.ProfileResult` (reutilizar ou reexpor o tipo do outbound port — decidir: se reutilizar o tipo do port, a interface de use case deve permanecer coerente com o padrao hexagonal; preferir um tipo proprio do inbound port para evitar vazamento de infra para a api).

**Fase 4 — Application service**
Criar `GetDeviceProfileService` em `application/service`. Nao e `@Transactional`. Primeira linha do metodo publico: `String tenantId = TenantContextHolder.require()`. Carregar dispositivo. Verificar pareamento. Chamar port. Mapear resultado para tipo do use case. Se `degraded`: lancar `CodedException("edge.profile-unavailable", "GEST unavailable — cannot serve detection profile")`.

**Fase 5 — Infrastructure**

*5a — Expandir `GestDetectionProfileView`:*
Adicionar campos: `UUID pacienteId`, `boolean catalogoDegradado`, `String etag`, `GestDetectionThresholdTableView thresholds`, `List<GestProfilePrimitiveView> primitives`. Criar records aninhados `public`:
- `public record GestDetectionThresholdTableView(GestDetectionThresholdBandView baixa, GestDetectionThresholdBandView media, GestDetectionThresholdBandView alta)`
- `public record GestDetectionThresholdBandView(double earClose, double earOpen, double earOtherMax, int blinkMinFrames, int doubleBlinkWindowMs, double briRaiseOn, double briRaiseOff, double briOtherMax, int browMinFrames)`
- `public record GestProfilePrimitiveView(UUID tipoGestoId, String nome, String engineFeatureRef, String sensibilidade, String origem)`
Manter `@JsonIgnoreProperties(ignoreUnknown = true)` em `GestDetectionProfileView`. Verificar que `version` ainda e deserializavel (nao remover); o `DetectionProfileFeignAdapter` existente usa `response.getBody().version()` e deve continuar funcionando.

*5b — Adicionar metodo ao `DetectionProfileClient`:*
```java
@GetMapping("/gest-service/detection-profile/devices/{deviceId}")
ResponseEntity<GestDetectionProfileView> getProfileForDeviceConditional(
    @PathVariable("deviceId") UUID deviceId,
    @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch);
```
Manter o metodo existente `getActiveProfileForDevice` intacto — usado pelo `DetectionProfileFeignAdapter`. Atualizar Javadoc: C-19 esta fechado por `DeviceProfileController`.

*5c — Criar `DeviceProfileFeignAdapter`:*
Implementa `GetFullDetectionProfilePort`. Injeta `DetectionProfileClient`. Circuit breaker `edge-device-profile` (circuito separado do `gest-detection-profile` para nao interferir com heartbeats). `ignoreExceptions` deve incluir `FeignException.NotFound` (404 nao e falha de GEST). `ServiceTokenUnavailableException` relancada. Fallback: retorna `GetFullDetectionProfilePort.ProfileResult.degraded()`. `304` do GEST → `ProfileResult.notModified()`. `200` do GEST → `ProfileResult.ok(view)`. Nao usa `@Primary`.

**Fase 6 — API**

*6a — `DeviceProfileResponse` em `api/dto`:*
`public record DeviceProfileResponse(UUID pacienteId, long version, String etag, Object thresholds, List<?> primitives, boolean catalogoDegradado)`. Os tipos de `thresholds` e `primitives` devem ser records proprios da camada `api/dto` (ex.: `DeviceProfileThresholdTableResponse`, `DeviceProfilePrimitiveResponse`) — nao importar tipos de `infrastructure.clients`. O mapeamento de `GestDetectionProfileView` para `DeviceProfileResponse` ocorre no `GetDeviceProfileService` (ou em um metodo estatico `from(GestDetectionProfileView)` no proprio `DeviceProfileResponse`, sem importar classes de infra — se necessario, aceitar os campos primitivos e mapear no service).

*Decisao do planner:* O service recebe o `GestDetectionProfileView` como resultado do port (o port retorna `ProfileResult.ok(view)` onde `view` e `GestDetectionProfileView`). O service extrai os campos e constroi `DeviceProfileResponse` diretamente — nao expoe o tipo Feign para a camada de api. O `GetFullDetectionProfilePort.ProfileResult.ok` carrega `GestDetectionProfileView`.

*6b — `DeviceProfileController` em `api/controller`:*
```java
@GetMapping
@PreAuthorize("hasAuthority('edge.device')")
public ResponseEntity<DeviceProfileResponse> getProfile(
    Authentication auth,
    @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch)
```
`deviceId` extraido de `auth.getName()` (mesmo padrao de `HeartbeatController`). Nao aceitar `deviceId` como parametro. Retornar `ResponseEntity.status(HttpStatus.NOT_MODIFIED).build()` para `notModified`. Propagar `ETag` e `Cache-Control: max-age=60` na resposta 200. Nenhuma importacao de `infrastructure.*`.

#### Politica de testes

**Unit (`GetDeviceProfileServiceTest`):**
- Dispositivo nao encontrado → `CodedException("edge.device-not-found")`.
- Dispositivo encontrado, `pairingState != paired` → `CodedException("edge.device-not-paired")`.
- Dispositivo pareado, GEST retorna `ok` → `DeviceProfileResponse` com `pacienteId` correto.
- Dispositivo pareado, GEST retorna `notModified` → resultado `notModified` propagado ao caller.
- Dispositivo pareado, GEST retorna `degraded` → `CodedException("edge.profile-unavailable")`.
- `TenantContextHolder.require()` e a primeira chamada (verificar via verify no Mockito).

**BDD (`device-profile.feature` + `DeviceProfileSteps`):**
- Cenario 1: token `edge.device` + dispositivo pareado → `200` com perfil completo (WireMock para GEST).
- Cenario 2: token de usuario (qualquer outra authority) → `403`.
- Cenario 3: token `edge.device` + dispositivo nao pareado → `403` com `edge.device-not-paired`.
- Cenario 4: token `edge.device` + `If-None-Match` igual ao ETag corrente → `304` sem corpo.
- Cenario 5: token `edge.device` + GEST indisponivel (WireMock simula 503) → resposta de erro explicita, nunca `200` com corpo vazio.

**Integration:** `./gradlew :services:edge-service:integrationTest` deve rodar todos os 5 cenarios novos (via `CucumberIT`). Nenhum cenario pode ser marcado como `@Ignore` ou ter step stub com `// TODO`.

#### Multi-empresa
`TenantContextHolder.require()` e a primeira chamada em `GetDeviceProfileService`. O `findByIdAndTenantId` ja e tenant-safe. Nenhum dado cross-tenant pode ser acessado: o `tenantId` vem do JWT do dispositivo e e propagado para a query de dispositivo e para a chamada ao GEST via `GestServiceIdentityFeignConfig` (identidade de servico — nao carrega o JWT do dispositivo). O GEST resolve o tenant pelo pareamento registrado.

#### Migracoes Flyway
Nenhuma. Nenhum novo campo de DB, nenhum novo schema. O `pacienteId` ja existe em `dispositivo_edge` (coluna adicionada em P anteriores).

#### Criterios de aceite
1. Token `edge.device` + dispositivo pareado → `200` com `pacienteId` nao nulo, `primitives` nao vazio, `thresholds` com tres faixas.
2. Token de usuario → `403` (qualquer authority diferente de `edge.device`).
3. Parametro `deviceId` em path, query ou corpo nao e aceito — o endpoint simplesmente nao declara esse parametro.
4. Dispositivo nao pareado → `403` com codigo `edge.device-not-paired`.
5. `If-None-Match` igual ao ETag corrente do perfil → `304` sem corpo.
6. GEST indisponivel → erro explicito (`5xx` ou `503`), nunca `200` com corpo vazio.
7. `./gradlew :services:edge-service:architectureCheck` — exit 0.
8. `./gradlew :services:edge-service:test` — todos os testes PASS (incluindo novos unit).
9. `./gradlew :services:edge-service:integrationTest` — todos os 5 cenarios BDD PASS (incluindo novos).

#### Riscos

**RISCO-1 (alto): GestDetectionProfileView usada pelo DetectionProfileFeignAdapter existente.**
A expansao de campos na view nao deve quebrar o `DetectionProfileFeignAdapter` que usa `response.getBody().version()`. Verificar: o campo `version` permanece como `long`; o Jackson desserializa os novos campos ignorando os nao mapeados originalmente (ja ha `@JsonIgnoreProperties(ignoreUnknown = true)`). Mitigacao: executar o `./gradlew :services:edge-service:test` para garantir que `HeartbeatServiceTest` e `ProfilePointerDriftSteps` continuam passando apos a expansao.

**RISCO-2 (medio): Circuit breaker `edge-device-profile` nao configurado em application.yml.**
Se o circuit breaker nao estiver declarado, o Resilience4j usa defaults que podem abrir o circuito com base em timeouts de GEST que nao refletem degradacao real. O implementador deve adicionar a secao `resilience4j.circuitbreaker.instances.edge-device-profile` em `application.yml` com os mesmos parametros do `gest-detection-profile`, incluindo `ignoreExceptions`.

**RISCO-3 (medio): Token de usuario alcancando o endpoint via role diferente.**
`@PreAuthorize("hasAuthority('edge.device')")` bloqueia qualquer token sem essa authority especifica. A cuidadora tem `acs.*`, a coordenadora tem `edge.read`, `edge.write` etc. Nenhuma delas tem `edge.device`. O cenario BDD deve testar com token de cuidadora (para garantir o 403) alem do token de usuario generico.

**RISCO-4 (baixo): `@Primary` duplicado se DeviceProfileFeignAdapter for anotado erroneamente.**
`DetectionProfileFeignAdapter` ja tem `@Primary` para `DetectionProfilePort`. `DeviceProfileFeignAdapter` implementa `GetFullDetectionProfilePort` — nao ha `@Primary` necessario, pois ha apenas um bean por porta. Verificar antes de commitar.

#### Instrucao para implementador

Execute na seguinte ordem:
1. Ler o `DetectionProfileController` do gest-service inteiro antes de escrever qualquer Feign method ou stub WireMock — o path, os headers e o response body devem ser espelho do produtor, nao da suposicao do consumidor.
2. Criar os arquivos na ordem da sequencia de execucao (Fase 2 → 3 → 4 → 5 → 6 → 7).
3. Apos a Fase 5a (expansao de `GestDetectionProfileView`), rodar `./gradlew :services:edge-service:test` para garantir que os testes existentes continuam passando.
4. Apos a Fase 6 completa, rodar `./gradlew :services:edge-service:architectureCheck`.
5. Escrever os cenarios BDD antes de escrever os steps (feature-first), entao implementar os steps com WireMock para o GEST.
6. Rodar `./gradlew :services:edge-service:integrationTest` ao final.
7. Nunca commitar com architectureCheck falhando.
8. Reportar qualquer TODO/FIXME/stub encontrado fora do escopo.

---

## §4 Riscos

Ver secao "Riscos" da Prioridade 1 acima. O risco dominante e a expansao de `GestDetectionProfileView` quebrar o `DetectionProfileFeignAdapter` existente — mitigado rodando o suite de unit apos a fase 5a antes de prosseguir.

---

## §5 Critérios de aceite

1. `GET /api/v1/edge/profile` existe e responde corretamente (200, 304, 403) conforme os cenarios BDD.
2. Token `edge.device` + dispositivo pareado → `200` com `pacienteId`, `thresholds` (tres faixas), `primitives[]` nao vazios.
3. Token de usuario → `403`.
4. `deviceId` nao e aceito como parametro externo em nenhuma forma.
5. `If-None-Match` correto → `304` sem corpo.
6. GEST indisponivel → erro explicito, jamais perfil vazio.
7. `architectureCheck` exit 0.
8. Suite de unit PASS (160 + novos).
9. Suite BDD PASS (37 + 5 novos cenarios).
10. Javadoc do `DetectionProfileClient` atualizado: C-19 fechado.
