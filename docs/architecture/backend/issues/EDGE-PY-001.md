# EDGE-PY-001 -- [URGENTE] Python Edge Agent: arquitetar Layer 1 do agente local de captura/detecção de gestos

**Status:** PENDENTE -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Prioridade:** P0-URGENTE
**Decisão de referência:** D-534

## Contexto

O módulo GEST define o vocabulário de gestos (catálogo, estado, semântica) como serviço Java.
Porém, o componente que **captura frames da webcam, executa CV/classificação e publica eventos
`GestoDetectado`** é um processo local que roda no dispositivo do paciente — um Python Edge Agent
(PEA) independente do monorepo Java.

Sem o PEA arquitetado, os módulos GEST e EVNT ficam sem um produtor real de eventos de gesto.
O PEA é pré-requisito para o teste end-to-end do fluxo de detecção de gesticulação.

## Decisão arquitetural (D-534)

- PEA é um deployable **fora do monorepo Java**, repositório próprio: `neuroassist-edge-agent/`
- Linguagem: Python 3.11+, OpenCV, modelo leve ONNX ou TFLite
- Roda localmente no dispositivo do paciente (edge device — Windows/Linux/Raspberry Pi)
- Consome `GET /api/v1/gest/vocabulary/{tenantId}/active` para obter vocabulário vigente
- Autentica via JWT RS256 emitido pelo ACS (role `edge_agent`)
- Publica `GestoDetectado` ao EVNT via HTTP POST ou WebSocket

## Escopo da Layer 1 a executar (URGENTE)

- [ ] **Vision (PEA-01):** documento arquitetural `01-architectural-vision.md` para o PEA
  - Componentes: CaptureModule, ClassifierModule, VocabularyCache, EventPublisher, AuthClient
  - Requisitos não-funcionais: latência < 200ms ponta-a-ponta, operação offline parcial (cache de vocabulário)
  - Diagrama de contexto C4 (PEA ↔ GEST ↔ EVNT ↔ ACS)
- [ ] **Specs (PEA-02):** contratos de integração
  - API consumida de GEST: endpoint de vocabulário, formato, versionamento
  - Evento publicado ao EVNT: `GestoDetectado` (payload, headers, idempotency key)
  - Auth flow: client_credentials via ACS (role `edge_agent`, TTL 900s)
- [ ] **Backlog (PEA-03):** issues PEA-001..N decompostas por componente
- [ ] **Repositório (PEA-04):** criar `neuroassist-edge-agent/` com estrutura inicial, pyproject.toml, Docker image para edge device

## Impacto em módulos Java dependentes

- **GEST M5 Scaffold:** deve incluir endpoint `/vocabulary/{tenantId}/active` (pré-req do PEA)
- **EVNT M5 Scaffold:** deve aceitar eventos `GestoDetectado` publicados pelo PEA
- **ACS:** role `edge_agent` deve existir no mapa role→authorities (ACS-005); grant type `client_credentials` deve estar habilitado

## Critérios de aceite desta issue (arquitetamento)

- [ ] Documento de visão do PEA existe e cobre: componentes, fluxo de dados, requisitos de hardware mínimo
- [ ] Contratos de integração definidos (GEST vocab endpoint + EVNT evento + ACS auth flow)
- [ ] Issues PEA-001..N no backlog com P1..P3 atribuídas
- [ ] D-534 indexado no ES (decisions-index.py backfill)

## Próximos passos imediatos

1. Executar `/be-architect-vision neuroassist-ai PEA` (adaptar para Python edge agent)
2. Confirmar com o time a plataforma alvo do edge device (Windows/Linux/RPi)
3. Definir modelo de classificação base (MediaPipe Hands vs. modelo custom ONNX)

## Referências cruzadas

- D-534 (decisão arquitetural)
- GEST-001..N (serviço Java de vocabulário)
- EVNT-001..N (barramento de eventos)
- ACS-005 (TokenIssuer — role `edge_agent`)
- ACS-006 (AutenticacaoService — client_credentials grant)
