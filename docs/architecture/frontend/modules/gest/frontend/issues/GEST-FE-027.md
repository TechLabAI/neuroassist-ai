# GEST-FE-027 -- Sessão de dispositivo — DeviceSessionStore + POST /oauth2/token/device

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens
**GitHub:** [#TBD]

## Outcome
A estação autentica-se como DISPOSITIVO. `DeviceSessionStore` obtém e mantém um token via
`POST /oauth2/token/device`, expõe `deviceId` (claim `sub`), `tenantId` (`tenant_id`) e o estado
da sessão como signals. O interceptor de Authorization passa a anexar o token de dispositivo nas
requisições da estação — nunca o token de usuário.

## Context / Problem statement
- `ADR-008` D1: o paciente não digita senha; um papel de usuário é inutilizável por ele. A estação
  é um dispositivo, e o backend já implementa essa identidade.
- Contrato lido no produtor (`AutenticacaoController` + `DeviceTokenRequest`), não presumido:
  `{ clientId, clientSecret }`, ambos `@NotBlank`; resposta **sem** `refreshToken`.
- Sem `refreshToken`, a renovação é reautenticar com a credencial — não há rotação.

## Approach (execution path)
1. `libs/gest/src/lib/estacao/device-session.store.ts` — signals `token`, `deviceId`, `tenantId`, `estado`.
2. `autenticar(clientId, clientSecret)` → `POST /oauth2/token/device`; decodificar `sub` e `tenant_id`.
3. Reautenticar proativamente antes de `exp` (o token não tem refresh).
4. Ensinar `AUTH_BEARER_URL_PREFIXES` / o interceptor a usar o token de dispositivo no escopo da estação.
5. 401 → estado `credencial-invalida`, nunca "tentando para sempre" em silêncio.

## Non-goals
- Enroll/pareamento (EDGE, já existe).
- **Armazenamento seguro do `clientSecret`** — pendência P1 da ADR-008. Nesta issue a credencial é
  fornecida por configuração de ambiente da máquina dedicada, e isso fica **escrito na tela** de
  configuração como limitação conhecida. Não inventar um esquema aqui.

## Layer(s)
frontend / data-access

## Bounded context
gest

## Service execution spec(s)
- `frontend-spec/SPEC-09-estacao-paciente.md` §2.1, §1
- `docs/architecture/backend/standards/service-identity.md`

## Architecture alignment
- `providedIn: 'root'` store com signals; zero componente
- Tag Nx: `scope:gest`, `type:data-access`

## Implementation checklist (by layer/file)

**Data access**
- [ ] `libs/gest/src/lib/estacao/device-session.store.ts`
- [ ] `libs/gest/src/lib/estacao/device-session.store.spec.ts`
- [ ] `libs/gest/src/lib/estacao/device-auth.port.ts` + adapter HTTP
- [ ] integração com o interceptor de Authorization
- [ ] `libs/gest/src/index.ts` — export

## Acceptance
- Token obtido carrega `edge.device`; `deviceId` sai do `sub`, nunca de configuração.
- 401 leva a estado terminal declarado, não a laço de retry silencioso.
- Nenhum `clientSecret` aparece em log, em erro ou no DOM.
