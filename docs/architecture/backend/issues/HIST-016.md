# HIST-016 -- LGPD payload guard: rejeição de chaves biométricas proibidas

## Contexto

LGPD hard rule: `payload` de um audit fact nunca deve conter dado biométrico/imagem.
Chaves proibidas: `landmarks`, `image`, `frame`, `video`, `embedding`. A rejeição ocorre
na camada de aplicação (SPEC-02, SPEC-06) antes de qualquer persistência, com 422
`/errors/forbidden-payload`. É uma validação de domínio autônoma, testável sem Spring.

## Spec de referencia

- SPEC-02 (seção "LGPD": rejeitar payload com chaves biométricas proibidas)
- SPEC-06 (§ postura LGPD)
- `contracts/api-contract.md §2.1 EARS`

## Escopo

**`com.neuroassist.hist.domain.guard.LgpdPayloadGuard`:**
- Conjunto de chaves proibidas: `{"landmarks", "image", "frame", "video", "embedding"}` (configurável via `@ConfigurationProperties`).
- `fun assertClean(payload: Map<String, Any>)`: percorre recursivamente as chaves do mapa; se qualquer chave (em qualquer nível) está no conjunto proibido → lança `ForbiddenPayloadKeyException(key)`.
- Integração em `RecordAuditFactService.append` (HIST-006): chamado após validação básica, antes de idempotência.
- Testes unitários cobrindo: payload limpo (OK), chave proibida no nível raiz (422), chave proibida em nested object (422), chave com casing diferente (`Image` → decidir se case-insensitive; documentar decisão).

## Resultado esperado

`LgpdPayloadGuard` é um `@Component` leve sem estado. Testes unitários sem Spring passam em <100ms.

## Criterios de aceite

- [ ] `payload = {"eventoId": "...", "verdict": "confirmed"}` → `assertClean` passa sem exceção.
- [ ] `payload = {"image": "base64..."}` → lança `ForbiddenPayloadKeyException`.
- [ ] `payload = {"data": {"embedding": [0.1, ...]}}` (nested) → lança `ForbiddenPayloadKeyException`.
- [ ] Conjunto de chaves proibidas é configurável (não hardcoded na lógica, mas em propriedade).
- [ ] Decisão de case-sensitivity documentada em KDoc (recomendação: case-insensitive).
- [ ] Controller retorna `422 /errors/forbidden-payload` quando guard lança.

## Dependencias

- HIST-002 (ForbiddenPayloadKeyException)
- HIST-006 (integração do guard no append flow)

## Estimativa

S

## Prioridade

P5
