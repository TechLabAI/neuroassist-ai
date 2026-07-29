<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-005 — Edge/Cloud Detection Split (gest-service boundary)

## Status
aceita

## Contexto
GEST é "Detecção de Gesto (Edge)". A detecção real — captura de webcam, landmarks faciais
(MediaPipe ou similar), classificação multi-classe — precisa rodar localmente no dispositivo do
paciente por três razões não-negociáveis (Framing Técnico §2): latência detecção→alarme < 200ms,
alarme funciona offline, e nenhum frame de vídeo/imagem pode sair do dispositivo (privacidade por
padrão). Esse processamento é CV/Python, não Java/Spring. Ao mesmo tempo, o produto pivotou para um
dashboard SaaS multi-tenant que precisa de um lado servidor que governe *o que é detectável* e
entregue configuração ao edge. A decisão local de "single-service vs decomposição" para este módulo
se resume, na prática, a onde traçar a fronteira entre nuvem e edge.

## Decisão
`gest-service` é um **microsserviço backend independente** (alinhado a ADR-002 shared —
microsserviços independentes), responsável apenas pela **autoridade de detecção no lado nuvem**:
catálogo TipoGesto, ciclo de vida de detectabilidade, construção/publicação do perfil de detecção
versionado e o contrato de detecção. O `GestureDetectionEngine` do edge é um **deployable
separado**, fora do boundary do serviço Java — um cliente REST de gest-service, roteado via GWY.
gest-service nunca recebe pixels; expõe (a) endpoint de pull de perfil e (b) o contrato (OpenAPI)
para reporte de comando reconhecido. Nenhuma decomposição adicional de gest-service em si é feita
no MVP — o corte relevante aqui é nuvem↔edge (processo Java vs. processo de visão computacional),
não backend↔backend (esse já é resolvido por ADR-002: cada módulo já é seu próprio serviço).

## Consequências
**Positivas:** privacidade e latência preservadas (pixels nunca saem do edge); os dois lados evoluem
e implantam de forma independente; o perfil pull-based desacopla gest-service da disponibilidade do
edge; custo operacional baixo no MVP (um serviço Java simples, sem broker).
**Negativas / trade-offs:** exige um contrato REST/OpenAPI estável e versionado entre edge e nuvem;
introduz um segundo runtime (CV/edge) fora da stack Java canônica, cuja implementação/deploy vive
fora deste serviço; risco de deriva de versão de perfil entre edge e backend (mitigado por perfil
imutável versionado + pull no heartbeat via EDGE).
