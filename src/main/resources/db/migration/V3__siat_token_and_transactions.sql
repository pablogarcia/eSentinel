-- V3: token delegado cifrado y bitacora de transacciones contra el SIAT.

-- El token delegado SOLO se guarda cifrado (AES-256-GCM con AAD ligada a tenant+empresa, ADR-0008).
CREATE TABLE fiscal_token (
    id               uuid PRIMARY KEY,
    tenant_id        uuid         NOT NULL,
    company_id       uuid         NOT NULL,
    token_ciphertext text         NOT NULL,
    key_id           varchar(64)  NOT NULL,
    expires_at       timestamptz  NOT NULL,
    status           varchar(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       text         NOT NULL,
    revoked_at       timestamptz,
    CONSTRAINT fk_token_company FOREIGN KEY (tenant_id, company_id) REFERENCES company (tenant_id, id)
);
-- A lo sumo un token ACTIVE por empresa.
CREATE UNIQUE INDEX uq_fiscal_token_active ON fiscal_token (company_id) WHERE status = 'ACTIVE';
ALTER TABLE fiscal_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE fiscal_token FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON fiscal_token FOR ALL
    USING (tenant_id = app_current_tenant()) WITH CHECK (tenant_id = app_current_tenant());
GRANT SELECT, INSERT, UPDATE ON fiscal_token TO fiscal_app_role;

-- Bitacora de cada llamada al SIAT. Se inserta (STARTED) y confirma ANTES de llamar, y se completa despues.
CREATE TABLE siat_transaction (
    id             uuid PRIMARY KEY,
    tenant_id      uuid         NOT NULL,
    company_id     uuid         NOT NULL,
    service        varchar(60)  NOT NULL,
    operation      varchar(80)  NOT NULL,
    document_id    uuid,
    outcome        varchar(20)  NOT NULL DEFAULT 'STARTED'
                   CHECK (outcome IN ('STARTED', 'OK', 'SIAT_ERROR', 'AUTH_ERROR', 'TIMEOUT', 'UNAVAILABLE', 'PROTOCOL_ERROR')),
    siat_codes     text,
    error_message  text,
    started_at     timestamptz  NOT NULL DEFAULT now(),
    finished_at    timestamptz,
    latency_ms     integer,
    correlation_id varchar(100),
    CONSTRAINT fk_siat_tx_company FOREIGN KEY (tenant_id, company_id) REFERENCES company (tenant_id, id)
);
CREATE INDEX ix_siat_tx_company ON siat_transaction (tenant_id, company_id, started_at DESC);
-- Transacciones que quedaron en STARTED (caida a mitad de llamada): candidatas a reconciliacion.
CREATE INDEX ix_siat_tx_open ON siat_transaction (started_at) WHERE outcome = 'STARTED';
ALTER TABLE siat_transaction ENABLE ROW LEVEL SECURITY;
ALTER TABLE siat_transaction FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON siat_transaction FOR ALL
    USING (tenant_id = app_current_tenant()) WITH CHECK (tenant_id = app_current_tenant());
GRANT SELECT, INSERT, UPDATE ON siat_transaction TO fiscal_app_role;

-- Actualizacion de la empresa (codigoSistema llega despues de la autorizacion en piloto).
GRANT UPDATE (system_code, version, updated_at) ON company TO fiscal_app_role;
