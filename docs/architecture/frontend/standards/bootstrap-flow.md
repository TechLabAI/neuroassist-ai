# Bootstrap Flow (NeuroAssist AI Web)

> Sequência de inicialização do `apps/neuroassist-ai-web` + troca de empresa.

Goal: prover sequência previsível de inicialização do `neuroassist-ai-web` (Angular standalone).

---

## Estágios de bootstrap (em ordem)

1. **Start app route** -- Angular bootstrap, `provideRouter`, `provideHttpClient`.
2. **Restaurar sessão** (`AuthSessionService.restore()`)
   - lê JWT de cookie httpOnly (preferido) ou storage seguro
   - valida `exp` claim; se expirado, redirect para `iam-service` login
   - se válido: decodifica claims (`sub`, `roles`, `empresas`)
3. **Resolver empresa ativo** (`EmpresaContextService.resolve()`)
   - se JWT carrega `empresa_id` (selecionada antes): usa
   - se não, mas usuário tem 1 empresa: usa essa automaticamente
   - se tem N > 1: redirect para tela de seleção de empresa
4. **Avaliar decisões de acesso** (`AccessDecisionService.evaluate()`)
   - calcula RBAC + feature flags + capabilities
5. **Construir navegação** (`NavigationService.buildTree()`)
   - deriva menu/breadcrumb a partir das decisões e dos `<modulo>.permission-map.ts` registrados
6. **Renderizar Shell** (slots topbar/sidebar/content) e liberar rotas dos módulos
7. **Registrar telemetria** (`@neuroassist/shared/observability`)
   - bootstrap success/failure events
   - timing de cada estágio (para debug de cold start)

---

## Comportamento de troca de empresa

Quando o usuário troca a empresa ativo via seletor (`EmpresaContextService.switchTo(empresaId)`):

- **Cancelar in-flight requests** escopados ao empresa anterior (interceptor checa header e descarta)
- **Invalidar caches escopados** explicitamente (`QueryClient.invalidateQueries(['empresa', oldId])` ou equivalente)
- **Recomputar decisões de acesso** (papéis podem variar por empresa)
- **Reconstruir navegação** se o empresa mudou os módulos habilitados
- **Atualizar UI sem refresh completo** (signals reagem)

---

## Estados globais de UX

- **Loading:** enquanto bootstrap não completou
- **Error:** falha no bootstrap (route para `ErrorPageComponent`)
- **No access:** decisão nega rota ou empresa ativo não habilita o módulo
- **No empresa:** usuário válido mas sem empresa atribuído (extremo -- mostrar mensagem clara para chamar admin)

---

## Evidências / observabilidade (mínimo)

- Registrar bootstrap timing (start/end por estágio).
- Registrar reason codes para falhas (`auth_expired`, `empresa_resolve_failed`, `permission_denied`, ...).
- Registrar eventos de troca de contexto (`empresa_switched` old/new).
- Logs via `@neuroassist/shared/observability` (não `console.log`).

---

## Anti-patterns (evitar)

- Bootstrap síncrono que bloqueia a UI por mais de 1s sem feedback visual.
- Decisão de RBAC dentro de componentes durante bootstrap (delegar a `AccessDecisionService`).
- Troca de empresa que não invalida caches (dados de empresa A vazam para empresa B).
- Recarregar a página inteira (`location.reload()`) ao trocar empresa -- usar signals.
