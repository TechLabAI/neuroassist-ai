# SPEC-10 — Integrations

## Objetivo
Especificar as integrações de saída do CONV via portas hexagonais, com posturas de falha explícitas.
Contratos completos: [`../../contracts/integration-contracts.md`](../../contracts/integration-contracts.md)
(externas) e [`../../contracts/hub-integration.md`](../../contracts/hub-integration.md) (plataforma).

## Escopo
**In:** `SearchPort` (ES), `AcsSessionPort` (ACS), `VocabularyPort` (PAC/GEST), `AudioRefPort` (áudio),
`HistAuditPort` (HIST). Resolve **G4** (opaco), **G6** (layout de áudio), **G7** (escopo s2s).
**Out:** implementação do índice (design), TREI (sibling; sem call-path).

## Decisões técnicas
| Integração | Porta | Postura | Falha |
|---|---|---|---|
| Elasticsearch | `SearchPort` (lib) | soft | fail-open → estático (OPS-003) |
| ACS | `AcsSessionPort` | **hard** | fail-closed (`401`/`403`) |
| PAC/GEST | `VocabularyPort` | soft (só gesto) | `bound:false` |
| Áudio (filesystem) | `AudioRefPort` | soft | fail-open → Web Speech (OPS-004) |
| HIST | `HistAuditPort` | best-effort | outbox + retry (SPEC-09) |

- **G4:** `engineFeatureRef` modelado como **string opaca**; CONV não parseia sua estrutura. Formato real
  (commit `89f4ee5`) não confirmado como consumível fora de PAC/GEST → se não houver endpoint, o binding
  degrada para `bound:false` (documentado no risks-gaps).
- **G6:** áudio em `${CONV_AUDIO_ROOT}/<empresa>/<paciente_id>/<locale>/<no_id>.<ext>`; presença do
  arquivo é o sinal de fallback.
- **G7:** s2s via `client_credentials`, escopo `pac.vocabulary.read`.
- **Reuso obrigatório:** ES só via `elastic-stack-client-lib`; nunca um cliente ES paralelo (`ADR-002`).

## Implementação
- Adapters em `infrastructure`, um por porta; o adapter de retrieval delega ao `SearchPort` da lib.
- `VocabularyAdapter` trata resposta desconhecida/erro como `bound:false` (não propaga erro).
- `AudioRefAdapter` resolve caminho e confere presença; ausência → ref nula.

## Casos de borda
- ES `ElasticException` → estático + `degraded`.
- PAC/GEST indisponível → `bound:false`, navegação/toque seguem.
- Áudio ausente/inacessível → Web Speech.
- HIST indisponível → outbox.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL access Elasticsearch only through `elastic-stack-client-lib`'s `SearchPort` and SHALL NOT instantiate a parallel ES client.
- THE SYSTEM SHALL treat `engineFeatureRef` as an opaque identifier and SHALL return `bound:false` when it cannot be resolved.
- WHEN resolving the cached audio reference, THE SYSTEM SHALL derive the path as `<empresa>/<paciente_id>/<locale>/<no_id>.<ext>` and SHALL treat file absence as the Web-Speech fallback signal.
- THE SYSTEM SHALL authenticate service-to-service vocabulary calls with `client_credentials` (scope `pac.vocabulary.read`).
