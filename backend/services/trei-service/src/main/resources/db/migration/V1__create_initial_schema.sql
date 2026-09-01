-- =============================================================================
-- Migration: V1__create_initial_schema.sql
-- Service:   trei-service
-- Generated: 2026-09-01 by be-migration-generator.py
-- Source:    contracts/data-model.md (extraído de 4 blocos ```sql```)
--
-- ATENÇÃO: este arquivo é GERADO. Para alterar DDL:
--   1. Edite contracts/data-model.md (blocos ```sql```).
--   2. Re-rode: python scripts/be-migration-generator.py <service> --spec-dir <path> --force
-- =============================================================================

-- schema: empresa
CREATE TABLE trei_sessao_treinamento (
    id                  uuid        NOT NULL DEFAULT gen_random_uuid(),
    paciente_id         uuid        NOT NULL,               -- ref PAC.Paciente (no cross-service FK)
    cuidadora_id        uuid        NOT NULL,               -- ref ACS caregiver identity (by id)
    locale_autoria      text        NOT NULL,               -- 'pt' | 'en' (ADR-018: one authoring locale per session)
    estado              text        NOT NULL DEFAULT 'ativa',-- 'ativa' | 'encerrada'
    llm_model_id        text        NOT NULL,               -- Anthropic model id/version used (audit, TECH-007)
    llm_transcript_ref  text        NULL,                   -- pointer to transcript store; null until first turn
    iniciada_em         timestamptz NOT NULL DEFAULT now(),
    encerrada_em        timestamptz NULL,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    atualizado_em       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CHECK (locale_autoria IN ('pt','en')),
    CHECK (estado IN ('ativa','encerrada')),
    CHECK (encerrada_em IS NULL OR estado = 'encerrada')
);

CREATE INDEX ix_trei_sessao_paciente ON trei_sessao_treinamento (paciente_id);
CREATE INDEX ix_trei_sessao_estado   ON trei_sessao_treinamento (estado, iniciada_em);
COMMENT ON TABLE trei_sessao_treinamento IS 'LLM authoring session (caregiver-initiated); one authoring locale (ADR-018).';

-- schema: empresa
CREATE TABLE trei_proposta_llm (
    id             uuid        NOT NULL DEFAULT gen_random_uuid(),
    sessao_id      uuid        NOT NULL,
    no_id          uuid        NULL,                        -- set once the proposal is materialized as a draft node
    locale         text        NOT NULL,                    -- 'pt' | 'en' (== sessao.locale_autoria)
    texto_proposto text        NOT NULL,                    -- the phrase/category text the human will review
    prompt_ref     text        NULL,                        -- pointer to the prompt sent to Anthropic
    response_ref   text        NULL,                        -- pointer to the raw LLM response
    estado         text        NOT NULL DEFAULT 'proposta', -- 'proposta' | 'materializada' | 'descartada'
    criado_em      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CHECK (locale IN ('pt','en')),
    CHECK (estado IN ('proposta','materializada','descartada')),
    FOREIGN KEY (sessao_id) REFERENCES trei_sessao_treinamento (id) ON DELETE CASCADE,
    FOREIGN KEY (no_id)     REFERENCES conv_no_arvore_fala (id)     ON DELETE SET NULL
);

CREATE INDEX ix_trei_proposta_sessao ON trei_proposta_llm (sessao_id);
CREATE INDEX ix_trei_proposta_no     ON trei_proposta_llm (no_id) WHERE no_id IS NOT NULL;
COMMENT ON TABLE trei_proposta_llm IS 'LLM proposal traceability (SEC-002); prompt/response by reference.';

-- schema: empresa
CREATE TABLE trei_conteudo_autoria (
    id                  uuid        NOT NULL DEFAULT gen_random_uuid(),
    no_id               uuid        NOT NULL,               -- FK conv_no_arvore_fala (same schema)
    locale              text        NOT NULL,               -- 'pt' | 'en'
    sessao_id           uuid        NULL,                   -- authoring session that produced it
    proposta_id         uuid        NULL,                   -- originating LLM proposal
    sub_estado          text        NOT NULL DEFAULT 'rascunho',
        -- 'rascunho' | 'aguardando_aprovacao' | 'publicando' | 'ativo' | 'descartado'
    embedding_model_id  text        NULL,                   -- G5: model id that produced the indexed vector; null until embedded
    embedding_ref       text        NULL,                   -- pointer/doc-id of the ES document (no_id_locale)
    audio_ref           text        NULL,                   -- filesystem path (relative to TREI_AUDIO_ROOT)
    audio_formato       text        NULL,                   -- 'mp3' | 'opus'
    publicado_em        timestamptz NULL,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    atualizado_em       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CHECK (locale IN ('pt','en')),
    CHECK (sub_estado IN ('rascunho','aguardando_aprovacao','publicando','ativo','descartado')),
    CHECK (audio_formato IS NULL OR audio_formato IN ('mp3','opus')),
    CHECK (audio_ref IS NULL OR audio_formato IS NOT NULL),          -- a ref must declare its format
    CHECK (sub_estado <> 'ativo' OR (embedding_model_id IS NOT NULL AND embedding_ref IS NOT NULL)),
        -- G5 hard gate: a locale cannot be 'ativo' without a recorded embedding model id + ref
    FOREIGN KEY (no_id)       REFERENCES conv_no_arvore_fala (id)        ON DELETE CASCADE,
    FOREIGN KEY (sessao_id)   REFERENCES trei_sessao_treinamento (id)    ON DELETE SET NULL,
    FOREIGN KEY (proposta_id) REFERENCES trei_proposta_llm (id)          ON DELETE SET NULL,
    UNIQUE (no_id, locale)
);

CREATE INDEX ix_trei_autoria_no       ON trei_conteudo_autoria (no_id);
CREATE INDEX ix_trei_autoria_estado   ON trei_conteudo_autoria (sub_estado, atualizado_em);
CREATE INDEX ix_trei_autoria_model    ON trei_conteudo_autoria (embedding_model_id) WHERE embedding_model_id IS NOT NULL;
COMMENT ON TABLE trei_conteudo_autoria IS 'Per (node,locale) authoring lifecycle + G5 embedding_model_id record; mirrors conv_no_conteudo UNIQUE(no_id,locale).';

-- schema: empresa
CREATE TABLE trei_publicacao_outbox (
    id             uuid        NOT NULL DEFAULT gen_random_uuid(),
    no_id          uuid        NOT NULL,
    locale         text        NOT NULL,                    -- 'pt' | 'en'
    passo          text        NOT NULL,                    -- 'embed' | 'tts' | 'index'
    estado         text        NOT NULL DEFAULT 'pendente', -- 'pendente' | 'ok' | 'falha'
    tentativas     integer     NOT NULL DEFAULT 0,
    proxima_em     timestamptz NULL,                        -- next retry time (backoff)
    ultimo_erro    text        NULL,
    criado_em      timestamptz NOT NULL DEFAULT now(),
    atualizado_em  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CHECK (locale IN ('pt','en')),
    CHECK (passo IN ('embed','tts','index')),
    CHECK (estado IN ('pendente','ok','falha')),
    FOREIGN KEY (no_id) REFERENCES conv_no_arvore_fala (id) ON DELETE CASCADE,
    UNIQUE (no_id, locale, passo)                            -- one row per step; idempotent replay
);

CREATE INDEX ix_trei_outbox_due ON trei_publicacao_outbox (estado, proxima_em);
COMMENT ON TABLE trei_publicacao_outbox IS 'Broker-less retry buffer for the publication pipeline (ADR-004); step-idempotent by (no_id,locale,passo).';
