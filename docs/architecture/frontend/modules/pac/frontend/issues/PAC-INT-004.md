## [PAC-INT-004] HTTP interceptors — X-Empresa-Id, Authorization, X-Correlation-Id (wiring para PAC)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/shell-entry/src/lib/pac.providers.ts` registra os providers necessários para que os interceptors HTTP compartilhados (`X-Empresa-Id`, `Authorization: Bearer`, `X-Correlation-Id`) sejam aplicados a todas as chamadas dos clients PAC, garantindo que o `X-Empresa-Id` correto está presente em cada requisição (multi-tenant schema-per-empresa).

**Context / Problem statement:**
- backend-api-cross-link.md §1: todos os requests PAC carregam `X-Empresa-Id`, `Authorization: Bearer <JWT>` e `X-Correlation-Id` — gerenciados pelos shared interceptors.
- O `X-Empresa-Id` é crítico para o schema-per-empresa isolation (se ausente → BE retorna `400 /errors/missing-tenant`).
- Os interceptors provavelmente já existem em `@neuroassist/shared/access-http-base`; esta issue é sobre garantir que estão registrados no escopo do router lazy do módulo PAC.
- `pac.providers.ts` também registra o `EdgeDeviceAckPort` stub (INT-FE-PAC-001) e qualquer outro provider PAC-specific.

**Approach (execution path):**
1. Criar `libs/pac/shell-entry/src/lib/pac.providers.ts` exportando `PAC_PROVIDERS: Provider[]`.
2. Incluir: `provideHttpClient(withInterceptors([empresaIdInterceptor, authInterceptor, correlationIdInterceptor]))` (ou registrar via `HTTP_INTERCEPTORS` multi-provider se a versão do Angular não suportar functional interceptors).
3. Registrar `{ provide: EdgeDeviceAckPort, useClass: EdgeDeviceAckStubAdapter }` (INT-FE-PAC-001).
4. Usar `PAC_PROVIDERS` em `PacModule` providers array (PAC-FE-003).
5. Unit test: criar um `TestBed` com `PAC_PROVIDERS`, fazer um HTTP request e verificar que os 3 headers estão presentes.

**Non-goals:**
- Sem implementação dos interceptors (pertencem a `@neuroassist/shared/access-http-base`).
- Sem `If-Match` aqui — é responsabilidade de cada client (PAC-FE-007/008) passá-lo explicitamente.

**Layer(s):**
frontend / libs/pac/shell-entry

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/contracts/backend-api-cross-link.md` (§1)
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-04-data-access.md` (§2 nota headers)

**Architecture alignment:**
- Tag Nx: `scope:pac`, `type:domain`

**Implementation checklist (by file):**

**Lib: libs/pac/shell-entry**
- [ ] `libs/pac/shell-entry/src/lib/pac.providers.ts`
- [ ] `libs/pac/shell-entry/src/lib/pac.providers.spec.ts`

**Tests**
- [ ] `npx nx test pac-shell-entry` PASS
- [ ] `npx nx lint pac-shell-entry` PASS

**Milestone:**
Wave 4 / P4 Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:pac
priority:P4

**Acceptance criteria:**
- `npx nx test pac-shell-entry` PASS: `HttpClientTestingModule` recebe request com `X-Empresa-Id`, `Authorization`, `X-Correlation-Id` após providers registrados.
- `400 missing-tenant` não ocorre em ambiente de teste com empresa ativa configurada.

**Dependencies:**
- `@neuroassist/shared/access-http-base` (interceptors)
- `@neuroassist/shared/empresa-context` (EmpresaContextService)
- INT-FE-PAC-001 (EdgeDeviceAckStubAdapter — registrado aqui)

**Data impact:**
Nenhum.

**API / Contract impact:**
- Garante que todos os requests PAC carregam os headers mandatórios.

**Testing / Evidence required:**
- `HttpTestingController`: request interceptado contém os 3 headers.

**Risks / Notes:**
- Se os interceptors forem registrados globalmente no `app.config.ts` do shell, não precisam ser re-registrados aqui; verificar se o lazy module herda os providers do root. Se sim, `pac.providers.ts` pode omitir os interceptors e apenas registrar o `EdgeDeviceAckPort`.
