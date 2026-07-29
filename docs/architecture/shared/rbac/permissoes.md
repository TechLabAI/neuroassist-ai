# Catálogo de Permissões — NeuroAssist AI

> Lista canônica de permissões namespace `<modulo>.<recurso>.<acao>`.
> Aplicada em V1 do `neuroassist-ai-migrations` (tabela `neuroassist_global.permissao`).
> Bundle por papel: [`papeis.md`](papeis.md).
>
> **Fonte de verdade:** `templates/architecture/decisions/permission-catalogue.arch.json` (D-060).
> Este markdown é gerado deterministicamente. Não editar à mão — o próximo render sobrescreve.

## Convenções

- **Namespace de 3 partes (regra geral):** `<modulo>.<recurso>.<acao>` (snake/kebab-case, sempre lowercase).
- **4 partes** quando o recurso tem sub-recurso natural (ex: `cad.vendedor.comissao.config`).
- **Módulos** (prefixos canônicos): `gest, evnt, pac, acs, hist, dash, edge`.
- **Ações canônicas**: `read`, `criar`, `editar`, `excluir`, `cancelar`, `aprovar`, `emitir`, etc. (verbos do domínio em pt-BR — ver `arch_decisions._OP_VERB_TO_ACTION` para o mapeamento `op-name → ação`).
- **Wildcards proibidos em produção** — uma linha por permissão (auditoria precisa rastrear).

## GEST — Detecção de Gesto (Edge)

Captura de webcam, extração de landmarks faciais e classificação multi-classe do comando ativo (gesto simples ou composto por múltiplos primitivos), executado localmente no dispositivo do paciente.

```
```

## EVNT — Eventos e Alarmes

Geração do evento estruturado correspondente ao comando reconhecido, orquestração de notificação por nível de urgência (alarme full-screen para emergência; discreta para rotina) e roteamento para o responsável.

```
```

## PAC — Pacientes e Catálogo de Comandos

Cadastro de paciente e catálogo de comandos (gesto -> ação, rótulo livre definido pela família, sensibilidade, tempo mínimo/máximo de gesto e nível de urgência), calibráveis independentemente por comando.

```
```

## ACS — Cuidadores e Acesso

Gestão de cuidadores/usuários, papéis e vínculo com paciente(s); base do multi-tenant (tenant = família ou clínica).

```
```

## HIST — Histórico e Auditoria

Registro imutável de eventos, alarmes e ações de confirmação/edição, com trilha de auditoria.

```
```

## DASH — Dashboard e Notificações

Painel web (Angular) para cuidador consultar histórico, calibrar sensibilidade e receber notificações.

```
```

## EDGE — Integração de Dispositivo Edge

Registro, pareamento e monitoramento de heartbeat do agente local (edge) que roda a detecção de gesto.

```
```

## Total

**0 permissões canônicas** distribuídas em 7 módulos. O número cresce a cada novo recurso — atualizar o JSON (não este markdown).

## Validação automática

- O gerador (`scripts/be-module-generator.py`) valida ao scaffold que toda permissão referenciada em `@PreAuthorize` consta neste catálogo (D-060). Ops cujo verbo não mapeia para nenhuma ação canônica em `arch_decisions._OP_VERB_TO_ACTION` falham com erro explícito.
- `scripts/arch_decisions.py validate --aspect permission-catalogue` checa duplicatas, prefixos consistentes e `_schema_version`.
- `cross_validate_rbac_against_catalogue(rbac, catalogue)` checa que toda permissão listada em `roles[*].permission_bundle` (kind=`list`) consta no catálogo.

## Como adicionar permissão nova

1. Editar `templates/architecture/decisions/permission-catalogue.arch.json` — adicionar entrada em `modules[prefix=<modulo>].permissions`.
2. Bumpar `version` (semver) e `last_changed`.
3. Validar: `python scripts/arch_decisions.py validate`.
4. Re-renderizar este documento (`new-project` P2 ou comando equivalente).
5. Se o BE precisa permitir uso da permissão em algum papel, editar `rbac.arch.json` `roles[*].permission_bundle`.
6. Migração: adicionar `INSERT INTO neuroassist_global.permissao` em `V<N>__iam_permissao_<codigo>.sql`.
7. Bumpar `neuroassist_global.modelo_acesso_versao` para invalidar cache.

## Anti-patterns

- ❌ Editar este markdown direto — ele é gerado. Próximo render sobrescreve.
- ❌ Inventar permissão ad-hoc em código sem registrar no JSON — quebra auditoria + cache + validação do gerador.
- ❌ Permissão genérica `*` ou `<modulo>.*` no `papel_permissao` — sempre individual em runtime; wildcards só são aceitos na expansão de `permission_bundle` em tempo de geração.
- ❌ Esquecer de bumpar `neuroassist_global.modelo_acesso_versao` após adicionar permissão — JWT antigo continua válido com modelo desatualizado.
- ❌ Renomear permissão depois de aplicada em produção — sempre criar nova + descontinuar antiga (depreciate em N versões).
