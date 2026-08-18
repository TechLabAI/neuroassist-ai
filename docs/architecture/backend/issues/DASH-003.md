# DASH-003 -- Define transient view-model and envelope value objects (no JPA entities)

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

DASH nao persiste nenhum agregado de dominio (ADR-004). Toda informacao renderizada e composta
on-the-fly a partir dos servicos donos. Os value objects aqui sao transientes: montados por request
e descartados apos o response. Nenhum deve ter anotacao JPA.

## Spec de referencia

- SPEC-02 (composicao BFF: view models)
- SPEC-04 (postura zero-persistencia: PanelViewModel, NotificationEnvelope e PanelSession sao in-memory)

## Escopo

- [ ] `domain/model/SummaryView` — view model do home dashboard (lista de pacientes, latestEvent, unacknowledgedCount, degradedSections)
- [ ] `domain/model/PatientOverview` — view model de overview por paciente (header + commands + device)
- [ ] `domain/model/HistoryPage` — view model paginado de historico (items[] + metadata de pagina)
- [ ] `domain/model/DeviceStatus` — status de liveness (status enum: online/offline/unknown, lastSeenAt, degraded flag)
- [ ] `domain/model/NotificationEnvelope` — envelope de notificacao in-flight (dispatchId, tier, targetCaregiverId, patientId, commandLabel, urgencia, raisedAt)

## Resultado esperado

Cinco records/classes Java imutaveis (ou Kotlin data classes) em `com.neuroassist.dash.domain.model`,
sem anotacoes JPA, usaveis como tipos de retorno dos ports e inputs dos controllers.

## Criterios de aceite

- [ ] Cinco value objects presentes em `com.neuroassist.dash.domain.model`
- [ ] Nenhuma anotacao `@Entity`, `@Table`, `@Column` nos value objects
- [ ] `SummaryView` inclui `List<PatientSummary>` e `List<String> degradedSections`
- [ ] `NotificationEnvelope` inclui `dispatchId` (String UUID) como campo obrigatorio
- [ ] `./gradlew :dash-service:compileJava` sem erros

## Dependencias

- DASH-001 (estrutura de pacotes)

## Estimativa

S

## Prioridade

P1
