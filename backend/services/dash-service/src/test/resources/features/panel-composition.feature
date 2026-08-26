# SPEC-02 — Panel composition (BFF) with per-section degradation

Feature: Panel composition composes owner services into caregiver-scoped view models

Background:
  Given a caregiver "Jamilly" authenticated with a valid ACS JWT for tenant "familia-amaral"
  And Jamilly's ACS scope contains patient "Marcio"

Scenario: Home summary composes patients, device liveness and event state
  Given PAC, EVNT and EDGE are all available
  When Jamilly requests GET /panel/summary
  Then the response status is 200
  And the summary contains patient "Marcio"
  And "Marcio" has a device status and a latest event
  And degradedSections is empty

# O nome vem do PAC (PacienteResponse.nome). Ate esta issue o painel colocava o proprio
# patientId no campo `name` — a cuidadora via um UUID onde deveria estar uma pessoa.
# O nome do cenario e distinto do id de proposito: uma asserçao `contains("Marcio")`
# passaria com o defeito vivo.
Scenario: The summary shows the patient's real name, not the id
  Given PAC, EVNT and EDGE are all available
  When Jamilly requests GET /panel/summary
  Then the response status is 200
  And the summary shows the real name "Marcio Amaral" for "Marcio"

# A unica coisa que este painel existe para fazer. `hasActiveEvent` era `false` literal no
# codigo — o painel JAMAIS indicou um pedido de ajuda em aberto. Um cenario que assertasse
# `false` passaria com o defeito vivo; este exige o `true`.
Scenario: An unanswered help request lights up the patient's indicator
  Given Marcio has an unanswered help request in EVNT
  When Jamilly requests GET /panel/summary
  Then the response status is 200
  And the summary marks "Marcio" as having an active help request

Scenario: EDGE unavailable soft-degrades the device indicator
  Given EDGE is unreachable
  When Jamilly requests GET /panel/summary
  Then the response status is 200
  And the device status for "Marcio" is "unknown"
  And the device indicator is marked degraded

# E2E-018 — "nao ha dispositivo" e "nao consegui perguntar" sao respostas diferentes.
# Antes as duas viravam degraded=true/unknown, e a cuidadora nao tinha como saber se estava
# vendo ausencia de equipamento ou ausencia de informacao.
Scenario: Patient without a paired device is not reported as degraded
  Given EDGE reports that the patient has no paired device
  When Jamilly requests GET /panel/summary
  Then the response status is 200
  And the device status for "Marcio" is "no-device"
  And the device indicator is not marked degraded

# EVNT fora do ar nao pode virar "ninguem esta pedindo ajuda": `hasActiveEvent` vai NULO.
Scenario: EVNT unavailable omits the event section but still returns 200
  Given EVNT is unreachable
  When Jamilly requests GET /panel/summary
  Then the response status is 200
  And the summary omits the latest event section
  And the event indicator for "Marcio" is unknown
  And degradedSections contains "evnt"

# O nome e cosmetico; o alarme nao e. Derrubar o painel inteiro porque o servico de NOMES
# caiu esconderia o pedido de ajuda por causa de um dado decorativo — por isso o PAC e soft
# AQUI (e continua hard no overview, onde o catalogo e o proprio conteudo da tela).
Scenario: PAC unavailable for names still shows the alarm indicator
  Given PAC does not answer the patient name lookup
  When Jamilly requests GET /panel/summary
  Then the response status is 200
  And the name for "Marcio" is unknown
  And the summary marks "Marcio" as having an active help request

Scenario: PAC unavailable makes the patient overview return 503
  Given PAC is unreachable
  When Jamilly requests GET /panel/patients/Marcio/overview
  Then the response status is 503
  And the ProblemDetail type ends with "upstream-unavailable"

Scenario: History filters and paging are forwarded to HIST
  Given HIST is available
  When Jamilly requests GET /panel/patients/Marcio/history with from "2026-08-01" and size "200"
  Then the response status is 200
  And HIST received filter from "2026-08-01"
  And HIST received size "100"
