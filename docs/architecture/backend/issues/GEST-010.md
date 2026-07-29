# GEST-010 -- Implement DetectionContractService.getContractDescriptor() and validateReport() — stateless contract validation (no persistence, no biometric data)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto
`GET /api/v1/gest/detection-contract` expoe o descriptor JSON da versao atual do contrato de deteccao (v1.0.0) — edge e EVNT podem verificar com qual schema trabalhar. `POST /api/v1/gest/recognized-commands` valida um `RecognizedCommandReport` do edge — verifica se `profileVersion` foi publicado para o tenant e se todos os `tipoGestoId` do report pertencem ao perfil referenciado. Nenhuma persistencia; nenhum dado biometrico armazenado (invariante LGPD).

## Spec de referencia
- SPEC-01, SPEC-06, api-contract.md §5.1, §5.2, event-contracts.md §3.1, §3.2

## Escopo
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/application/service/DetectionContractService.java` (metodos: `getContractDescriptor()`, `validateReport(RecognizedCommandReportDto)`)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/dto/RecognizedCommandReportDto.java` (campos: contractVersion, reportId, deviceId, tenantRef, profileVersion, commandRef, recognizedAt, primitives, timingWindow, composite)
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/api/dto/ContractDescriptorDto.java` (contractVersion, recognizedCommandReportSchema ref, edgeRecognitionSignalSchema ref)
- [ ] adicionar `findByVersion(long version, String tenantId)` em `DetectionProfileRepositoryPort` (retorna Optional com snapshot de primitivos)
- [ ] validar que nenhum campo biometrico (video, image, landmark, raw frame) aparece no DTO — constraint via Jackson `@JsonIgnoreProperties` ou anotacao customizada
- [ ] testes: report valido=200, profileVersion desconhecido=422, primitivo fora do perfil=422

## Resultado esperado
`GET /detection-contract` retorna descriptor com contractVersion 1.0.0; `POST /recognized-commands` valida report sem persistir nada.

## Criterios de aceite
- [ ] `GET /detection-contract` retorna `{ "contractVersion": "1.0.0", ... }` sem autenticacao especial (scope gest.read)
- [ ] `POST /recognized-commands` com `profileVersion` nao publicado retorna `422 /errors/unknown-profile-version`
- [ ] `POST /recognized-commands` com `tipoGestoId` nao pertencente ao perfil referenciado retorna `422 /errors/primitive-not-in-profile`
- [ ] `POST /recognized-commands` valido retorna `{ "valid": true, "contractVersion": "1.0.0" }` sem criar nenhum registro em BD (contagem constante antes/depois)
- [ ] `X-Detection-Contract-Version` presente na resposta de todos os endpoints de deteccao

## Dependencias
GEST-005, GEST-009 (GEST-009 gera perfis para testar validateReport).

## Estimativa
M

## Prioridade
P2
