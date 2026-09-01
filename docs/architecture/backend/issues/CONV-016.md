# CONV-016 -- Implementar guards de segurança: guard anti-biométrico (400 no-biometric-ingress) + contrato de locale (pending_locale 409, unsupported-locale 400)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

Dois guards de fronteira complementares ao OAuth2 (CONV-015):
1. **Guard anti-biométrico (SEC-002/LGPD):** rejeitar qualquer request cujo body/query contenha
   campos de imagem, frame, landmark, ou dado biométrico com `400 no-biometric-ingress`. CONV nunca
   ingere dados biométricos — este guard é a barreira de última instância no controller.
2. **Contrato de locale:** `unsupported-locale` (400) quando `Accept-Language` ou `locale` query
   param não estão em `localesSuportados` da árvore do paciente; `content-pending-locale` (409)
   quando o nó existe mas o locale ativo não tem conteúdo `ativo` (já coberto em CONV-005/006, mas
   este guard centraliza o ProblemDetail RFC 7807 correto para ambos os casos).

## Spec de referencia

- SPEC-06 (security — anti-biometric guard, SEC-002)
- SPEC-11 (locale-bilingual-content — contrato de locale e pending_locale)
- `contracts/api-contract.md` §1 (headers), §3 (error model)

## Escopo

- [ ] Criar `infrastructure/security/BiometricIngressGuard.java` (`@Component` interceptando `HandlerInterceptorAdapter` ou `@RequestBodyAdvice`): inspeciona nomes de campos no body JSON (ex.: `image`, `frame`, `landmarks`, `biometric`); se detectado → `ProblemDetail{type:no-biometric-ingress, status:400}`
- [ ] Criar `infrastructure/web/LocaleValidator.java`: extrai locale efetivo (`Accept-Language` se presente, else JWT claim `locale`); valida contra `localesSuportados` da árvore do paciente; se inválido → `ProblemDetail{type:unsupported-locale, status:400}`
- [ ] Criar `infrastructure/web/ConvExceptionHandler.java` (`@RestControllerAdvice`): mapeamento de todas as exceptions de domínio para `ProblemDetail` RFC 7807 (tabela §3 do api-contract): `NodeNotFoundException → 404`, `NodeNotSelectableException → 409 node-not-selectable`, `ContentPendingLocaleException → 409 content-pending-locale`, `SessionRevokedException → 401 session-revoked`, `UnauthorizedException → 401 unauthenticated`, etc.
- [ ] `ProblemDetail` sempre inclui `correlationId` (de `MDC.get("correlationId")`)
- [ ] Testes unitários: body com campo `image` → `400 no-biometric-ingress`; locale não suportado → `400 unsupported-locale`; seleção de nó pendente → `409 content-pending-locale`

## Resultado esperado

Todos os erros do CONV retornam RFC 7807 `ProblemDetail` com `correlationId`; guard anti-biométrico bloqueia na fronteira; locale inválido rejeitado antes de chegar ao service.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS
- [ ] Teste unitário: POST /search com body `{"image": "..."}` → `400 no-biometric-ingress` (ProblemDetail)
- [ ] Teste unitário: GET /tree com `Accept-Language: de` (não em localesSuportados) → `400 unsupported-locale`
- [ ] Teste unitário: POST /nodes/{id}/select para nó com `content-pending-locale` → `409 content-pending-locale` ProblemDetail
- [ ] Todos os ProblemDetail têm campo `correlationId` presente

## Dependencias

- CONV-015 (SecurityConfig — guard registrado no filtro chain)

## Estimativa

M

## Prioridade

P5
