# EDGE-022 -- GET /api/v1/edge/profile — o edge-service serve o perfil de detecção ao dispositivo (fecha C-19 para a estação no browser)

**Status:** DONE
**Maturidade:** 90 -- Maduro
**Done/Total:** 13/13 itens

## Contexto

A `GEST ADR-008` decidiu que a estação do paciente — a superfície que observa o paciente pela
webcam — autentica-se como **dispositivo**, não como usuário. Com a `ADR-007`, essa estação é o
browser, e não mais o agente Python.

Ao implementar a fase 2 (`GEST-FE-028`) apareceu o bloqueio: **um token de dispositivo não alcança
o perfil de detecção.**

```
DeviceTokenService.DEVICE_AUTHORITIES = List.of("edge.device")      // só isso

GET /gest-service/detection-profile/devices/{id}  exige  gest.detection-profile.read
GET /api/v1/edge/devices/{deviceId}               exige  edge.read | edge.internal
```

Varrendo os `@RestController` dos 7 serviços, um token `edge.device` alcança **exatamente dois
endpoints em todo o sistema**: `POST /api/v1/edge/heartbeat` e `POST /api/v1/reports`.

Isto **não é novidade para o backend** — está catalogado como **defeito C-19**, escrito no Javadoc
do `DetectionProfileClient`:

> *"GEST requires the authority `gest.detection-profile.read`, which a device token never has
> (defect C-19)."*

Foi exatamente por isso que o `DetectionProfileClient` usa **identidade de serviço**
(`GestServiceIdentityFeignConfig`). Na arquitetura anterior o agente Python falava com o
edge-service, e o edge-service buscava o perfil por ele. Com o browser como dispositivo, esse
intermediário deixou de existir no caminho — mas a razão de ele existir continua de pé.

**Consequência hoje:** a estação não sabe o que detectar, nem qual paciente observa. E
`POST /api/v1/reports` exige `pacienteId` no corpo, então a cadeia gesto → alarme não fecha.

## Spec de referencia

- `GEST ADR-008` §D1, §D3, §D4 (`docs/architecture/frontend/modules/gest/frontend/adr/`)
- `GEST SPEC-09` §2.2 (`.../frontend-spec/SPEC-09-estacao-paciente.md`)
- EDGE `api-contract` §3.1 (heartbeat — o padrão de identidade que este endpoint deve seguir)
- `EDGE ADR-005` (anti-spoofing: identidade vem do token, nunca do corpo)

## Escopo

- [x] `GET /api/v1/edge/profile` no edge-service, guardado por `@PreAuthorize("hasAuthority('edge.device')")`
- [x] **`deviceId` extraído do claim `sub` do JWT** — nunca de path, query ou corpo. Mesmo padrão do
      `HeartbeatController`, que rejeita corpo divergente com 403 `edge.identity-mismatch`. Aqui não
      há corpo: o endpoint simplesmente **não aceita** `deviceId` como parâmetro, o que fecha a
      questão por construção em vez de por validação
- [x] Verificar o pareamento antes de responder — dispositivo não pareado devolve o mesmo
      `device-not-paired` que o gest-service já produz (403), não um 200 vazio
- [x] Ampliar `GestDetectionProfileView`, que hoje é `record GestDetectionProfileView(long version)`
      — só a versão. Passa a espelhar o `DetectionProfileResponse` do gest-service no que a estação
      precisa: `thresholds` (as três faixas), `primitives[]` (com `tipoGestoId`, `nome`,
      `engineFeatureRef`, `sensibilidade`, `origem`), `pacienteId`, `version`, `etag`,
      `catalogoDegradado`. Manter `@JsonIgnoreProperties(ignoreUnknown = true)`
- [x] **Repassar o GET condicional de ponta a ponta**: `If-None-Match` do dispositivo vira
      `If-None-Match` na chamada ao gest-service; `304` do gest-service vira `304` para o
      dispositivo, sem corpo. Sem isso a estação refaz download completo do perfil a cada
      verificação, e o `Cache-Control: max-age=60` que o gest-service publica não serve para nada
- [x] Reusar o `DetectionProfileClient` existente (identidade de serviço já configurada). **Não**
      criar um segundo cliente
- [x] Teste de integração: token de dispositivo pareado recebe 200 com perfil; **token de usuário
      recebe 403**; dispositivo não pareado recebe 403; `If-None-Match` correspondente recebe 304

## Resultado esperado

A estação, com o token que já possui, obtém em uma chamada:
- **o que detectar** — `primitives[].engineFeatureRef`
- **com qual sensibilidade** — `thresholds` + `sensibilidade` por primitivo
- **de quem** — `pacienteId`, que é o que falta para montar o `POST /api/v1/reports`

## Criterios de aceite

- [x] Token `edge.device` pareado → 200 com o perfil completo
- [x] Token de **usuário** (cuidadora/coordenadora) → **403**. A estação é um dispositivo; este
      endpoint não é uma segunda porta para dado clínico
- [x] `deviceId` não é aceito como parâmetro em forma nenhuma — a identidade vem só do `sub`
- [x] Dispositivo não pareado → 403 `device-not-paired`
- [x] `If-None-Match` igual ao ETag corrente → 304 sem corpo
- [x] gest-service indisponível → resposta degradada explícita, jamais um perfil vazio que a estação
      possa confundir com "nenhum gesto configurado". Um perfil vazio faria a estação rodar sem
      detectar nada, parecendo saudável — o pior modo de falha deste produto

## O que NÃO fazer, e por quê

**Não alargue as autoridades do token de dispositivo** para incluir `gest.detection-profile.read`.
Seria uma linha em `DeviceTokenService` e resolveria o sintoma — ao custo de dar a um dispositivo
que vive numa máquina de quarto de paciente acesso direto de leitura ao gest-service. O privilégio
mínimo aqui é: o dispositivo fala com o edge-service, e só o edge-service fala com o gest-service.
É a razão de o `DetectionProfileClient` existir com identidade de serviço.

**Não pendure o perfil na resposta do heartbeat.** O heartbeat já carrega `profileDrift` e
`currentProfileVersion`, que é o sinal certo de "há perfil novo". Empurrar o perfil inteiro nele
misturaria sinal de vida com configuração, e obrigaria a trafegar o perfil a cada 15 s.

## Dependencias

- EDGE-014 (precede — `DetectionProfileClient` + identidade de serviço já existem)
- Bloqueia: `GEST-FE-028` (parcial, com `TODO[GEST-FE-028][blocked]` no código),
  `GEST-FE-030` (rota `/estacao` + laço + relato), `GEST-FE-031`, `GEST-FE-032`

## Estimativa

M

## Prioridade

P1 — bloqueia toda a onda W10 do GEST, que é a que faz a cadeia gesto → alarme existir em runtime.

## Nota de proveniência

A lacuna é do desenho da `ADR-008`, escrito por mim: verifiquei corpos de requisição, regras de
tenant e invariantes de privacidade nos `@RestController`, e **não cruzei a autoridade exigida por
cada endpoint com a que o token de dispositivo carrega**. É a mesma família do defeito nº 1
catalogado neste repositório — o consumidor escrito a partir da própria suposição —, desta vez na
camada de arquitetura. O backend já tinha registrado como C-19; faltou eu ler o Javadoc do cliente
antes de escrever a ADR.
