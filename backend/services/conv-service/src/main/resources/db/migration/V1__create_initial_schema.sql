-- =============================================================================
-- Migration: V1__create_initial_schema.sql
-- Service:   conv-service
-- Generated: 2026-08-31 by be-migration-generator.py
-- Source:    contracts/data-model.md (extraído de 5 blocos ```sql```)
--
-- ATENÇÃO: este arquivo é GERADO. Para alterar DDL:
--   1. Edite contracts/data-model.md (blocos ```sql```).
--   2. Re-rode: python scripts/be-migration-generator.py <service> --spec-dir <path> --force
-- =============================================================================

-- schema: global
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()

-- schema: empresa
CREATE TABLE conv_arvore_fala (
    id                  uuid        NOT NULL DEFAULT gen_random_uuid(),
    paciente_id         uuid        NOT NULL,               -- ref PAC.Paciente (no cross-service FK)
    locales_suportados  text[]      NOT NULL DEFAULT '{pt}',-- subset of {pt,en}
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    UNIQUE (paciente_id),                                   -- one logical tree per patient
    CHECK (locales_suportados <@ ARRAY['pt','en']::text[] AND array_length(locales_suportados,1) >= 1)
);

CREATE INDEX ix_conv_arvore_paciente ON conv_arvore_fala (paciente_id);
COMMENT ON TABLE conv_arvore_fala IS 'CONV speech tree, one per patient; tenant = schema (ADR-013).';

-- schema: empresa
CREATE TABLE conv_no_arvore_fala (
    id                  uuid        NOT NULL DEFAULT gen_random_uuid(),
    arvore_id           uuid        NOT NULL,
    parent_id           uuid        NULL,                   -- null = root
    tipo                text        NOT NULL,               -- 'categoria' | 'frase'
    engine_feature_ref  text        NULL,                   -- opaque PAC/GEST vocabulary token (ADR-013, gap G4)
    ordem               integer     NOT NULL DEFAULT 0,     -- sibling order
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CHECK (tipo IN ('categoria','frase')),
    CHECK (parent_id IS NULL OR parent_id <> id),           -- no self-parent
    FOREIGN KEY (arvore_id) REFERENCES conv_arvore_fala (id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES conv_no_arvore_fala (id) ON DELETE CASCADE,
    UNIQUE (arvore_id, parent_id, ordem)                    -- deterministic sibling ordering
);

CREATE INDEX ix_conv_no_arvore     ON conv_no_arvore_fala (arvore_id);
CREATE INDEX ix_conv_no_parent     ON conv_no_arvore_fala (parent_id);
CREATE INDEX ix_conv_no_engine_ref ON conv_no_arvore_fala (engine_feature_ref) WHERE engine_feature_ref IS NOT NULL;
-- exactly one root per tree (parent_id IS NULL)
CREATE UNIQUE INDEX uq_conv_no_root ON conv_no_arvore_fala (arvore_id) WHERE parent_id IS NULL;
COMMENT ON TABLE conv_no_arvore_fala IS 'Speech-tree nodes; structure locale-invariant (ADR-005).';

-- schema: empresa
CREATE TABLE conv_no_conteudo (
    id                    uuid        NOT NULL DEFAULT gen_random_uuid(),
    no_id                 uuid        NOT NULL,
    locale                text        NOT NULL,             -- 'pt' | 'en'
    texto                 text        NOT NULL,
    audio_ref             text        NULL,                 -- filesystem path/uri; null = not synthesized (ADR-004)
    audio_formato         text        NULL,                 -- 'mp3' | 'opus'
    confirmado_cuidadora  boolean     NOT NULL DEFAULT false,
    confirmado_paciente   boolean     NOT NULL DEFAULT false,
    status                text        NOT NULL DEFAULT 'pendente', -- 'ativo' | 'pendente'
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CHECK (locale IN ('pt','en')),
    CHECK (status IN ('ativo','pendente')),
    CHECK (audio_formato IS NULL OR audio_formato IN ('mp3','opus')),
    CHECK (audio_ref IS NULL OR audio_formato IS NOT NULL),  -- a ref must declare its format
    FOREIGN KEY (no_id) REFERENCES conv_no_arvore_fala (id) ON DELETE CASCADE,
    UNIQUE (no_id, locale)                                    -- one content row per (node, locale)
);

CREATE INDEX ix_conv_conteudo_no      ON conv_no_conteudo (no_id);
CREATE INDEX ix_conv_conteudo_locale  ON conv_no_conteudo (locale, status);
COMMENT ON TABLE conv_no_conteudo IS 'Per-locale node content (ADR-005); UNIQUE(no_id,locale); TREI writes, CONV reads.';

-- schema: empresa
CREATE TABLE conv_audit_outbox (
    id               uuid        NOT NULL DEFAULT gen_random_uuid(),
    client_event_id  text        NULL,                     -- idempotency key from POST /select (SPEC-05)
    tipo             text        NOT NULL,                 -- 'SpeechNodeSelected' | 'PhraseMatched' | 'ConvServedDegraded'
    paciente_id      uuid        NOT NULL,
    no_id            uuid        NULL,
    payload          jsonb       NOT NULL,
    status           text        NOT NULL DEFAULT 'pendente', -- 'pendente' | 'enviado' | 'descartado'
    tentativas       integer     NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL DEFAULT now(),
    sent_at          timestamptz NULL,
    PRIMARY KEY (id),
    CHECK (tipo IN ('SpeechNodeSelected','PhraseMatched','ConvServedDegraded')),
    CHECK (status IN ('pendente','enviado','descartado'))
);

CREATE INDEX ix_conv_outbox_status ON conv_audit_outbox (status, created_at);
CREATE UNIQUE INDEX uq_conv_outbox_client_event
    ON conv_audit_outbox (client_event_id) WHERE client_event_id IS NOT NULL;
COMMENT ON TABLE conv_audit_outbox IS 'Local retry buffer for HIST audit facts; idempotent by client_event_id (SPEC-05/09).';
