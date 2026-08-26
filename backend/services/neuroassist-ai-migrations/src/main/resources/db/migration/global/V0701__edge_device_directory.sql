-- EDGE migration — global schema — faixa V0700..V0799 — owner: edge-service
-- =============================================================================
-- Migration: V0701__edge_device_directory.sql
-- Service:   edge-service
-- Schema:    neuroassist_global
--
-- Índice global client_id → tenant, para verificação de credencial de dispositivo.
--
-- MOTIVO (C-18 / service-identity.md §7.4): o ACS recebe apenas {clientId,
-- clientSecret} do dispositivo e precisa perguntar ao EDGE se a credencial vale. Mas
-- dispositivo_credential vive no schema do tenant, e o clientId sozinho não diz qual
-- é — o mesmo ovo-e-galinha de C-21, resolvido do mesmo jeito.
--
-- Ponteiro apenas. O hash do secret NUNCA sai de <tenant>.dispositivo_credential.
-- =============================================================================

CREATE TABLE IF NOT EXISTS neuroassist_global.device_directory (
    client_id            varchar(120) NOT NULL,
    tenant_id            uuid         NOT NULL,
    dispositivo_edge_id  uuid         NOT NULL,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (client_id)
);

CREATE INDEX IF NOT EXISTS ix_device_directory_device
    ON neuroassist_global.device_directory (dispositivo_edge_id);

COMMENT ON TABLE neuroassist_global.device_directory IS
    'Índice global client_id → tenant para verificação de credencial de dispositivo pelo ACS. '
    'Ponteiro apenas; o hash do secret permanece em <tenant>.dispositivo_credential.';
