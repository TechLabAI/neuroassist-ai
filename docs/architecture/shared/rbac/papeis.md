# Papéis canônicos — sementes SQL

> Aplicado em V1 do `neuroassist-ai-migrations` (schema `neuroassist_global`).
> Detalhes de cada papel: [`modelo.md`](modelo.md) §1.
>
> **Fonte de verdade:** `templates/architecture/decisions/rbac.arch.json` (D-058).
> Este markdown é gerado deterministicamente a partir daquele JSON.

## 1 papéis padrão (todas as empresas)

```sql
INSERT INTO neuroassist_global.papel (papel_id, codigo, nome, descricao, ativo) VALUES
  (gen_random_uuid(), 'CUIDADORA_FAMILIAR', 'Cuidadora Familiar', 'Jamilly Amaral, cuidadora familiar de Márcio Amaral (paciente com doença neurodegenerativa em estágio avançado), monitorando em ambiente domiciliar. (Danilo Amaral conduz o projeto como engenheiro/BA — não é o cuidador do dia a dia.)', true);
```

## Bundles de permissões por papel

Os bundles são definidos via `papel_permissao` e podem mudar conforme negócio. Configuração canônica recomendada:

### CUIDADORA_FAMILIAR

TODO: refine permissions — defaulted to `all` because per-role authority is not declared in discovery. Edit before going to production.

```sql
INSERT INTO neuroassist_global.papel_permissao (papel_id, permissao_id)
SELECT (SELECT papel_id FROM neuroassist_global.papel WHERE codigo='CUIDADORA_FAMILIAR'),
       permissao_id
FROM neuroassist_global.permissao;
```

## Como adicionar papel novo

1. Editar `templates/architecture/decisions/rbac.arch.json` — adicionar entrada em `roles`.
2. Validar com `python scripts/arch_decisions.py validate --aspect rbac`.
3. Re-renderizar este documento (`new-project` P2 ou comando equivalente).
4. Bumpar `rbac.version` no JSON (semver) e `last_changed`.
5. Migração: adicionar `INSERT INTO neuroassist_global.papel` em `V<N>__iam_papel_<codigo>.sql` espelhando a entrada nova.
6. Se afeta UI, atualizar `frontend/libs/iam/feature-permissoes/` para mostrar opção.
7. Bumpar `neuroassist_global.modelo_acesso_versao` para invalidar cache.

## Anti-patterns

- ❌ Editar este markdown direto — ele é gerado. Próximo render sobrescreve.
- ❌ Criar papel ad-hoc por empresa — papéis são **canônicos do grupo**; o que muda é a *associação* `usuario_empresa`.
- ❌ Bundle de permissões "wildcard" tipo `*.*` — sempre listar explicitamente (auditoria precisa rastrear).
- ❌ Permitir que GERENTE acesse `iam.*` — escalação de privilégio.
