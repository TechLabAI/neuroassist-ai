-- P10: per-patient AES-256 encryption key for LGPD crypto-shredding (HIST-024, ADR-004)
-- payload column (already TEXT) now stores "base64(iv):base64(ciphertext)" when non-null.
-- Erasure: null out key_ciphertext + key_iv + stamp erased_at → payload becomes unreadable.
-- Hash chain structure (recordHash, prevHash, sequenceNo) is never touched — stays verifiable.

CREATE TABLE IF NOT EXISTS patient_encryption_key
(
    subject_id     UUID         NOT NULL,
    tenant_id      VARCHAR(255) NOT NULL,
    key_ciphertext BYTEA,                         -- AES-256 patient key wrapped by master key; NULL after erase
    key_iv         BYTEA,                         -- IV used to wrap the patient key; NULL after erase
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    erased_at      TIMESTAMPTZ,                   -- NULL = active; non-NULL = LGPD-erased
    PRIMARY KEY (subject_id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_patient_key_tenant
    ON patient_encryption_key (tenant_id);

GRANT SELECT, INSERT, UPDATE ON patient_encryption_key TO ${hist_service_role};
