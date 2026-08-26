# CI — NeuroAssist AI

> ## ⛔ OS DOIS WORKFLOWS ESTÃO DESATIVADOS (25/08/2026)
>
> ```
> $ gh workflow list --all
> CI — Backend     disabled_manually
> CI — Frontend    disabled_manually
> ```
>
> Decisão do dono do produto ("desativar e pular por agora"). **Nada do que este documento
> descreve está executando.** Os workflows existem, com clone limpo, `integrationTest` e o
> `gitignore-guard` — e nenhum deles roda.
>
> Enquanto isso valer, "os módulos estão verdes" volta a ser uma afirmação sobre a última vez
> que alguém rodou localmente — exatamente o que a seção abaixo explica que o CI existe para
> evitar. Rastreado em `E2E-027`, que foi REABERTA por causa disto.
>
> Para religar:
> ```bash
> gh workflow enable "CI — Backend"
> gh workflow enable "CI — Frontend"
> ```

Implementado em E2E-027. Dois workflows em `.github/workflows/`:

| Arquivo | Gatilho | O que verifica |
|---|---|---|
| `ci-backend.yml` | push/PR para `dev`, `release`, `prod` com alterações em `backend/` | Build + ArchUnit + integrationTest + .gitignore guard |
| `ci-frontend.yml` | push/PR para `dev`, `release`, `prod` com alterações em `frontend/` | `nx affected:test` + `nx affected:lint` |

---

## Por que o CI importa neste projeto

Antes do CI, "os 7 módulos estão verdes" era uma afirmação sobre a última vez que
alguém rodou localmente — não sobre o estado do repositório.

Dois defeitos concretos documentados:

1. **61 de 62 `application/port/out/*.java` ficaram fora do git** (E2E-027, defect
   detalhado em `plan-e2e-integration.md` §11). `out/` sem barra inicial no `.gitignore`
   casava com `application/port/out/` em qualquer profundidade. O Gradle compila o
   **diretório de trabalho**, não o índice do git — nenhum gate local detectou.
   Um clone limpo no CI revelaria na primeira compilação porque o diretório não
   existiria. O `.gitignore` já foi corrigido para `/out/` (barra inicial); o
   `.gitignore guard` previne regressão.

2. **`:test` não roda `integrationTest`** — um `*IT` quebrado convive com gate verde.
   O workflow adiciona a etapa `integration-test` para gest-service, que é o único
   serviço com um `integrationTest` real (Testcontainers/PostgreSQL), não um delegado.

---

## ci-backend.yml — detalhes

### Job `build-and-check`

Roda `./gradlew build --parallel` a partir de um clone limpo. Isso:

- Compila apenas o que está no índice do git (arquivos não commitados não existem).
- Executa os testes unitários de todos os serviços (o `test` task é parte do `build`
  lifecycle do Gradle).

Em seguida roda `architectureCheck` nos 7 serviços que registram essa task:

```
:services:acs-service:architectureCheck
:services:dash-service:architectureCheck
:services:edge-service:architectureCheck
:services:evnt-service:architectureCheck
:services:gest-service:architectureCheck
:services:hist-service:architectureCheck
:services:pac-service:architectureCheck
```

**Serviços SEM `architectureCheck` registrado** (verificado no código):
- `neuroassist-ai-api-gateway` — sem registro explícito da task
- `neuroassist-ai-migrations` — sem registro explícito (ArchUnit via `:test`)
- `neuroassist-ai-service-registry` — sem registro explícito da task

### Job `integration-test`

Roda apenas para `gest-service`, único serviço com `integrationTest` real (Testcontainers +
PostgreSQL). `ubuntu-latest` tem Docker pré-instalado.

Tasks executadas:
- `:services:gest-service:integrationTest` — testes marcados com `@Tag("integration")`
- `:services:gest-service:bddTest` — suíte Cucumber via `CucumberIT.class`

**Serviços com `integrationTest` delegado** (apenas chamam `:test`, já cobertos no build):
- `acs-service`, `edge-service`

**Serviços sem `integrationTest`:**
- `dash-service`, `evnt-service`, `hist-service`, `pac-service`,
  `neuroassist-ai-api-gateway`, `neuroassist-ai-migrations`,
  `neuroassist-ai-service-registry`

### Job `gitignore-guard`

Protege contra regressão do defeito do `out/` sem barra.

**Passo a — canary:** Cria um arquivo temporário em
`backend/services/gest-service/src/main/java/com/neuroassist/gest/application/port/out/CiGuard.java`
e chama `git check-ignore`. Falha se git ignoraria o arquivo.

**Passo b — arquivos existentes:** Para cada arquivo em `application/port/out/*.java` que
está no índice do git, verifica que `git check-ignore` não o ignora.

---

## ci-frontend.yml — detalhes

Usa `nx affected` para rodar apenas os projetos afetados pelo diff:

```bash
npx nx affected:test --base=<sha-base> --head=HEAD --ci --passWithNoProjects
npx nx affected:lint --base=<sha-base> --head=HEAD --passWithNoProjects
```

- Em PRs, o `base` é o SHA do branch de destino.
- Em push direto, é `HEAD~1`.

---

## Cache Gradle

A cache de dependências Gradle é compartilhada entre os jobs `build-and-check` e
`integration-test` pela key:

```
gradle-<OS>-<hash dos *.gradle.kts + gradle-wrapper.properties>
```

---

## O que não está configurado (requer permissão administrativa no GitHub)

### Branch protection em `dev`, `release`, `prod`

O checklist de E2E-027 inclui branch protection, mas essa configuração exige
permissão administrativa no repositório e é decisão do dono do repo.

Configuração recomendada para o branch `dev` (e análoga para `release`/`prod`):

1. Settings → Branches → Add rule → Branch name pattern: `dev`
2. Marcar: **Require a pull request before merging**
3. Marcar: **Require status checks to pass before merging**
4. Em "Status checks required": adicionar
   - `Build + architectureCheck`
   - `.gitignore guard — application/port/out/`
   - `nx affected:test + affected:lint`
5. Marcar: **Require branches to be up to date before merging**
6. Marcar: **Do not allow bypassing the above settings** (opcional mas recomendado)

Sem branch protection, o CI é informativo mas não obrigatório.

---

## O que não foi possível verificar localmente

- **Se os workflows passam no GitHub Actions** — não é possível confirmar sem executar
  no runner real. Os YAMLs têm sintaxe válida (verificado com `python yaml.safe_load`),
  mas podem ter erros de runtime (path do Gradle wrapper, permissão do gradlew,
  disponibilidade do Docker para Testcontainers, etc.).
- **Se `nx affected` detecta corretamente os projetos afetados** em todos os cenários de
  diff — depende da configuração do `nx.json` e das tags dos projetos, que não foram
  alteradas aqui.
- **Se `bddTest` passa em CI** — os cenários BDD do gest-service requerem PostgreSQL via
  Testcontainers. Se algum cenário depende de dados externos ou de serviços não mockados,
  pode falhar em CI. Verificar após a primeira execução.
